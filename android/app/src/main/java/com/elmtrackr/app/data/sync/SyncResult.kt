package com.elmtrackr.app.data.sync

sealed interface SyncResult {
    data object Success : SyncResult
    data object NotConfigured : SyncResult

    /** The Supabase session is no longer valid; syncing needs a fresh sign-in. */
    data object AuthExpired : SyncResult
    data class Error(val message: String) : SyncResult
}
