package com.elmtrackr.app.data.local.entity

enum class SyncStatus {
    PENDING_CREATE,
    PENDING_UPDATE,
    PENDING_DELETE,
    SYNCED,
    FAILED,
}
