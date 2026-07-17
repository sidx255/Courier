package ca.pkay.rcloneexplorer.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.widget.RemoteViews
import ca.pkay.rcloneexplorer.Activities.HomeActivity
import ca.pkay.rcloneexplorer.Activities.MainActivity
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.guided.GuidedBackupStatusStore
import ca.pkay.rcloneexplorer.util.AppMode

class BackupStatusWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, buildViews(context)) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, BackupStatusWidget::class.java)
            manager.getAppWidgetIds(component).forEach {
                manager.updateAppWidget(it, buildViews(context))
            }
        }

        private fun buildViews(context: Context): RemoteViews {
            val taskIds = AppMode.getGuidedTaskIds(context)
            val statuses = taskIds.mapNotNull { GuidedBackupStatusStore.get(context, it) }
            val views = RemoteViews(context.packageName, R.layout.widget_backup_status)
            val text = when {
                taskIds.isEmpty() -> context.getString(R.string.widget_backup_setup)
                statuses.size < taskIds.size -> context.getString(R.string.widget_backup_never)
                statuses.all { it.outcome == GuidedBackupStatusStore.OUTCOME_SUCCESS } -> {
                    val protectedAt = statuses.maxOf(GuidedBackupStatusStore.TaskStatus::completedAt)
                    val relative = DateUtils.getRelativeTimeSpanString(
                        protectedAt,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE
                    )
                    context.getString(R.string.widget_backup_safe, relative)
                }
                else -> context.getString(R.string.widget_backup_attention)
            }
            views.setTextViewText(R.id.widget_status_text, text)
            views.setImageViewResource(
                R.id.widget_status_icon,
                if (statuses.size == taskIds.size && statuses.all {
                        it.outcome == GuidedBackupStatusStore.OUTCOME_SUCCESS
                    }) R.drawable.ic_check else R.drawable.ic_info_outline
            )
            val destination = if (AppMode.isGuidedSetupComplete(context)) HomeActivity::class.java else MainActivity::class.java
            val intent = Intent(context, destination)
            val pending = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_status_text, pending)
            views.setOnClickPendingIntent(R.id.widget_status_icon, pending)
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            return views
        }
    }
}