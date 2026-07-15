package ca.pkay.rcloneexplorer.workmanager

import android.os.Build
import ca.pkay.rcloneexplorer.util.FLog
import java.util.concurrent.locks.ReentrantLock

object TransferRuntime {
    private const val TAG = "TransferRuntime"
    private const val GRACEFUL_STOP_TIMEOUT_MS = 10_000L
    private const val STOP_POLL_MS = 100L

    val executionLock = ReentrantLock(true)

    fun terminateGracefully(process: Process?): Boolean {
        if (process == null || !isAlive(process)) {
            return true
        }

        process.destroy()
        val deadline = System.currentTimeMillis() + GRACEFUL_STOP_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (!isAlive(process)) {
                return true
            }
            try {
                Thread.sleep(STOP_POLL_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }

        FLog.e(TAG, "rclone did not stop after SIGTERM; forcing termination")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            process.destroyForcibly()
        } else {
            process.destroy()
        }
        return !isAlive(process)
    }

    fun isAlive(process: Process): Boolean {
        return try {
            process.exitValue()
            false
        } catch (_: IllegalThreadStateException) {
            true
        }
    }
}
