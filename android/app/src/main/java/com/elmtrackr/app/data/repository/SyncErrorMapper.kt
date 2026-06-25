package com.elmtrackr.app.data.repository

/** Prevents HTTP headers, tokens, URLs, and backend internals from entering Room or the UI. */
internal object SyncErrorMapper {

    fun messageFor(error: Throwable): String {
        val details = generateSequence(error) { it.cause }
            .joinToString(" ") { "${it::class.simpleName} ${it.message.orEmpty()}" }
            .lowercase()

        return when {
            details.contains("row-level security") ||
                details.contains("code: 42501") ||
                details.contains("unauthorized") ||
                details.contains("forbidden") ->
                "Remote access denied."

            details.contains("code: 23505") || details.contains("duplicate key") ->
                "Remote data conflicts with an existing record."

            details.contains("code: 23503") || details.contains("foreign key") ->
                "A related remote record is missing."

            details.contains("unknownhost") ||
                details.contains("connectexception") ||
            details.contains("sockettimeout") ||
                details.contains("network error") ||
                details.contains("network is unreachable") ||
                details.contains("failed to connect") ->
                "Network unavailable."

            else -> "Remote sync failed."
        }
    }
}
