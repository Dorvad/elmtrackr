package com.elmtrackr.app.data.sync

sealed interface SyncResult {
    data object Success : SyncResult
    data object NotConfigured : SyncResult
    data class Error(val message: String) : SyncResult
}
