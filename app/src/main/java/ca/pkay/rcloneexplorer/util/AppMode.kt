package ca.pkay.rcloneexplorer.util

import android.content.Context
import androidx.preference.PreferenceManager
import ca.pkay.rcloneexplorer.R
import java.io.File

object AppMode {

    const val MODE_SIMPLE = "simple"
    const val MODE_ADVANCED = "advanced"

    private const val GUIDED_TASK_IDS_KEY = "courier_guided_task_ids"
    private const val GUIDED_TASK_CATEGORIES_KEY = "courier_guided_task_categories"
    private const val GUIDED_TRIGGER_ID_KEY = "courier_guided_trigger_id"
    private const val GUIDED_REMOTE_NAME_KEY = "courier_guided_remote_name"
    private const val GUIDED_REMOTE_ADOPTED_KEY = "courier_guided_remote_adopted"
    private const val GUIDED_SHARE_KEY = "courier_guided_share"
    private const val GUIDED_DEVICE_NAME_KEY = "courier_guided_device_name"
    private const val GUIDED_SETUP_COMPLETE_KEY = "courier_guided_setup_complete"

    @JvmStatic
    fun isSimpleMode(context: Context): Boolean = getMode(context) == MODE_SIMPLE

    @JvmStatic
    fun getMode(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val key = context.getString(R.string.pref_key_app_mode)
        prefs.getString(key, null)?.let { return it }
        return if (hasExistingConfiguration(context)) MODE_ADVANCED else MODE_SIMPLE
    }

    @JvmStatic
    fun hasChosenMode(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getString(context.getString(R.string.pref_key_app_mode), null) != null
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
        val categories = getGuidedTaskCategories(context).toMutableMap()
        categories.remove(taskId)
        prefs.edit()
            .putStringSet(GUIDED_TASK_IDS_KEY, updated)
            .putStringSet(GUIDED_TASK_CATEGORIES_KEY, encodeTaskCategories(categories))
            .apply()
    }

    @JvmStatic
    fun getGuidedTaskCategories(context: Context): Map<Long, String> {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getStringSet(GUIDED_TASK_CATEGORIES_KEY, emptySet())
            .orEmpty()
            .mapNotNull { entry ->
                val separator = entry.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val taskId = entry.substring(0, separator).toLongOrNull() ?: return@mapNotNull null
                taskId to entry.substring(separator + 1)
            }
            .toMap()
    }

    @JvmStatic
    fun getGuidedTriggerId(context: Context): Long {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getLong(GUIDED_TRIGGER_ID_KEY, -1L)
    }

    @JvmStatic
    fun getGuidedRemoteName(context: Context): String? {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getString(GUIDED_REMOTE_NAME_KEY, null)
    }

    @JvmStatic
    fun isGuidedRemoteAdopted(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(GUIDED_REMOTE_ADOPTED_KEY, false)
    }

    @JvmStatic
    fun getGuidedShare(context: Context): String? {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getString(GUIDED_SHARE_KEY, null)
    }

    @JvmStatic
    fun getGuidedDeviceName(context: Context): String? {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getString(GUIDED_DEVICE_NAME_KEY, null)
    }

    @JvmStatic
    fun isGuidedSetupComplete(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean(GUIDED_SETUP_COMPLETE_KEY, false) && getGuidedTaskIds(context).isNotEmpty()
    }

    @JvmStatic
    fun setGuidedConfiguration(
        context: Context,
        taskCategories: Map<Long, String>,
        triggerId: Long,
        remoteName: String,
        share: String,
        deviceName: String,
        remoteAdopted: Boolean
    ) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putStringSet(GUIDED_TASK_IDS_KEY, taskCategories.keys.map(Long::toString).toSet())
            .putStringSet(GUIDED_TASK_CATEGORIES_KEY, encodeTaskCategories(taskCategories))
            .putLong(GUIDED_TRIGGER_ID_KEY, triggerId)
            .putString(GUIDED_REMOTE_NAME_KEY, remoteName)
            .putBoolean(GUIDED_REMOTE_ADOPTED_KEY, remoteAdopted)
            .putString(GUIDED_SHARE_KEY, share)
            .putString(GUIDED_DEVICE_NAME_KEY, deviceName)
            .putBoolean(GUIDED_SETUP_COMPLETE_KEY, true)
            .apply()
    }

    @JvmStatic
    fun clearGuidedConfiguration(context: Context) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .remove(GUIDED_TASK_IDS_KEY)
            .remove(GUIDED_TASK_CATEGORIES_KEY)
            .remove(GUIDED_TRIGGER_ID_KEY)
            .remove(GUIDED_REMOTE_NAME_KEY)
            .remove(GUIDED_REMOTE_ADOPTED_KEY)
            .remove(GUIDED_SHARE_KEY)
            .remove(GUIDED_DEVICE_NAME_KEY)
            .remove(GUIDED_SETUP_COMPLETE_KEY)
            .apply()
    }

    private fun encodeTaskCategories(categories: Map<Long, String>): Set<String> {
        return categories.map { (taskId, category) -> "$taskId=$category" }.toSet()
    }
}
