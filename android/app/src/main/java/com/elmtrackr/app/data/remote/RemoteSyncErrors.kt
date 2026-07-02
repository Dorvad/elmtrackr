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

    fun isUniqueViolation(error: Throwable): Boolean {
        val message = buildString {
            append(error.message.orEmpty())
            error.cause?.message?.let { append(' ').append(it) }
        }
        return message.contains("23505", ignoreCase = true) ||
            message.contains("duplicate key", ignoreCase = true) ||
            message.contains("shifts_user_id_start_time_uidx", ignoreCase = true)
    }
}
