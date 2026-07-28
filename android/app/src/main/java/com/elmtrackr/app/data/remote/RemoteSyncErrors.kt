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
