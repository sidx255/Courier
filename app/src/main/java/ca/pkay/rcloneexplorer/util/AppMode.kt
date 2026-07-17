package ca.pkay.rcloneexplorer.util

import android.content.Context
import androidx.preference.PreferenceManager
import ca.pkay.rcloneexplorer.R
import java.io.File

object AppMode {

    const val MODE_SIMPLE = "simple"
    const val MODE_ADVANCED = "advanced"

    private const val GUIDED_TASK_IDS_KEY = "courier_guided_task_ids"

    @JvmStatic
    fun isSimpleMode(context: Context): Boolean = getMode(context) == MODE_SIMPLE

    @JvmStatic
    fun getMode(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val key = context.getString(R.string.pref_key_app_mode)
        prefs.getString(key, null)?.let { return it }
        val default = if (hasExistingConfiguration(context)) MODE_ADVANCED else MODE_SIMPLE
        prefs.edit().putString(key, default).apply()
        return default
    }

    @JvmStatic
    fun setMode(context: Context, mode: String) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(context.getString(R.string.pref_key_app_mode), mode)
            .apply()
    }

    private fun hasExistingConfiguration(context: Context): Boolean {
        val config = File(context.filesDir, "rclone.conf")
        return config.exists() && config.length() > 0L
    }

    @JvmStatic
    fun getGuidedTaskIds(context: Context): Set<Long> {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getStringSet(GUIDED_TASK_IDS_KEY, emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            ?: emptySet()
    }

    @JvmStatic
    fun isGuidedTask(context: Context, taskId: Long): Boolean =
        getGuidedTaskIds(context).contains(taskId)

    @JvmStatic
    fun addGuidedTask(context: Context, taskId: Long) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val updated = (prefs.getStringSet(GUIDED_TASK_IDS_KEY, emptySet()) ?: emptySet()).toMutableSet()
        updated.add(taskId.toString())
        prefs.edit().putStringSet(GUIDED_TASK_IDS_KEY, updated).apply()
    }

    @JvmStatic
    fun removeGuidedTask(context: Context, taskId: Long) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val updated = (prefs.getStringSet(GUIDED_TASK_IDS_KEY, emptySet()) ?: return).toMutableSet()
        updated.remove(taskId.toString())
        prefs.edit().putStringSet(GUIDED_TASK_IDS_KEY, updated).apply()
    }
}
