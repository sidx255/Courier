package ca.pkay.rcloneexplorer.workmanager

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import ca.pkay.rcloneexplorer.Database.DatabaseHandler
import ca.pkay.rcloneexplorer.Items.Task
import ca.pkay.rcloneexplorer.Items.Trigger
import ca.pkay.rcloneexplorer.util.AppMode
import java.util.Random
import java.util.concurrent.TimeUnit

class SyncManager(private var mContext: Context) {

    companion object {
        private const val UNIQUE_TASK_PREFIX = "sync_task_"
        private const val UNIQUE_VERIFY_PREFIX = "sync_verify_"
        private const val UNIQUE_EPHEMERAL_PREFIX = "sync_ephemeral_"

        @JvmStatic
        fun transferWorkName(taskId: Long): String = UNIQUE_TASK_PREFIX + taskId

        @JvmStatic
        fun verifyWorkName(taskId: Long): String = UNIQUE_VERIFY_PREFIX + taskId
    }

    fun queue(trigger: Trigger) {
        queue(
            trigger.triggerTarget,
            SyncOperation.TRANSFER,
            ExistingWorkPolicy.KEEP,
            AppMode.isGuidedTask(mContext, trigger.triggerTarget)
        )
    }

    fun queue(task: Task) {
        queue(task.id)
    }

    fun queue(task: Task, operation: SyncOperation) {
        queue(task.id, operation, ExistingWorkPolicy.REPLACE)
    }

    fun queueNow(task: Task) {
        queue(task.id, SyncOperation.TRANSFER, ExistingWorkPolicy.REPLACE)
    }

    fun queue(taskID: Long) {
        queue(taskID, SyncOperation.TRANSFER)
    }

    fun queue(taskID: Long, operation: SyncOperation) {
        queue(taskID, operation, ExistingWorkPolicy.KEEP)
    }

    fun queueFollowup(taskID: Long, requiresCharging: Boolean) {
        queue(taskID, SyncOperation.TRANSFER, ExistingWorkPolicy.KEEP, requiresCharging)
    }

    private fun queue(
        taskID: Long,
        operation: SyncOperation,
        policy: ExistingWorkPolicy,
        requiresCharging: Boolean = false
    ) {
        val requiresUnmetered = DatabaseHandler(mContext).getTask(taskID)?.wifionly == true
        val data = Data.Builder()
        data.putLong(SyncWorker.TASK_ID, taskID)
        data.putString(SyncWorker.TASK_OPERATION, operation.name)
        data.putBoolean(SyncWorker.TASK_REQUIRES_CHARGING, requiresCharging)

        val uploadWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(data.build())
            .addTag(taskID.toString())
            .setConstraints(buildConstraints(requiresCharging, requiresUnmetered))
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        workUnique(uniqueNameFor(operation) + taskID, policy, uploadWorkRequest)
    }

    private fun uniqueNameFor(operation: SyncOperation): String {
        return if (operation == SyncOperation.TRANSFER) UNIQUE_TASK_PREFIX else UNIQUE_VERIFY_PREFIX
    }

    fun queueEphemeral(task: Task) {
        task.id = Random().nextLong()
        val data = Data.Builder()
        data.putString(SyncWorker.TASK_EPHEMERAL, task.asJSON().toString())

        val uploadWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(data.build())
            .addTag(task.id.toString())
            .setConstraints(buildConstraints(false, task.wifionly))
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        workUnique(UNIQUE_EPHEMERAL_PREFIX + task.id, ExistingWorkPolicy.KEEP, uploadWorkRequest)
    }

    private fun buildConstraints(requiresCharging: Boolean, requiresUnmetered: Boolean): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(if (requiresUnmetered) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(requiresCharging)
            .build()
    }

    private fun workUnique(uniqueName: String, policy: ExistingWorkPolicy, request: OneTimeWorkRequest) {
        WorkManager.getInstance(mContext)
            .enqueueUniqueWork(uniqueName, policy, request)
    }

    fun cancel() {
        WorkManager.getInstance(mContext)
            .cancelAllWork()
    }
    fun cancel(tag: String) {

        //Intent syncIntent = new Intent(context, SyncService.class);
        //syncIntent.setAction(TASK_CANCEL_ACTION);
        //syncIntent.putExtra(EXTRA_TASK_ID, intent.getLongExtra(EXTRA_TASK_ID, -1));
        //context.startService(syncIntent);
        Log.e("TAG", "CANCEL"+tag)
        WorkManager
            .getInstance(mContext)
            .cancelAllWorkByTag(tag)
    }
}