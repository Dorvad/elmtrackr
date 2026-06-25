package com.elmtrackr.app.sync

fun interface SyncTrigger {
    fun schedule()
}

val NoOpSyncTrigger: SyncTrigger = SyncTrigger {}
