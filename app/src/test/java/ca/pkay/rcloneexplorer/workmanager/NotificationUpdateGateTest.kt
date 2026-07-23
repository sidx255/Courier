package ca.pkay.rcloneexplorer.workmanager

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationUpdateGateTest {

    @Test
    fun allowsFirstUpdate() {
        assertTrue(NotificationUpdateGate.shouldUpdate(0L, 100L))
    }

    @Test
    fun suppressesUpdatesInsideInterval() {
        assertFalse(NotificationUpdateGate.shouldUpdate(1_000L, 1_999L))
    }

    @Test
    fun allowsUpdateAtIntervalBoundary() {
        assertTrue(NotificationUpdateGate.shouldUpdate(1_000L, 2_000L))
    }
}