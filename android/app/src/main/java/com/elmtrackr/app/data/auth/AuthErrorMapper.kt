package com.elmtrackr.app.data.auth

internal enum class AuthOperation {
    SIGN_IN,
    SIGN_UP,
    PASSWORD_RESET,
}

/** Converts SDK failures into messages that are safe to render in the UI. */
internal object AuthErrorMapper {

    fun messageFor(error: Throwable, operation: AuthOperation): String {
        val details = generateSequence(error) { it.cause }
            .joinToString(" ") { "${it::class.simpleName} ${it.message.orEmpty()}" }
            .lowercase()

        return when {
            details.contains("invalid_credentials") ||
                details.contains("invalid login credentials") ->
                "Incorrect email or password."

            details.contains("email_not_confirmed") ||
                details.contains("email not confirmed") ->
                "Confirm your email before signing in."

            details.contains("user_already_exists") ||
                details.contains("already registered") ||
                details.contains("already exists") ->
                "An account with this email already exists."

            details.contains("weak_password") || details.contains("weak password") ->
                "Choose a stronger password."

            details.contains("rate_limit") ||
                details.contains("rate limit") ||
                details.contains("too many requests") ->
                "Too many attempts. Please wait and try again."

            details.contains("unknownhost") ||
                details.contains("connectexception") ||
                details.contains("sockettimeout") ||
                details.contains("network is unreachable") ||
                details.contains("failed to connect") ->
                "Check your internet connection and try again."

            else -> when (operation) {
                AuthOperation.SIGN_IN -> "Unable to sign in. Please try again."
                AuthOperation.SIGN_UP -> "Unable to create your account. Please try again."
                AuthOperation.PASSWORD_RESET -> "Unable to send the reset email. Please try again."
            }
        }
    }
}
