package ca.pkay.rcloneexplorer.guided

import android.content.Context
import androidx.preference.PreferenceManager
import ca.pkay.rcloneexplorer.util.AppMode

object GuidedBackupStatusStore {

    const val OUTCOME_SUCCESS = "success"
    const val OUTCOME_FAILED = "failed"
    const val OUTCOME_CANCELLED = "cancelled"

    data class TaskStatus(
        val taskId: Long,
        val completedAt: Long,
        val outcome: String,
        val transferredBytes: Long,
        val transferredFiles: Int
    )

    @JvmStatic
    fun record(
        context: Context,
        taskId: Long,
        outcome: String,
        transferredBytes: Long,
        transferredFiles: Int
    ) {
        if (!AppMode.isGuidedTask(context, taskId)) return
        val prefix = prefix(taskId)
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putLong(prefix + "time", System.currentTimeMillis())
            .putString(prefix + "outcome", outcome)
            .putLong(prefix + "bytes", transferredBytes.coerceAtLeast(0L))
            .putInt(prefix + "files", transferredFiles.coerceAtLeast(0))
            .apply()
    }

    @JvmStatic
    fun get(context: Context, taskId: Long): TaskStatus? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val prefix = prefix(taskId)
        val time = prefs.getLong(prefix + "time", 0L)
        if (time == 0L) return null
        return TaskStatus(
            taskId,
            time,
            prefs.getString(prefix + "outcome", OUTCOME_FAILED) ?: OUTCOME_FAILED,
            prefs.getLong(prefix + "bytes", 0L),
            prefs.getInt(prefix + "files", 0)
        )
    }

    @JvmStatic
    fun clear(context: Context, taskId: Long) {
        val prefix = prefix(taskId)
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .remove(prefix + "time")
            .remove(prefix + "outcome")
            .remove(prefix + "bytes")
            .remove(prefix + "files")
            .apply()
    }

    private fun prefix(taskId: Long) = "courier_guided_status_${taskId}_"
}