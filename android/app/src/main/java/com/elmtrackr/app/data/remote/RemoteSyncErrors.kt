package com.elmtrackr.app.data.remote

object RemoteSyncErrors {
    fun isMissingRemoteTable(error: Throwable, table: String): Boolean {
        val message = buildString {
            append(error.message.orEmpty())
            error.cause?.message?.let { append(' ').append(it) }
        }
        if (message.contains("PGRST205", ignoreCase = true)) return true
        if (!message.contains("Could not find the table", ignoreCase = true)) return false
        return message.contains("public.$table", ignoreCase = true) ||
            message.contains("'$table'", ignoreCase = true) ||
            message.contains(".$table", ignoreCase = true)
    }

    /**
     * True when the request was rejected because the Supabase session is no
     * longer valid (expired/invalid JWT, dead refresh token). Matching is
     * message/class-name based so it stays independent of supabase-kt's
     * exception hierarchy.
     */
    fun isAuthExpired(error: Throwable): Boolean =
        generateSequence(error) { it.cause }.take(4).any { e ->
            val className = e::class.simpleName.orEmpty()
            val message = e.message.orEmpty()
            className.contains("Unauthorized", ignoreCase = true) ||
                message.contains("JWT expired", ignoreCase = true) ||
                message.contains("invalid JWT", ignoreCase = true) ||
                message.contains("PGRST301", ignoreCase = true) ||
                message.contains("refresh_token_not_found", ignoreCase = true) ||
                message.contains("Invalid Refresh Token", ignoreCase = true)
        }

    /**
     * A failure a later attempt could plausibly get past on its own.
     *
     * Everything that was not a missing table, an expired session or a unique
     * violation used to be recorded on the row as FAILED, and FAILED is
     * deliberately excluded from the immediate retry path by
     * `hasRetryablePendingWork` — so a clock-out pushed on a flaky connection was
     * not retried until the fifteen-minute periodic run, and sat in the "unsynced
     * changes" count until then. A dropped connection is not a rejection.
     *
     * Matched on message and class name rather than on ktor's or supabase-kt's
     * exception types, like [isAuthExpired] above and for the same reason: the
     * hierarchy is theirs to change.
     *
     * Deliberately narrow. Anything not recognised here still becomes FAILED,
     * because treating an unknown rejection as retryable is how a row that can
     * never succeed becomes an endless loop — the failure mode the August sync
     * work existed to close.
     */
    fun isTransient(error: Throwable): Boolean =
        generateSequence(error) { it.cause }.take(4).any { e ->
            val className = e::class.simpleName.orEmpty()
            val message = e.message.orEmpty()
            className.contains("SocketTimeout", ignoreCase = true) ||
                className.contains("ConnectTimeout", ignoreCase = true) ||
                className.contains("HttpRequestTimeout", ignoreCase = true) ||
                className.contains("UnknownHost", ignoreCase = true) ||
                className.contains("ConnectException", ignoreCase = true) ||
                className.contains("SSLException", ignoreCase = true) ||
                className.contains("IOException", ignoreCase = true) ||
                message.contains("timeout", ignoreCase = true) ||
                message.contains("Unable to resolve host", ignoreCase = true) ||
                message.contains("Connection reset", ignoreCase = true) ||
                message.contains("Software caused connection abort", ignoreCase = true) ||
                // 429 and 5xx: the server is asking for later, not saying no.
                message.contains("Too Many Requests", ignoreCase = true) ||
                message.contains("Bad Gateway", ignoreCase = true) ||
                message.contains("Service Unavailable", ignoreCase = true) ||
                message.contains("Gateway Timeout", ignoreCase = true) ||
                Regex("\\b5\\d\\d\\b").containsMatchIn(message) ||
                message.contains("429", ignoreCase = true)
        }

    /**
     * A foreign-key violation: the row points at a parent the server does not have.
     *
     * This is a **hold**, not a failure. It happens when a child is pushed before
     * its parent has a remote id — a task scoped to a profile that has not synced,
     * a shift carrying a workplace that has not — and the next run, once the
     * parent has landed, succeeds unchanged. Recording it as FAILED instead took
     * the row out of the immediate retry path (`hasRetryablePendingWork` excludes
     * FAILED, deliberately) and left it stuck at fifteen-minute intervals and
     * permanently in the "unsynced changes" count.
     */
    fun isForeignKeyViolation(error: Throwable): Boolean {
        val message = messageOf(error)
        return message.contains("23503", ignoreCase = true) ||
            message.contains("violates foreign key constraint", ignoreCase = true)
    }

    fun isUniqueViolation(error: Throwable): Boolean {
        val message = messageOf(error)
        return message.contains("23505", ignoreCase = true) ||
            message.contains("duplicate key", ignoreCase = true) ||
            message.contains("shifts_user_id_start_time_uidx", ignoreCase = true)
    }

    /**
     * True only when the conflicting constraint is [table]'s primary key.
     *
     * Inserts carry the client-generated local id as the primary key, so a retry after a
     * lost response collides with the row it already created — the one case where adopting
     * the local id as the remote id is correct. [isUniqueViolation] matches *any* 23505,
     * which made that adoption swallow genuine business-rule conflicts: a second refund
     * claim rejected by a `(shift_id, direction)` constraint was marked SYNCED against a
     * remote id that existed on no row, so the ride never reached the server. When the
     * constraint cannot be identified this returns false, and the caller rethrows — the row
     * stays pending and is retried instead of being silently dropped.
     */
    fun isPrimaryKeyViolation(error: Throwable, table: String): Boolean {
        if (!isUniqueViolation(error)) return false
        val message = messageOf(error)
        return message.contains("${table}_pkey", ignoreCase = true) ||
            message.contains("Key (id)=", ignoreCase = true)
    }

    private fun messageOf(error: Throwable): String = buildString {
        append(error.message.orEmpty())
        error.cause?.message?.let { append(' ').append(it) }
    }
}
