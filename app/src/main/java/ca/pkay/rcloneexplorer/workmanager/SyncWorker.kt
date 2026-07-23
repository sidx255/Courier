package ca.pkay.rcloneexplorer.workmanager

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.net.wifi.WifiManager
import android.os.PowerManager
import androidx.annotation.StringRes
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import androidx.work.ForegroundInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import ca.pkay.rcloneexplorer.Database.DatabaseHandler
import ca.pkay.rcloneexplorer.Items.RemoteItem
import ca.pkay.rcloneexplorer.Items.Task
import ca.pkay.rcloneexplorer.Log2File
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.Rclone
import ca.pkay.rcloneexplorer.guided.GuidedBackupStatusStore
import ca.pkay.rcloneexplorer.guided.GuidedVerificationStatusStore
import ca.pkay.rcloneexplorer.notifications.GenericSyncNotification
import ca.pkay.rcloneexplorer.notifications.ReportNotifications
import ca.pkay.rcloneexplorer.notifications.SyncServiceNotifications
import ca.pkay.rcloneexplorer.notifications.SyncServiceNotifications.Companion.GROUP_ID
import ca.pkay.rcloneexplorer.notifications.support.StatusObject
import ca.pkay.rcloneexplorer.util.FLog
import ca.pkay.rcloneexplorer.util.RcloneErrorMapper
import ca.pkay.rcloneexplorer.util.SyncLog
import ca.pkay.rcloneexplorer.util.WifiConnectivitiyUtil
import ca.pkay.rcloneexplorer.widgets.BackupStatusWidget
import kotlinx.serialization.json.Json
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.InterruptedIOException
import java.util.Random
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SyncWorker (private var mContext: Context, workerParams: WorkerParameters): Worker(mContext, workerParams) {

    companion object {
        const val TASK_ID = "TASK_ID"
        const val TASK_EPHEMERAL = "TASK_EPHEMERAL"
        const val TASK_OPERATION = "TASK_OPERATION"
        const val TASK_REQUIRES_CHARGING = "TASK_REQUIRES_CHARGING"
        const val TASK_RUN_FOLLOWUPS = "TASK_RUN_FOLLOWUPS"
        const val TASK_FOLLOWUP_DEPTH = "TASK_FOLLOWUP_DEPTH"
        private const val TAG = "SyncWorker"
        private const val SYNC_STALL_TIMEOUT_MS = 15 * 60 * 1000L
        private const val SYNC_STALL_CHECK_MS = 60 * 1000L
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 1_000L
        // Bound the length of onSuccess/onFail follow-up chains so a misconfigured cycle
        // (task A -> task B -> task A) cannot queue itself forever and drain the battery.
        private const val MAX_FOLLOWUP_DEPTH = 25

        //those Extras do not follow the above schema, because they are exposed to external applications
        //That means shorter values make it easier to use. There is no other technical reason
        const val TASK_SYNC_ACTION = "START_TASK"
        const val TASK_CANCEL_ACTION = "CANCEL_TASK"
        const val EXTRA_TASK_ID = "task"

        // Todo: Allow SyncWorker to run in silent mode, or remove this!
        const val EXTRA_TASK_SILENT = "notification"

        private const val RETRY_WINDOW_MS = 2 * 60 * 1000L
    }



    internal enum class FAILURE_REASON {
        NO_FAILURE, NO_UNMETERED, NO_CONNECTION, RCLONE_ERROR, CONNECTIVITY_CHANGED, CANCELLED, NO_TASK
    }

    // Objects
    private var mRclone = Rclone(mContext)
    private var mDatabase = DatabaseHandler(mContext)
    private var mNotificationManager = SyncServiceNotifications(mContext)
    private val mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext)


    private var log2File: Log2File? = null



    // States
    private val sIsLoggingEnabled = mPreferences.getBoolean(getString(R.string.pref_key_logs), false)
    private var sRcloneProcess: Process? = null
    @Volatile private var lastSyncProgressAt = 0L
    @Volatile private var lastSyncBytes = -1L
    private var lastNotificationUpdateAt = 0L
    private var lastRcloneDiagnostic: String? = null
    private var lastExitCode: Int? = null
    private val statusObject = StatusObject(mContext)
    private var failureReason = FAILURE_REASON.NO_FAILURE
    private var endNotificationAlreadyPosted = false
    private var silentRun = false
    private val ongoingNotificationID = Random().nextInt()
    private val mRequiresCharging = inputData.getBoolean(TASK_REQUIRES_CHARGING, false)
    private val mRunFollowups = inputData.getBoolean(TASK_RUN_FOLLOWUPS, false)
    private val mFollowupDepth = inputData.getInt(TASK_FOLLOWUP_DEPTH, 0)

    private var mWakeLock: PowerManager.WakeLock? = null
    private var mWifiLock: WifiManager.WifiLock? = null


    // Task
    private lateinit var mTask: Task
    private var mTitle: String = mContext.getString(R.string.sync_service_notification_startingsync)
    private var mOperation = SyncOperation.TRANSFER
    private var mVerifyDifferences: List<String> = emptyList()


    override fun doWork(): Result {
        if (!TransferRuntime.executionLock.tryLock()) {
            return Result.retry()
        }
        try {
            acquireWakeLocks()
            prepareNotifications()

            updateForegroundNotification(mNotificationManager.updateSyncNotification(
                mTitle,
                mTitle,
                ArrayList(),
                0,
                ongoingNotificationID
            ))


            var ephemeralTask: Task? = null

            if(inputData.keyValueMap.containsKey(TASK_ID)){
                val id = inputData.getLong(TASK_ID, -1)
                ephemeralTask = mDatabase.getTask(id)
            }

            if(inputData.keyValueMap.containsKey(TASK_EPHEMERAL)){
                val taskString = inputData.getString(TASK_EPHEMERAL) ?: ""
                if(taskString.isNotEmpty()) {
                    try {
                        ephemeralTask = Json.decodeFromString<Task>(taskString)
                    } catch (e: Exception) {
                        log("Could not deserialize")
                    }
                }
            }

            if (ephemeralTask != null) {
                mTask = ephemeralTask
                mOperation = try {
                    SyncOperation.valueOf(inputData.getString(TASK_OPERATION) ?: SyncOperation.TRANSFER.name)
                } catch (_: IllegalArgumentException) {
                    SyncOperation.TRANSFER
                }
                handleTask()

                if (shouldRetry()) {
                    SyncLog.info(mContext, mTitle, "Transient failure ($failureReason). Retrying within the 2-minute recovery window.")
                    cleanupForRetry()
                    return Result.retry()
                }

                postSync()
            } else {
                failureReason = FAILURE_REASON.NO_TASK
                postSync()
                return Result.failure()
            }

            return if (failureReason == FAILURE_REASON.NO_FAILURE) Result.success() else Result.failure()
        } finally {
            releaseWakeLocks()
            TransferRuntime.executionLock.unlock()
        }
    }

    override fun onStopped() {
        super.onStopped()
        SyncLog.info(mContext, mTitle, mContext.getString(R.string.operation_sync_cancelled))
        SyncLog.info(mContext, mTitle, statusObject.toString())
        failureReason = FAILURE_REASON.CANCELLED
        TransferRuntime.terminateGracefully(sRcloneProcess)
        if (::mTask.isInitialized) {
            postSync()
        }
    }

    private fun finishWork() {
        TransferRuntime.terminateGracefully(sRcloneProcess)
        postSync()
    }

    private fun shouldRetry(): Boolean {
        val transient = failureReason == FAILURE_REASON.NO_CONNECTION || lastExitCode == 5
        if (!transient) {
            clearRetryWindow()
            return false
        }
        val now = System.currentTimeMillis()
        var windowStart = mPreferences.getLong(retryWindowKey(), 0L)
        if (windowStart == 0L) {
            windowStart = now
            mPreferences.edit().putLong(retryWindowKey(), windowStart).apply()
        }
        val withinWindow = now - windowStart < RETRY_WINDOW_MS
        if (!withinWindow) {
            clearRetryWindow()
        }
        return withinWindow
    }

    private fun retryWindowKey(): String = "courier_retry_window_${mTask.id}"

    private fun clearRetryWindow() {
        if (::mTask.isInitialized) {
            mPreferences.edit().remove(retryWindowKey()).apply()
        }
    }

    private fun cleanupForRetry() {
        TransferRuntime.terminateGracefully(sRcloneProcess)
        mNotificationManager.cancelSyncNotification(ongoingNotificationID)
    }

    private fun acquireWakeLocks() {
        try {
            val powerManager = mContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG::sync").apply {
                setReferenceCounted(false)
                acquire(TimeUnit.HOURS.toMillis(6))
            }
            val wifiManager = mContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            mWifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "$TAG::wifi").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            FLog.e(TAG, "acquireWakeLocks: failed to acquire", e)
        }
    }

    private fun releaseWakeLocks() {
        try {
            mWakeLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
            FLog.e(TAG, "releaseWakeLocks: wakelock", e)
        }
        try {
            mWifiLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
            FLog.e(TAG, "releaseWakeLocks: wifilock", e)
        }
        mWakeLock = null
        mWifiLock = null
    }

    private fun handleTask() {
        mTitle = mTask.title
        mNotificationManager.setCancelId(id)
        val remoteItem = RemoteItem(mTask.remoteId, mTask.remoteType, "")

        if (mTask.title == "") {
            mTitle = mTask.remotePath
        }
        if(arePreconditionsMet()) {
            val taskFilter = if(mTask.filterId != null ) mDatabase.getFilter(mTask.filterId!!) else null;
            val taskFilterList = taskFilter?.getFilters() ?: ArrayList()
            when (mOperation) {
                SyncOperation.TRANSFER -> transferTask(remoteItem, taskFilterList)
                SyncOperation.VERIFY -> verifyTask(remoteItem, taskFilterList, false)
                SyncOperation.VERIFY_DEEP -> verifyTask(remoteItem, taskFilterList, true)
                SyncOperation.REPAIR -> repairTask(remoteItem, taskFilterList, false)
                SyncOperation.REPAIR_DEEP -> repairTask(remoteItem, taskFilterList, true)
            }
            if (failureReason == FAILURE_REASON.NO_FAILURE && mOperation == SyncOperation.TRANSFER) {
                sendUploadFinishedBroadcast(remoteItem.name, mTask.remotePath)
            }
        }
    }

    private fun cleanupStalePartials(remoteItem: RemoteItem) {
        val cleanup = mRclone.cleanupCourierPartials(remoteItem, mTask.remotePath) ?: return
        cleanup.errorStream.bufferedReader().use { reader ->
            while (reader.readLine() != null) {
            }
        }
        cleanup.waitFor()
    }

    private fun transferTask(remoteItem: RemoteItem, filters: ArrayList<ca.pkay.rcloneexplorer.Items.FilterEntry>) {
        cleanupStalePartials(remoteItem)

        if (!isStopped) {
            sRcloneProcess = mRclone.sync(
                remoteItem,
                mTask.localPath,
                mTask.remotePath,
                mTask.direction,
                false,
                filters,
                mTask.deleteExcluded
            )
            handleSync(mTitle)
        }
    }

    private fun verifyTask(remoteItem: RemoteItem, filters: ArrayList<ca.pkay.rcloneexplorer.Items.FilterEntry>, deep: Boolean) {
        val report = reportFile("verify")
        report.delete()
        sRcloneProcess = mRclone.verify(
            remoteItem,
            mTask.localPath,
            mTask.remotePath,
            mTask.direction,
            filters,
            report.absolutePath,
            deep
        )
        val exit = handleSync(getString(R.string.task_verify_contents), false)
        if (exit == 0) {
            mVerifyDifferences = emptyList()
            failureReason = FAILURE_REASON.NO_FAILURE
            return
        }
        val diffs = readReportPaths(report)
        if (diffs.isEmpty()) {
            failureReason = FAILURE_REASON.RCLONE_ERROR
            return
        }
        mVerifyDifferences = diffs
        failureReason = FAILURE_REASON.NO_FAILURE
    }

    private fun repairTask(remoteItem: RemoteItem, filters: ArrayList<ca.pkay.rcloneexplorer.Items.FilterEntry>, deep: Boolean) {
        cleanupStalePartials(remoteItem)
        val report = reportFile("repair-check")
        report.delete()
        sRcloneProcess = mRclone.verify(
            remoteItem,
            mTask.localPath,
            mTask.remotePath,
            mTask.direction,
            filters,
            report.absolutePath,
            deep
        )
        val checkExit = handleSync(getString(R.string.task_repair_differences), false)
        if (checkExit == 0) {
            failureReason = FAILURE_REASON.NO_FAILURE
            return
        }

        val repairPaths = readReportPaths(report)
        if (repairPaths.isEmpty()) {
            failureReason = FAILURE_REASON.RCLONE_ERROR
            return
        }

        val filesFrom = reportFile("repair-files")
        filesFrom.writeText(repairPaths.joinToString(System.lineSeparator()))
        statusObject.clearObject()
        failureReason = FAILURE_REASON.NO_FAILURE
        lastExitCode = null
        sRcloneProcess = mRclone.repair(
            remoteItem,
            mTask.localPath,
            mTask.remotePath,
            mTask.direction,
            filesFrom.absolutePath
        )
        handleSync(getString(R.string.task_repair_differences))
    }

    private fun reportFile(suffix: String): File {
        return File(mContext.cacheDir, "courier-task-${mTask.id}-$suffix.txt")
    }

    private fun readReportPaths(report: File): List<String> {
        return report.takeIf { it.exists() }?.readLines()
            ?.mapNotNull { line ->
                if (line.length > 2 && (line[0] == '+' || line[0] == '*' || line[0] == '!')) {
                    line.substring(2)
                } else {
                    null
                }
            }
            ?.distinct()
            ?: emptyList()
    }

    private fun handleSync(title: String, recordFailure: Boolean = true): Int {
        SyncLog.info(mContext, mTitle, mContext.getString(R.string.operation_start_sync))
        var exitCode = -1
        if (sRcloneProcess != null) {
            val localProcessReference = sRcloneProcess!!
            lastSyncProgressAt = System.currentTimeMillis()
            lastSyncBytes = -1L
            val stallWatchdog = Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "courier-sync-stall").also { it.isDaemon = true }
            }
            stallWatchdog.scheduleWithFixedDelay({
                if (System.currentTimeMillis() - lastSyncProgressAt > SYNC_STALL_TIMEOUT_MS &&
                    TransferRuntime.isAlive(localProcessReference)
                ) {
                    FLog.e(TAG, "SyncWorker: transfer stalled with no progress; terminating rclone")
                    TransferRuntime.terminateGracefully(localProcessReference)
                }
            }, SYNC_STALL_CHECK_MS, SYNC_STALL_CHECK_MS, TimeUnit.MILLISECONDS)
            try {
                val reader = BufferedReader(InputStreamReader(localProcessReference.errorStream))
                val iterator = reader.lineSequence().iterator()
                while(iterator.hasNext()) {
                    val line = iterator.next()
                    try {
                        val logline = JSONObject(line)
                        //todo: migrate this to StatusObject, so that we can handle everything properly.
                        if (logline.getString("level") == "error") {
                            if (sIsLoggingEnabled) {
                                log2File?.log(line)
                            }
                            statusObject.parseLoglineToStatusObject(logline)
                        } else if (logline.getString("level") == "warning") {
                            statusObject.parseLoglineToStatusObject(logline)
                        } else if (logline.has("stats")) {
                            statusObject.parseLoglineToStatusObject(logline)
                        }

                        val bytesTransferred = statusObject.mStats.optLong("bytes", 0L)
                        if (bytesTransferred != lastSyncBytes) {
                            lastSyncBytes = bytesTransferred
                            lastSyncProgressAt = System.currentTimeMillis()
                        }

                        val now = System.currentTimeMillis()
                        if (NotificationUpdateGate.shouldUpdate(
                                lastNotificationUpdateAt,
                                now,
                                NOTIFICATION_UPDATE_INTERVAL_MS
                            )) {
                            lastNotificationUpdateAt = now
                            updateForegroundNotification(mNotificationManager.updateSyncNotification(
                                title,
                                statusObject.notificationContent,
                                statusObject.notificationBigText,
                                statusObject.notificationPercent,
                                ongoingNotificationID
                            ))
                        }
                    } catch (_: JSONException) {
                        RcloneDiagnosticLine.extract(line)?.let { diagnostic ->
                            lastRcloneDiagnostic = diagnostic
                            FLog.e(TAG, "rclone: $diagnostic")
                        }
                    }
                }
            } catch (e: InterruptedIOException) {
                FLog.e(TAG, "onHandleIntent: I/O interrupted, stream closed", e)
            } catch (e: IOException) {
                FLog.e(TAG, "onHandleIntent: error reading stdout", e)
            } finally {
                stallWatchdog.shutdownNow()
            }
            try {
                exitCode = localProcessReference.waitFor()
                lastExitCode = exitCode
                if (recordFailure && exitCode != 0 && failureReason == FAILURE_REASON.NO_FAILURE) {
                    val connection = WifiConnectivitiyUtil.dataConnection(applicationContext)
                    failureReason = if (connection === WifiConnectivitiyUtil.Connection.DISCONNECTED ||
                        connection === WifiConnectivitiyUtil.Connection.NOT_AVAILABLE) {
                        FAILURE_REASON.NO_CONNECTION
                    } else {
                        FAILURE_REASON.RCLONE_ERROR
                    }
                }
            } catch (e: InterruptedException) {
                FLog.e(TAG, "onHandleIntent: error waiting for process", e)
            }
        } else {
            log("Sync: No Rclone Process!")
            if (recordFailure) {
                failureReason = FAILURE_REASON.RCLONE_ERROR
            }
        }
        mNotificationManager.cancelSyncNotification(ongoingNotificationID)
        return exitCode
    }

    private fun postSync() {
        if (endNotificationAlreadyPosted) {
            return
        }
        if (silentRun) {
            return
        }

        val notificationId = System.currentTimeMillis().toInt()

        var content = mContext.getString(R.string.operation_failed_unknown, mTitle)
        when (failureReason) {
            FAILURE_REASON.NO_FAILURE -> {
                recordGuidedOutcome(GuidedBackupStatusStore.OUTCOME_SUCCESS)
                recordGuidedVerification()
                recordGuidedRepair(GuidedVerificationStatusStore.REPAIR_SUCCESS)
                showSuccessNotification(notificationId)
                if (mOperation == SyncOperation.TRANSFER) {
                    if (!mRunFollowups || mTask.onSuccessFollowup == null || mTask.onSuccessFollowup == -1L) {
                        BackupStatusWidget.updateAll(mContext)
                    }
                    if (mRunFollowups) {
                        followupTask(mTask.onSuccessFollowup)
                    }
                }
                return
            }
            FAILURE_REASON.CANCELLED -> {
                recordGuidedOutcome(GuidedBackupStatusStore.OUTCOME_CANCELLED)
                if (mOperation == SyncOperation.VERIFY_DEEP) {
                    GuidedVerificationStatusStore.record(
                        mContext,
                        mTask.id,
                        GuidedVerificationStatusStore.OUTCOME_CANCELLED,
                        0
                    )
                }
                recordGuidedRepair(GuidedVerificationStatusStore.REPAIR_FAILED)
                if (mOperation == SyncOperation.TRANSFER) {
                    BackupStatusWidget.updateAll(mContext)
                }
                showCancelledNotification(notificationId)
                endNotificationAlreadyPosted = true
                return
            }
            FAILURE_REASON.NO_TASK -> {
                content = getString(R.string.operation_failed_notask)
            }
            FAILURE_REASON.CONNECTIVITY_CHANGED -> {
                content = mContext.getString(R.string.operation_failed_data_change, mTitle)
            }
            FAILURE_REASON.NO_UNMETERED -> {
                content = mContext.getString(R.string.operation_failed_no_unmetered, mTitle)
            }
            FAILURE_REASON.NO_CONNECTION -> {
                content = mContext.getString(R.string.operation_failed_no_connection, mTitle)
            }
            FAILURE_REASON.RCLONE_ERROR -> {
                content = mContext.getString(R.string.operation_failed_unknown_rclone_error, mTitle)
            }
        }
        recordGuidedOutcome(GuidedBackupStatusStore.OUTCOME_FAILED)
        if (mOperation == SyncOperation.VERIFY_DEEP) {
            GuidedVerificationStatusStore.record(
                mContext,
                mTask.id,
                GuidedVerificationStatusStore.OUTCOME_FAILED,
                0
            )
        }
        recordGuidedRepair(GuidedVerificationStatusStore.REPAIR_FAILED)
        if (mOperation == SyncOperation.TRANSFER) {
            if (!mRunFollowups || mTask.onFailFollowup == null || mTask.onFailFollowup == -1L) {
                BackupStatusWidget.updateAll(mContext)
            }
            if (mRunFollowups) {
                followupTask(mTask.onFailFollowup)
            }
        }
        showFailNotification(notificationId, content)
        endNotificationAlreadyPosted = true
        finishWork()
    }

    private fun recordGuidedOutcome(outcome: String) {
        if (mOperation != SyncOperation.TRANSFER) return
        GuidedBackupStatusStore.record(
            mContext,
            mTask.id,
            outcome,
            statusObject.mStats.optLong("bytes", 0L),
            statusObject.getTransfers()
        )
    }

    private fun recordGuidedVerification() {
        if (mOperation != SyncOperation.VERIFY_DEEP) return
        GuidedVerificationStatusStore.record(
            mContext,
            mTask.id,
            if (mVerifyDifferences.isEmpty()) {
                GuidedVerificationStatusStore.OUTCOME_SAFE
            } else {
                GuidedVerificationStatusStore.OUTCOME_DIFFERENT
            },
            mVerifyDifferences.size
        )
    }

    private fun recordGuidedRepair(outcome: String) {
        if (mOperation != SyncOperation.REPAIR_DEEP) return
        GuidedVerificationStatusStore.recordRepair(mContext, mTask.id, outcome)
    }

    private fun showCancelledNotification(notificationId: Int) {
        SyncLog.info(mContext, mTitle, mContext.getString(R.string.operation_failed_cancelled))
        mNotificationManager.showCancelledNotificationOrReport(
            mTitle,
            notificationId,
            mTask.id
        )
    }

    private fun showSuccessNotification(notificationId: Int) {
        //Todo: Show sync-errors in notification. Also see line 169

        var message = when (mOperation) {
            SyncOperation.VERIFY, SyncOperation.VERIFY_DEEP ->
                if (mVerifyDifferences.isEmpty()) {
                    if (mOperation == SyncOperation.VERIFY_DEEP) {
                        getString(R.string.task_verify_success_deep)
                    } else {
                        getString(R.string.task_verify_success)
                    }
                } else {
                    mContext.getString(R.string.task_verify_found_differences, mVerifyDifferences.size) +
                            "\n" + mVerifyDifferences.take(20).joinToString("\n")
                }
            SyncOperation.REPAIR, SyncOperation.REPAIR_DEEP -> getString(R.string.task_repair_success)
            SyncOperation.TRANSFER -> generateSuccessMessage(statusObject)
        }
        mNotificationManager.showSuccessNotificationOrReport(
            mTitle,
            message,
            notificationId
        )

        message += """
                        
        Est. Speed: ${statusObject.getEstimatedAverageSpeed()}
        Avg. Speed: ${statusObject.getLastItemAverageSpeed()}
                        """.trimIndent()
        SyncLog.info(mContext, mContext.getString(R.string.operation_success, mTitle), message)
    }

    // this is currently only a useless mapper. It is supposed to keep this worker in sync with the ephemeral one.
    // when they are merged eventually, this can be easily extracted.
    private fun generateSuccessMessage(statusObject: StatusObject): String {
        var message = mContext.resources.getQuantityString(
                R.plurals.operation_success_description,
                statusObject.getTotalTransfers(),
                mTitle,
                statusObject.getTotalSize(),
                statusObject.getTotalTransfers()
        )
        if (statusObject.getTotalTransfers() == 0) {
            message = mContext.resources.getString(R.string.operation_success_description_zero)
        }
        if (statusObject.getDeletions() > 0) {
            message += """
                        
                        ${
                mContext.getString(
                        R.string.operation_success_description_deletions_prefix,
                        statusObject.getDeletions()
                )
            }
                        """.trimIndent()
        }
        return message
    }

    private fun showFailNotification(notificationId: Int, content: String, wasCancelled: Boolean = false) {
        statusObject.printErrors()
        val errors = statusObject.getAllErrorMessages()
        val diagnostic = buildString {
            lastExitCode?.let { append(mContext.getString(R.string.rclone_diagnostic_exit_code, it)) }
            lastRcloneDiagnostic?.let {
                if (isNotEmpty()) append('\n')
                append(mContext.getString(R.string.rclone_diagnostic_output, it))
            }
        }
        val fallback = if (diagnostic.isBlank()) content else "$content\n$diagnostic"
        val userError = RcloneErrorMapper.map(
            mContext,
            errors.ifBlank { diagnostic.ifBlank { content } },
            fallback
        )
        val text = userError.message

        var notifyTitle = mContext.getString(R.string.operation_failed)
        if (wasCancelled) {
            notifyTitle = mContext.getString(R.string.operation_failed_cancelled)
        }
        SyncLog.error(mContext, notifyTitle, "$mTitle: $text${if (diagnostic.isBlank()) "" else "\n$diagnostic"}")
        mNotificationManager.showFailedNotificationOrReport(
            mTitle,
            text,
            notificationId,
            mTask.id,
            userError.action,
            userError.actionLabel
        )
    }

    private fun arePreconditionsMet(): Boolean {
        val connection = WifiConnectivitiyUtil.dataConnection(this.applicationContext)
        if (mTask.wifionly && connection === WifiConnectivitiyUtil.Connection.METERED) {
            failureReason = FAILURE_REASON.NO_UNMETERED
            return false
        } else if (connection === WifiConnectivitiyUtil.Connection.DISCONNECTED || connection === WifiConnectivitiyUtil.Connection.NOT_AVAILABLE) {
            failureReason = FAILURE_REASON.NO_CONNECTION
            return false
        }

        return true
    }

    private fun prepareNotifications() {

        GenericSyncNotification(mContext).setNotificationChannel(
                SyncServiceNotifications.CHANNEL_ID,
                getString(R.string.sync_service_notification_channel_title),
                getString(R.string.sync_service_notification_channel_description),
                GROUP_ID,
                getString(R.string.sync_service_notification_group)
        )
        GenericSyncNotification(mContext).setNotificationChannel(
            SyncServiceNotifications.CHANNEL_SUCCESS_ID,
            getString(R.string.sync_service_notification_channel_success_title),
            getString(R.string.sync_service_notification_channel_success_description),
                GROUP_ID,
                getString(R.string.sync_service_notification_group)
        )
        GenericSyncNotification(mContext).setNotificationChannel(
            SyncServiceNotifications.CHANNEL_FAIL_ID,
            getString(R.string.sync_service_notification_channel_fail_title),
            getString(R.string.sync_service_notification_channel_fail_description),
                GROUP_ID,
                getString(R.string.sync_service_notification_group)
        )
        GenericSyncNotification(mContext).setNotificationChannel(
            ReportNotifications.CHANNEL_REPORT_ID,
            getString(R.string.sync_service_notification_channel_report_title),
            getString(R.string.sync_service_notification_channel_report_description),
                GROUP_ID,
                getString(R.string.sync_service_notification_group)
        )

    }

    private fun sendUploadFinishedBroadcast(remote: String, path: String?) {
        val intent = Intent()
        intent.action = getString(R.string.background_service_broadcast)
        intent.putExtra(getString(R.string.background_service_broadcast_data_remote), remote)
        intent.putExtra(getString(R.string.background_service_broadcast_data_path), path)
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent)
    }

    // Creates an instance of ForegroundInfo which can be used to update the
    // ongoing notification.
    private fun updateForegroundNotification(notification: Notification?) {
        notification?.let {
            try {
                setForegroundAsync(ForegroundInfo(ongoingNotificationID, it, FOREGROUND_SERVICE_TYPE_DATA_SYNC))
            } catch (e: Exception) {
                FLog.e(TAG, "updateForegroundNotification: could not promote to foreground service", e)
            }
        }
    }


    private fun log(message: String) {
        FLog.e(TAG, "SyncWorker: $message")
    }

    private fun getString(@StringRes resId: Int): String {
        return mContext.getString(resId)
    }

    private fun followupTask(followUpTaskID: Long?) {
        if (followUpTaskID == null || followUpTaskID == -1L) {
            return
        }
        // Stop self-referencing or runaway follow-up chains instead of looping forever.
        if (followUpTaskID == mTask.id || mFollowupDepth >= MAX_FOLLOWUP_DEPTH) {
            SyncLog.error(mContext, mTitle, getString(R.string.followup_chain_stopped))
            return
        }
        Thread.sleep(1000)
        SyncManager(mContext).queueFollowup(followUpTaskID, mRequiresCharging, mFollowupDepth + 1)
    }
}
