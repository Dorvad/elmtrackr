package com.elmtrackr.app.data.auth

import androidx.annotation.StringRes
import com.elmtrackr.app.R

internal enum class AuthOperation {
    SIGN_IN,
    SIGN_UP,
    GOOGLE_SIGN_IN,
    PASSWORD_RESET,
    PASSWORD_RECOVERY,
    UPDATE_PASSWORD,
    DELETE_ACCOUNT,
}

/**
 * Converts SDK failures into string resources that are safe to render in the
 * UI (no URLs, tokens, or stack-trace text) and localize at render time.
 */
internal object AuthErrorMapper {

    @StringRes
    fun messageResFor(error: Throwable, operation: AuthOperation): Int {
        val details = generateSequence(error) { it.cause }
            .joinToString(" ") { "${it::class.simpleName} ${it.message.orEmpty()}" }
            .lowercase()

        return when {
            details.contains("invalid_credentials") ||
                details.contains("invalid login credentials") ->
                R.string.auth_error_incorrect_credentials

            details.contains("email_not_confirmed") ||
                details.contains("email not confirmed") ->
                R.string.auth_error_confirm_email

            details.contains("user_already_exists") ||
                details.contains("already registered") ||
                details.contains("already exists") ->
                R.string.auth_error_account_exists

            details.contains("weak_password") || details.contains("weak password") ->
                R.string.auth_error_weak_password

            details.contains("rate_limit") ||
                details.contains("rate limit") ||
                details.contains("too many requests") ->
                R.string.auth_error_rate_limited

            details.contains("unknownhost") ||
                details.contains("connectexception") ||
                details.contains("sockettimeout") ||
                details.contains("network is unreachable") ||
                details.contains("failed to connect") ->
                R.string.auth_error_network

            else -> when (operation) {
                AuthOperation.SIGN_IN -> R.string.auth_error_sign_in
                AuthOperation.SIGN_UP -> R.string.auth_error_sign_up
                AuthOperation.GOOGLE_SIGN_IN -> R.string.auth_error_google_sign_in
                AuthOperation.PASSWORD_RESET -> R.string.auth_error_password_reset
                AuthOperation.PASSWORD_RECOVERY -> R.string.auth_error_recovery_link
                AuthOperation.UPDATE_PASSWORD -> R.string.auth_error_update_password
                AuthOperation.DELETE_ACCOUNT -> R.string.auth_error_delete_account
            }
        }
    }
}
