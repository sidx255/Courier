package ca.pkay.rcloneexplorer.guided

import android.content.Context
import androidx.preference.PreferenceManager
import ca.pkay.rcloneexplorer.util.AppMode

object GuidedVerificationStatusStore {

    const val OUTCOME_SAFE = "safe"
    const val OUTCOME_DIFFERENT = "different"
    const val OUTCOME_FAILED = "failed"
    const val OUTCOME_CANCELLED = "cancelled"
    const val REPAIR_SUCCESS = "success"
    const val REPAIR_FAILED = "failed"

    data class VerificationStatus(
        val taskId: Long,
        val completedAt: Long,
        val outcome: String,
        val differences: Int
    )

    fun record(context: Context, taskId: Long, outcome: String, differences: Int) {
        if (!AppMode.isGuidedTask(context, taskId)) return
        val prefix = prefix(taskId)
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putLong(prefix + "time", System.currentTimeMillis())
            .putString(prefix + "outcome", outcome)
            .putInt(prefix + "differences", differences.coerceAtLeast(0))
            .apply()
    }

    fun get(context: Context, taskId: Long): VerificationStatus? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val prefix = prefix(taskId)
        val completedAt = prefs.getLong(prefix + "time", 0L)
        if (completedAt == 0L) return null
        return VerificationStatus(
            taskId,
            completedAt,
            prefs.getString(prefix + "outcome", OUTCOME_FAILED) ?: OUTCOME_FAILED,
            prefs.getInt(prefix + "differences", 0)
        )
    }

    fun clear(context: Context, taskId: Long) {
        val prefix = prefix(taskId)
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .remove(prefix + "time")
            .remove(prefix + "outcome")
            .remove(prefix + "differences")
            .remove(prefix + "repair_time")
            .remove(prefix + "repair_outcome")
            .apply()
    }

    fun recordRepair(context: Context, taskId: Long, outcome: String) {
        if (!AppMode.isGuidedTask(context, taskId)) return
        val prefix = prefix(taskId)
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putLong(prefix + "repair_time", System.currentTimeMillis())
            .putString(prefix + "repair_outcome", outcome)
            .apply()
    }

    fun getRepair(context: Context, taskId: Long): Pair<Long, String>? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val prefix = prefix(taskId)
        val completedAt = prefs.getLong(prefix + "repair_time", 0L)
        if (completedAt == 0L) return null
        return completedAt to (prefs.getString(prefix + "repair_outcome", REPAIR_FAILED) ?: REPAIR_FAILED)
    }

    private fun prefix(taskId: Long) = "courier_guided_verify_${taskId}_"
}