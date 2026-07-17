package ca.pkay.rcloneexplorer.guided

import ca.pkay.rcloneexplorer.Items.Trigger
import java.util.Calendar
import java.util.TimeZone

object GuidedSchedule {

    fun nextRunTime(
        trigger: Trigger,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long? {
        if (!trigger.isEnabled || trigger.type != Trigger.TRIGGER_TYPE_SCHEDULE) return null
        val now = Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }
        for (offset in 0..7) {
            val candidate = (now.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, trigger.time / 60)
                set(Calendar.MINUTE, trigger.time % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val triggerDay = when (candidate.get(Calendar.DAY_OF_WEEK)) {
                Calendar.SUNDAY -> Trigger.TRIGGER_DAY_SUN
                else -> candidate.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY
            }
            if (trigger.isEnabledAtDay(triggerDay) && candidate.timeInMillis > nowMillis) {
                return candidate.timeInMillis
            }
        }
        return null
    }
}