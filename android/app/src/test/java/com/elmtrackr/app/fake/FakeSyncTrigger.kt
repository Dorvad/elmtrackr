package com.elmtrackr.app.fake

import com.elmtrackr.app.data.sync.SyncTrigger

class FakeSyncTrigger : SyncTrigger {
    var scheduleCount = 0
        private set

    override fun schedule() {
        scheduleCount++
    }
}
