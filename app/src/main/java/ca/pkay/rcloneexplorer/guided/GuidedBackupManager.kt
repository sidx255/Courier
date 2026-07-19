package ca.pkay.rcloneexplorer.guided

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.preference.PreferenceManager
import androidx.work.WorkManager
import ca.pkay.rcloneexplorer.Database.DatabaseHandler
import ca.pkay.rcloneexplorer.Items.Filter
import ca.pkay.rcloneexplorer.Items.FilterEntry
import ca.pkay.rcloneexplorer.Items.RemoteItem
import ca.pkay.rcloneexplorer.Items.SyncDirectionObject
import ca.pkay.rcloneexplorer.Items.Task
import ca.pkay.rcloneexplorer.Items.Trigger
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.Rclone
import ca.pkay.rcloneexplorer.Services.TriggerService
import ca.pkay.rcloneexplorer.util.AppMode
import ca.pkay.rcloneexplorer.util.RcloneErrorMapper
import ca.pkay.rcloneexplorer.workmanager.SyncManager
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.concurrent.thread

class GuidedBackupManager(context: Context) {

    data class NasConnection(
        val host: String,
        val port: Int,
        val share: String,
        val username: String,
        val password: String,
        val domain: String
    )

    data class CategorySpec(
        val key: String,
        val title: String,
        val localPath: String,
        val remoteFolder: String,
        val filters: List<FilterEntry> = emptyList()
    )

    data class ConnectionSummary(val host: String, val share: String, val port: Int)

    data class StorageStats(val files: Long, val bytes: Long, val memories: Long, val complete: Boolean)

    data class ProvisionedConfiguration(
        val remoteName: String,
        val taskIds: List<Long>,
        val triggerId: Long
    )

    sealed class OperationResult<out T> {
        data class Success<T>(val value: T) : OperationResult<T>()
        data class Error(
            val message: String,
            val action: RcloneErrorMapper.Action = RcloneErrorMapper.Action.RETRY,
            val actionLabel: Int = R.string.error_action_retry
        ) : OperationResult<Nothing>()
    }

    private data class ProcessResult(val exitCode: Int, val error: String)

    private val appContext = context.applicationContext
    private val database = DatabaseHandler(appContext)
    private val rclone = Rclone(appContext)
    private val triggerService = TriggerService(appContext)

    fun validateConnection(connection: NasConnection): OperationResult<Unit> {
        val validation = validateInput(connection)
        if (validation != null) return OperationResult.Error(validation)
        val temporaryRemote = "courier_test_${System.currentTimeMillis()}"
        return try {
            val created = createRemote(temporaryRemote, connection)
            if (created is OperationResult.Error) return created
            val access = rclone.checkPathAccessible(RemoteItem(temporaryRemote, "smb"), connection.share)
            if (!access.isAccessible) {
                val raw = if (access.isTimedOut) "timeout" else access.error
                RcloneErrorMapper.map(
                        appContext,
                        raw,
                        "Courier reached the NAS, but could not open that share. Check the share name and sign-in details."
                    ).let { OperationResult.Error(it.message, it.action, it.actionLabel) }
            } else {
                OperationResult.Success(Unit)
            }
        } finally {
            rclone.deleteRemote(temporaryRemote)
        }
    }

    fun getAdoptableRemotes(): List<String> {
        return rclone.remotes
            .filter { it.type == RemoteItem.SMB }
            .map { it.name }
            .sorted()
    }

    fun validateExistingRemote(remoteName: String, share: String): OperationResult<Unit> {
        val cleanShare = share.trim().trim('/')
        if (cleanShare.isBlank()) return OperationResult.Error("Enter the SMB share name.")
        if (!remoteExists(remoteName)) return OperationResult.Error("That remote is no longer available.")
        val access = rclone.checkPathAccessible(RemoteItem(remoteName, "smb"), cleanShare)
        return if (access.isAccessible) {
            OperationResult.Success(Unit)
        } else {
            val raw = if (access.isTimedOut) "timeout" else access.error
            RcloneErrorMapper.map(
                appContext,
                raw,
                "Courier reached the remote, but could not open that share. Check the share name."
            ).let { OperationResult.Error(it.message, it.action, it.actionLabel) }
        }
    }

    fun provision(
        connection: NasConnection?,
        existingRemoteName: String?,
        existingShare: String?,
        selectedCategories: Set<String>,
        customPaths: List<String>,
        deviceName: String
    ): OperationResult<ProvisionedConfiguration> {
        val specs = buildCategorySpecs(selectedCategories, customPaths)
        if (specs.isEmpty()) return OperationResult.Error("Choose at least one folder to protect.")
        val normalizedDeviceName = deviceName.trim()
        if (normalizedDeviceName.isEmpty()) {
            return OperationResult.Error(appContext.getString(R.string.guided_device_required))
        }
        val deviceFolder = sanitizeFolderName(normalizedDeviceName)

        val oldTaskIds = AppMode.getGuidedTaskIds(appContext)
        val oldTriggerId = AppMode.getGuidedTriggerId(appContext)
        val oldTrigger = database.getTrigger(oldTriggerId)
        val oldRemote = AppMode.getGuidedRemoteName(appContext)
        val oldRemoteAdopted = AppMode.isGuidedRemoteAdopted(appContext)
        val oldShare = AppMode.getGuidedShare(appContext)
        val oldTasks = oldTaskIds.mapNotNull(database::getTask)
        val wifiOnly = oldTasks.firstOrNull()?.wifionly ?: true
        val newTaskIds = mutableListOf<Long>()
        val newFilterIds = mutableListOf<Long>()
        var newTriggerId = -1L
        var createdRemote: String? = null
        var remoteAdopted = false

        if (connection != null) {
            validateInput(connection)?.let { return OperationResult.Error(it) }
        }

        try {
            val remoteName: String
            val share: String
            when {
                existingRemoteName != null -> {
                    if (!remoteExists(existingRemoteName)) {
                        throw ProvisioningException("That remote is no longer available. Pick another.")
                    }
                    val adoptShare = existingShare?.trim()?.trim('/').orEmpty()
                    if (adoptShare.isBlank()) throw ProvisioningException("Enter the SMB share name.")
                    if (!canListRemote(existingRemoteName, adoptShare)) {
                        throw ProvisioningException("Courier reached the remote, but could not open that share.")
                    }
                    remoteName = existingRemoteName
                    share = adoptShare
                    remoteAdopted = true
                }
                connection != null -> {
                    val name = uniqueRemoteName()
                    createdRemote = name
                    when (val result = createRemote(name, connection)) {
                        is OperationResult.Error -> throw ProvisioningException(result.message)
                        is OperationResult.Success -> Unit
                    }
                    if (!canListRemote(name, connection.share)) {
                        throw ProvisioningException("The NAS connection was created but the share could not be opened.")
                    }
                    remoteName = name
                    share = connection.share
                }
                else -> {
                    remoteName = oldRemote?.takeIf { remoteExists(it) }
                        ?: throw ProvisioningException("The saved NAS connection is missing. Reconnect the storage first.")
                    share = oldShare
                        ?: throw ProvisioningException("The saved SMB share is missing. Reconnect the storage first.")
                    remoteAdopted = oldRemoteAdopted
                }
            }

            val remoteType = RemoteItem.SMB
            val taskCategories = linkedMapOf<Long, String>()
            val tasks = specs.map { spec ->
                val filterId = if (spec.filters.isNotEmpty()) {
                    val filter = Filter(0).apply {
                        title = "Courier ${spec.title}"
                        setFilters(spec.filters)
                    }
                    database.createFilter(filter).also {
                        if (it.id <= 0L) error("Could not store category filter")
                        newFilterIds += it.id
                    }.id
                } else {
                    null
                }
                val task = Task(0).apply {
                    title = spec.title
                    remoteId = remoteName
                    this.remoteType = remoteType
                    remotePath = "/$share/NAS/$deviceFolder/${spec.remoteFolder}"
                    localPath = spec.localPath
                    direction = SyncDirectionObject.COPY_LOCAL_TO_REMOTE
                    wifionly = wifiOnly
                    this.filterId = filterId
                    onFailFollowup = -1L
                    onSuccessFollowup = -1L
                }
                database.createTask(task).also {
                    if (it.id <= 0L) error("Could not store backup task")
                    newTaskIds += it.id
                    taskCategories[it.id] = spec.key
                }
            }

            tasks.forEachIndexed { index, task ->
                val nextTaskId = tasks.getOrNull(index + 1)?.id ?: -1L
                task.onFailFollowup = nextTaskId
                task.onSuccessFollowup = nextTaskId
                database.updateTask(task)
            }

            val trigger = Trigger(Trigger.TRIGGER_ID_DOESNTEXIST).apply {
                title = "Nightly backup"
                isEnabled = oldTrigger?.isEnabled ?: true
                time = NIGHTLY_BACKUP_MINUTE
                triggerTarget = tasks.first().id
                type = Trigger.TRIGGER_TYPE_SCHEDULE
            }
            database.createTrigger(trigger)
            if (trigger.id <= 0L) error("Could not store backup schedule")
            newTriggerId = trigger.id

            AppMode.setGuidedConfiguration(
                appContext,
                taskCategories,
                trigger.id,
                remoteName,
                share,
                normalizedDeviceName,
                remoteAdopted
            )
            if (trigger.isEnabled) triggerService.queueSingleTrigger(trigger)

            if (oldTriggerId > 0L && oldTriggerId != trigger.id) {
                triggerService.cancelTrigger(oldTriggerId)
                database.deleteTrigger(oldTriggerId)
            }
            oldTaskIds.filterNot(newTaskIds::contains).forEach { taskId ->
                val task = database.getTask(taskId)
                WorkManager.getInstance(appContext).cancelAllWorkByTag(taskId.toString())
                database.deleteTask(taskId)
                task?.filterId?.takeIf { filterId ->
                    filterId > 0L && database.allTasks.none { it.filterId == filterId }
                }?.let(database::deleteFilter)
                GuidedBackupStatusStore.clear(appContext, taskId)
            }
            if (!oldRemote.isNullOrBlank() && oldRemote != remoteName && !oldRemoteAdopted &&
                database.allTasks.none { it.remoteId == oldRemote }
            ) {
                rclone.deleteRemote(oldRemote)
            }

            return OperationResult.Success(
                ProvisionedConfiguration(remoteName, tasks.map(Task::id), trigger.id)
            )
        } catch (exception: Exception) {
            if (newTriggerId > 0L) {
                triggerService.cancelTrigger(newTriggerId)
                database.deleteTrigger(newTriggerId)
            }
            newTaskIds.forEach { database.deleteTask(it) }
            newFilterIds.forEach { database.deleteFilter(it) }
            createdRemote?.let(rclone::deleteRemote)
            return OperationResult.Error(exception.message ?: "Courier could not finish setting up the backup.")
        }
    }

    fun getGuidedTasks(): List<Task> {
        val ids = AppMode.getGuidedTaskIds(appContext)
        return database.allTasks.filter { it.id in ids }
    }

    fun isGuidedConfigurationValid(): Boolean {
        if (!AppMode.isGuidedSetupComplete(appContext)) return false
        val taskIds = AppMode.getGuidedTaskIds(appContext)
        if (taskIds.isEmpty() || taskIds.any { database.getTask(it) == null }) return false
        if (database.getTrigger(AppMode.getGuidedTriggerId(appContext)) == null) return false
        val remoteName = AppMode.getGuidedRemoteName(appContext) ?: return false
        if (!remoteExists(remoteName)) return false
        return !AppMode.getGuidedShare(appContext).isNullOrBlank()
    }

    fun isRestoreDestinationWritable(path: String): Boolean {
        val directory = runCatching { File(path).canonicalFile }.getOrNull() ?: return false
        if (!directory.exists() || !directory.isDirectory || !directory.canWrite()) return false
        val probe = runCatching { File.createTempFile(".courier-write-", ".tmp", directory) }.getOrNull()
            ?: return false
        return try {
            true
        } finally {
            if (!probe.delete()) probe.deleteOnExit()
        }
    }

    fun getCustomTasks(): List<Task> {
        val ids = AppMode.getGuidedTaskIds(appContext)
        return database.allTasks.filterNot { it.id in ids }
    }

    fun getSelectedCategoryKeys(): Set<String> {
        return AppMode.getGuidedTaskCategories(appContext).values.toSet()
    }

    fun getCustomPaths(): List<String> {
        val categories = AppMode.getGuidedTaskCategories(appContext)
        return getGuidedTasks()
            .filter { categories[it.id]?.startsWith(CATEGORY_CUSTOM_PREFIX) == true }
            .map(Task::localPath)
    }

    fun backupNow(): Boolean {
        val firstTask = database.getTrigger(AppMode.getGuidedTriggerId(appContext))
            ?.let { database.getTask(it.triggerTarget) }
            ?: getGuidedTasks().firstOrNull()
            ?: return false
        SyncManager(appContext).queueNow(firstTask)
        return true
    }

    fun checkBackupSafety(): Int {
        val tasks = getGuidedTasks()
        tasks.forEach { task ->
            GuidedVerificationStatusStore.clear(appContext, task.id)
            SyncManager(appContext).queue(task, ca.pkay.rcloneexplorer.workmanager.SyncOperation.VERIFY_DEEP)
        }
        return tasks.size
    }

    fun repairBackupDifferences(taskIds: Set<Long>): Int {
        val guidedIds = AppMode.getGuidedTaskIds(appContext)
        val tasks = taskIds.filter(guidedIds::contains).mapNotNull(database::getTask)
        tasks.forEach { task ->
            SyncManager(appContext).queue(task, ca.pkay.rcloneexplorer.workmanager.SyncOperation.REPAIR_DEEP)
        }
        return tasks.size
    }

    fun restore(taskIds: Set<Long>, destinationRoot: String? = null): Int {
        val guidedIds = AppMode.getGuidedTaskIds(appContext)
        val tasks = taskIds.asSequence()
            .filter(guidedIds::contains)
            .mapNotNull(database::getTask)
            .toList()
        tasks.forEach { backupTask ->
            val restoreTask = Task(0).apply {
                title = "Restore ${backupTask.title}"
                remoteId = backupTask.remoteId
                remoteType = backupTask.remoteType
                remotePath = backupTask.remotePath
                localPath = destinationRoot?.let {
                    File(it, sanitizeFolderName(backupTask.title)).absolutePath
                } ?: backupTask.localPath
                direction = SyncDirectionObject.COPY_REMOTE_TO_LOCAL
                wifionly = true
                filterId = backupTask.filterId
            }
            SyncManager(appContext).queueEphemeral(restoreTask)
        }
        return tasks.size
    }

    fun isAutoBackupEnabled(): Boolean {
        return database.getTrigger(AppMode.getGuidedTriggerId(appContext))?.isEnabled == true
    }

    fun setAutoBackupEnabled(enabled: Boolean): Boolean {
        val trigger = database.getTrigger(AppMode.getGuidedTriggerId(appContext)) ?: return false
        trigger.isEnabled = enabled
        database.updateTrigger(trigger)
        if (enabled) triggerService.queueSingleTrigger(trigger) else triggerService.cancelTrigger(trigger.id)
        return true
    }

    fun isWifiOnly(): Boolean {
        val tasks = getGuidedTasks()
        return tasks.isNotEmpty() && tasks.all(Task::wifionly)
    }

    fun setWifiOnly(enabled: Boolean) {
        getGuidedTasks().forEach { task ->
            task.wifionly = enabled
            database.updateTask(task)
        }
    }

    fun getNextRunTime(nowMillis: Long = System.currentTimeMillis()): Long? {
        val trigger = database.getTrigger(AppMode.getGuidedTriggerId(appContext))
            ?: return null
        return GuidedSchedule.nextRunTime(trigger, nowMillis)
    }

    fun getConnectionSummary(): ConnectionSummary? {
        val remoteName = AppMode.getGuidedRemoteName(appContext)?.takeIf(::remoteExists) ?: return null
        val share = AppMode.getGuidedShare(appContext) ?: return null
        val config = rclone.getConfig(remoteName) ?: return null
        return runCatching {
            ConnectionSummary(
                config["host"].orEmpty(),
                share,
                config["port"]?.toIntOrNull() ?: 445
            )
        }.getOrNull()
    }

    fun getDeviceName(): String = AppMode.getGuidedDeviceName(appContext) ?: defaultDeviceName()

    fun defaultDeviceName(): String {
        val model = Build.MODEL?.trim().orEmpty().ifBlank { "Android" }
        val preferences = PreferenceManager.getDefaultSharedPreferences(appContext)
        val suffix = preferences.getString(DEVICE_SUFFIX_KEY, null) ?: UUID.randomUUID()
            .toString()
            .replace("-", "")
            .take(8)
            .also { preferences.edit().putString(DEVICE_SUFFIX_KEY, it).apply() }
        return "$model $suffix"
    }

    fun getStorageStats(): StorageStats {
        val tasks = getGuidedTasks()
        if (tasks.isEmpty()) return StorageStats(0L, 0L, 0L, true)
        val categories = AppMode.getGuidedTaskCategories(appContext)
        val executor = Executors.newFixedThreadPool(minOf(tasks.size, 4))
        val futures: List<Pair<Task, Future<JSONObject?>>> = tasks.map { task ->
            task to executor.submit<JSONObject?> {
                rclone.getPathStats(RemoteItem(task.remoteId, "smb"), task.remotePath)
            }
        }
        var files = 0L
        var bytes = 0L
        var memories = 0L
        var complete = true
        try {
            futures.forEach { (task, future) ->
                val stats = runCatching { future.get() }.getOrNull()
                if (stats == null) {
                    complete = false
                } else {
                    files += stats.optLong("count", 0L)
                    bytes += stats.optLong("bytes", 0L)
                    if (categories[task.id] == CATEGORY_PHOTOS) {
                        memories += stats.optLong("count", 0L)
                    }
                }
            }
        } finally {
            executor.shutdownNow()
        }
        return StorageStats(files, bytes, memories, complete)
    }

    fun isCategoryAvailable(category: String): Boolean {
        val root = Environment.getExternalStorageDirectory()
        return when (category) {
            CATEGORY_PHOTOS -> listOf("DCIM", "Pictures", "Movies").any { File(root, it).exists() }
            CATEGORY_WHATSAPP -> File(root, "Android/media/com.whatsapp/WhatsApp").exists() ||
                    File(root, "WhatsApp").exists()
            CATEGORY_DOCUMENTS -> File(root, "Documents").exists()
            CATEGORY_DOWNLOADS -> File(root, "Download").exists()
            else -> false
        }
    }

    private fun buildCategorySpecs(selected: Set<String>, customPaths: List<String>): List<CategorySpec> {
        val root = Environment.getExternalStorageDirectory().absolutePath
        val specs = mutableListOf<CategorySpec>()
        if (CATEGORY_PHOTOS in selected) {
            specs += CategorySpec(
                CATEGORY_PHOTOS,
                "Photos and videos",
                root,
                "Photos and Videos",
                listOf(
                    FilterEntry(FilterEntry.FILTER_INCLUDE, "/DCIM/**"),
                    FilterEntry(FilterEntry.FILTER_INCLUDE, "/Pictures/**"),
                    FilterEntry(FilterEntry.FILTER_INCLUDE, "/Movies/**"),
                    FilterEntry(FilterEntry.FILTER_EXCLUDE, "/**")
                )
            )
        }
        if (CATEGORY_WHATSAPP in selected) {
            val modern = File(root, "Android/media/com.whatsapp/WhatsApp")
            val legacy = File(root, "WhatsApp")
            val source = if (modern.exists() || !legacy.exists()) modern else legacy
            specs += CategorySpec(CATEGORY_WHATSAPP, "WhatsApp", source.absolutePath, "WhatsApp")
        }
        if (CATEGORY_DOCUMENTS in selected) {
            specs += CategorySpec(CATEGORY_DOCUMENTS, "Documents", File(root, "Documents").absolutePath, "Documents")
        }
        if (CATEGORY_DOWNLOADS in selected) {
            specs += CategorySpec(CATEGORY_DOWNLOADS, "Downloads", File(root, "Download").absolutePath, "Downloads")
        }
        customPaths.map(String::trim).filter(String::isNotEmpty).distinct().forEach { path ->
            val file = File(path)
            val name = file.name.ifBlank { "Custom" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val key = CATEGORY_CUSTOM_PREFIX + UUID.nameUUIDFromBytes(file.absolutePath.toByteArray())
            specs += CategorySpec(key, name, file.absolutePath, "Custom - $name")
        }
        return specs
    }

    private fun createRemote(name: String, connection: NasConnection): OperationResult<Unit> {
        val options = arrayListOf(name, "smb", "host", connection.host, "port", connection.port.toString())
        options += listOf("user", connection.username, "pass", connection.password)
        if (connection.domain.isNotEmpty()) options += listOf("domain", connection.domain)
        val process = rclone.configCreate(options)
            ?: return OperationResult.Error("Courier could not start the secure NAS connection.")
        val result = waitFor(process)
        return if (result.exitCode == 0) {
            OperationResult.Success(Unit)
        } else {
            humanizeError(result.error).let { OperationResult.Error(it.message, it.action, it.actionLabel) }
        }
    }

    private fun waitFor(process: Process): ProcessResult {
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val stdoutThread = thread(isDaemon = true) {
            BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                lines.forEach { stdout.appendLine(it) }
            }
        }
        val stderrThread = thread(isDaemon = true) {
            BufferedReader(InputStreamReader(process.errorStream)).useLines { lines ->
                lines.forEach { stderr.appendLine(it) }
            }
        }
        val exitCode = process.waitFor()
        stdoutThread.join()
        stderrThread.join()
        return ProcessResult(exitCode, stderr.toString())
    }

    private fun humanizeError(error: String): RcloneErrorMapper.UserError = RcloneErrorMapper.map(
        appContext,
        error,
        "Courier could not create the NAS connection. Check the address, share, and sign-in details."
    )

    private fun validateInput(connection: NasConnection): String? {
        if (connection.host.isBlank()) return "Enter or select the NAS address."
        if (connection.port !in 1..65535) return "Enter a port between 1 and 65535."
        if (connection.share.isBlank()) return "Enter the SMB share name."
        return null
    }

    private fun canListRemote(name: String, share: String): Boolean {
        return rclone.isPathAccessible(RemoteItem(name, "smb"), share)
    }

    private fun uniqueRemoteName(): String {
        val names = rclone.remotes.map(RemoteItem::getName).toSet()
        if (DEFAULT_REMOTE_NAME !in names) return DEFAULT_REMOTE_NAME
        var suffix = 2
        while ("${DEFAULT_REMOTE_NAME}_$suffix" in names) suffix++
        return "${DEFAULT_REMOTE_NAME}_$suffix"
    }

    private fun remoteExists(name: String): Boolean = rclone.remotes.any { it.name == name }

    private fun sanitizeFolderName(value: String): String {
        return value.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim('.', ' ')
            .ifBlank { "Android" }
    }

    private class ProvisioningException(message: String) : Exception(message)

    companion object {
        const val CATEGORY_PHOTOS = "photos"
        const val CATEGORY_WHATSAPP = "whatsapp"
        const val CATEGORY_DOCUMENTS = "documents"
        const val CATEGORY_DOWNLOADS = "downloads"
        const val CATEGORY_CUSTOM_PREFIX = "custom:"
        const val NIGHTLY_BACKUP_MINUTE = 2 * 60
        private const val DEFAULT_REMOTE_NAME = "courier_nas"
        private const val DEVICE_SUFFIX_KEY = "courier_guided_device_suffix"
    }
}