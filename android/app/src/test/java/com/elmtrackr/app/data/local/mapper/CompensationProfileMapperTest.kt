package com.elmtrackr.app.data.local.mapper

import com.elmtrackr.app.data.local.entity.CompensationProfileEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.domain.model.RegionCode
import org.junit.Assert.assertEquals
import org.junit.Test

class CompensationProfileMapperTest {

    private fun entity(regionCode: String = "IL") = CompensationProfileEntity(
        localId = "profile-1",
        remoteId = "remote-1",
        userId = "u1",
        name = "Main job",
        regionCode = regionCode,
        currencyCode = "ILS",
        timezone = "Asia/Jerusalem",
        baseHourlyRate = 50.0,
        rulesJson = "{}",
        stackingPolicy = "highest_only",
        effectiveFrom = 0L,
        effectiveUntil = null,
        isDefault = true,
        isArchived = false,
        createdAt = 0L,
        updatedAt = 0L,
        deletedAt = null,
        syncStatus = SyncStatus.SYNCED,
        lastSyncError = null,
        lastSyncedAt = null,
    )

    @Test
    fun `toDomain accepts uppercase region codes`() {
        assertEquals(RegionCode.IL, entity("IL").toDomain().regionCode)
    }

    @Test
    fun `toDomain accepts lowercase web region codes`() {
        assertEquals(RegionCode.US, entity("us").toDomain().regionCode)
    }

    @Test
    fun `toDomain falls back for unknown region codes`() {
        assertEquals(RegionCode.IL, entity("fellowship").toDomain().regionCode)
    }
}
