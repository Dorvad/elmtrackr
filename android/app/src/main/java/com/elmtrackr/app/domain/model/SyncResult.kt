package com.elmtrackr.app.domain.model

sealed interface SyncResult {
    data object Success : SyncResult
    data object NotConfigured : SyncResult
    data class PartialSuccess(val errors: List<SyncItemError>) : SyncResult
    data class Failure(val message: String) : SyncResult
}

data class SyncItemError(
    val entityType: String,
    val localId: String,
    val message: String,
)
