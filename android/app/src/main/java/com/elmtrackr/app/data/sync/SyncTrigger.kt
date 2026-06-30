package com.elmtrackr.app.data.sync

fun interface SyncTrigger {
    fun schedule()
}

object NoOpSyncTrigger : SyncTrigger {
    override fun schedule() = Unit
}
