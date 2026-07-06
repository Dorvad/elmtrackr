package com.elmtrackr.app.data.local.entity

enum class SyncStatus {
    PENDING_CREATE,
    PENDING_UPDATE,
    PENDING_DELETE,
    SYNCED,
    FAILED,
    ;

    companion object {
        /**
         * Parse a persisted status without crashing on unknown values.
         * Falls back to [PENDING_UPDATE] so the row is re-pushed on the next
         * sync instead of failing every read of its table.
         */
        fun fromPersisted(raw: String?): SyncStatus {
            if (raw.isNullOrBlank()) return PENDING_UPDATE
            val normalized = raw.trim().uppercase().replace('-', '_')
            return entries.find { it.name == normalized } ?: PENDING_UPDATE
        }
    }
}
