package com.elmtrackr.app.data.local.converter

import com.elmtrackr.app.data.local.entity.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun syncStatus_roundTrips_all_values() {
        SyncStatus.entries.forEach { status ->
            assertEquals(status, converters.stringToSyncStatus(converters.syncStatusToString(status)))
        }
    }

    @Test
    fun stringToSyncStatus_tolerates_unknown_values() {
        assertEquals(SyncStatus.PENDING_UPDATE, converters.stringToSyncStatus("LEGACY_STATUS"))
        assertEquals(SyncStatus.PENDING_UPDATE, converters.stringToSyncStatus(""))
    }

    @Test
    fun stringToSyncStatus_tolerates_case_and_separator_variants() {
        assertEquals(SyncStatus.SYNCED, converters.stringToSyncStatus("synced"))
        assertEquals(SyncStatus.PENDING_DELETE, converters.stringToSyncStatus("pending-delete"))
        assertEquals(SyncStatus.FAILED, converters.stringToSyncStatus(" failed "))
    }
}
