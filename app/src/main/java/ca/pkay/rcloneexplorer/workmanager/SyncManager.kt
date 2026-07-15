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
import ca.pkay.rcloneexplorer.Items.Task
import ca.pkay.rcloneexplorer.Items.Trigger
import java.util.Random
import java.util.concurrent.TimeUnit

class SyncManager(private var mContext: Context) {

    companion object {
        private const val UNIQUE_TASK_PREFIX = "sync_task_"
        private const val UNIQUE_EPHEMERAL_PREFIX = "sync_ephemeral_"
    }

    fun queue(trigger: Trigger) {
        queue(trigger.triggerTarget)
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

    private fun queue(taskID: Long, operation: SyncOperation, policy: ExistingWorkPolicy) {
        val data = Data.Builder()
        data.putLong(SyncWorker.TASK_ID, taskID)
        data.putString(SyncWorker.TASK_OPERATION, operation.name)

        val uploadWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(data.build())
            .addTag(taskID.toString())
            .setConstraints(buildConstraints())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        workUnique(UNIQUE_TASK_PREFIX + taskID, policy, uploadWorkRequest)
    }

    fun queueEphemeral(task: Task) {
        task.id = Random().nextLong()
        val data = Data.Builder()
        data.putString(SyncWorker.TASK_EPHEMERAL, task.asJSON().toString())

        val uploadWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(data.build())
            .addTag(task.id.toString())
            .setConstraints(buildConstraints())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        workUnique(UNIQUE_EPHEMERAL_PREFIX + task.id, ExistingWorkPolicy.KEEP, uploadWorkRequest)
    }

    private fun buildConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
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