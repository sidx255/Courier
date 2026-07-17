package ca.pkay.rcloneexplorer.guided

import ca.pkay.rcloneexplorer.Items.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class GuidedScheduleTest {

    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun schedulesTodayWhenNightlyTimeIsAhead() {
        val trigger = nightlyTrigger()
        val now = instant(2026, Calendar.JULY, 20, 1, 30)

        assertEquals(instant(2026, Calendar.JULY, 20, 2, 0), GuidedSchedule.nextRunTime(trigger, now, utc))
    }

    @Test
    fun schedulesTomorrowWhenNightlyTimePassed() {
        val trigger = nightlyTrigger()
        val now = instant(2026, Calendar.JULY, 20, 2, 1)

        assertEquals(instant(2026, Calendar.JULY, 21, 2, 0), GuidedSchedule.nextRunTime(trigger, now, utc))
    }

    @Test
    fun skipsDisabledWeekdays() {
        val trigger = nightlyTrigger().apply {
            setEnabledAtDay(Trigger.TRIGGER_DAY_TUE, false)
        }
        val now = instant(2026, Calendar.JULY, 20, 3, 0)

        assertEquals(instant(2026, Calendar.JULY, 22, 2, 0), GuidedSchedule.nextRunTime(trigger, now, utc))
    }

    @Test
    fun disabledTriggerHasNoNextRun() {
        val trigger = nightlyTrigger().apply { isEnabled = false }

        assertNull(GuidedSchedule.nextRunTime(trigger, instant(2026, Calendar.JULY, 20, 1, 0), utc))
    }

    private fun nightlyTrigger() = Trigger(1).apply {
        isEnabled = true
        type = Trigger.TRIGGER_TYPE_SCHEDULE
        time = GuidedBackupManager.NIGHTLY_BACKUP_MINUTE
    }

    private fun instant(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance(utc).apply {
            clear()
            set(year, month, day, hour, minute)
        }.timeInMillis
    }
}