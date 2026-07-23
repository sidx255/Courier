package ca.pkay.rcloneexplorer.workmanager

internal object NotificationUpdateGate {
    fun shouldUpdate(lastUpdateAt: Long, now: Long, intervalMillis: Long = 1_000L): Boolean {
        return lastUpdateAt == 0L || now - lastUpdateAt >= intervalMillis
    }
}