package ca.pkay.rcloneexplorer.util

import android.content.Context
import androidx.annotation.StringRes
import ca.pkay.rcloneexplorer.R

object RcloneErrorMapper {

    enum class Action {
        RETRY,
        EDIT_CONNECTION,
        CHECK_NETWORK,
        REPAIR,
        NONE,
        UNKNOWN
    }

    data class UserError(
        val message: String,
        val action: Action,
        @StringRes val actionLabel: Int
    )

    @JvmStatic
    fun map(context: Context, rawError: String?, fallback: String): UserError {
        val normalized = rawError.orEmpty().lowercase()
        return when (classify(rawError)) {
            Action.EDIT_CONNECTION -> UserError(
                context.getString(
                    if (normalized.contains("connection refused")) {
                        R.string.error_plain_refused
                    } else {
                        R.string.error_plain_auth
                    }
                ),
                Action.EDIT_CONNECTION,
                R.string.error_action_connection
            )
            Action.CHECK_NETWORK -> UserError(
                context.getString(
                    if (normalized.contains("unmetered") || normalized.contains("wi-fi") || normalized.contains("wifi")) {
                        R.string.error_plain_wifi
                    } else if (normalized.contains("timeout") || normalized.contains("timed out")) {
                        R.string.error_plain_timeout
                    } else {
                        R.string.error_plain_no_route
                    }
                ),
                Action.CHECK_NETWORK,
                R.string.error_action_network
            )
            Action.RETRY -> UserError(
                context.getString(R.string.error_plain_offline),
                Action.RETRY,
                R.string.error_action_retry
            )
            Action.NONE -> UserError(
                context.getString(R.string.error_plain_hashless),
                Action.NONE,
                0
            )
            Action.REPAIR -> UserError(
                context.getString(R.string.error_plain_differences),
                Action.REPAIR,
                R.string.error_action_repair
            )
            Action.UNKNOWN -> UserError(fallback, Action.RETRY, R.string.error_action_retry)
        }
    }

    fun classify(rawError: String?): Action {
        val normalized = rawError.orEmpty().lowercase()
        return when {
            normalized.contains("logon failure") ||
                    normalized.contains("authentication") ||
                    normalized.contains("access denied") ||
                    normalized.contains("permission denied") ||
                    normalized.contains("connection refused") -> Action.EDIT_CONNECTION
            normalized.contains("no route to host") ||
                    normalized.contains("network is unreachable") ||
                    normalized.contains("host is unreachable") ||
                    normalized.contains("unmetered") ||
                    normalized.contains("wi-fi") ||
                    normalized.contains("wifi") ||
                    normalized.contains("timeout") ||
                    normalized.contains("timed out") -> Action.CHECK_NETWORK
            normalized.contains("no network connection") || normalized.contains("no connection") -> Action.RETRY
            normalized.contains("no common hash") -> Action.NONE
            normalized.contains("differ") || normalized.contains("mismatch") -> Action.REPAIR
            else -> Action.UNKNOWN
        }
    }
}