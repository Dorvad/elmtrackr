package com.elmtrackr.app.data.local.mapper

import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.entity.UserSettingsEntity
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class UserSettingsMapperTest {

    private fun entity(
        weekendDays: String = "5,6",
        clockStyle: String = "CLASSIC",
        hourlyRate: Double? = 60.0,
        currency: String = "ILS",
        onboardingCompleted: Boolean = false,
    ) = UserSettingsEntity(
        localId = "cfg-1",
        remoteId = null,
        userId = "u1",
        timezone = "UTC",
        dailyOvertimeThresholdMinutes = 480,
        weeklyOvertimeThresholdMinutes = 2400,
        weekendDays = weekendDays,
        hourlyRate = hourlyRate,
        currency = currency,
        onboardingCompleted = onboardingCompleted,
        onboardingCompletedAt = null,
        featuresTravelRefunds = false,
        featuresPaidProjects = false,
        featuresInsights = true,
        featuresClockStyles = true,
        featuresOvertimeReminders = true,
        clockStyle = clockStyle,
        createdAt = 0L,
        updatedAt = 0L,
        deletedAt = null,
        syncStatus = SyncStatus.SYNCED,
        lastSyncError = null,
        lastSyncedAt = null,
    )

    @Test
    fun `toDomain parses weekendDays CSV correctly`() {
        assertEquals(listOf(5, 6), entity(weekendDays = "5,6").toDomain().weekendDays)
    }

    @Test
    fun `toDomain handles blank weekendDays as empty list`() {
        assertTrue(entity(weekendDays = "").toDomain().weekendDays.isEmpty())
    }

    @Test
    fun `toDomain skips invalid weekend day entries`() {
        assertEquals(listOf(5, 6), entity(weekendDays = "5,6,invalid").toDomain().weekendDays)
    }

    @Test
    fun `toDomain maps clockStyle enum`() {
        assertEquals(ClockStyle.AURORA, entity(clockStyle = "AURORA").toDomain().clockStyle)
        assertEquals(ClockStyle.CLASSIC, entity(clockStyle = "CLASSIC").toDomain().clockStyle)
    }

    @Test
    fun `toDomain falls back to CLASSIC for unknown clockStyle`() {
        assertEquals(ClockStyle.CLASSIC, entity(clockStyle = "NOT_A_REAL_STYLE").toDomain().clockStyle)
        assertEquals(ClockStyle.CLASSIC, entity(clockStyle = "").toDomain().clockStyle)
    }

    @Test
    fun `toDomain parses FELLOWSHIP clockStyle`() {
        assertEquals(ClockStyle.FELLOWSHIP, entity(clockStyle = "FELLOWSHIP").toDomain().clockStyle)
        assertEquals(ClockStyle.FELLOWSHIP, entity(clockStyle = "fellowship").toDomain().clockStyle)
    }

    @Test
    fun `toDomain preserves null hourlyRate`() {
        assertNull(entity(hourlyRate = null).toDomain().hourlyRate)
    }

    @Test
    fun `toEntity serialises weekendDays as CSV`() {
        val domain = UserSettings(
            id = "cfg-1",
            userId = "u1",
            weekendDays = listOf(5, 6),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        assertEquals("5,6", domain.toEntity().weekendDays)
    }

    @Test
    fun `toEntity serialises clockStyle as name string`() {
        val domain = UserSettings(
            id = "cfg-1",
            userId = "u1",
            clockStyle = ClockStyle.PRISM,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        assertEquals("PRISM", domain.toEntity().clockStyle)
    }

    @Test
    fun `currency round-trips through local entity`() {
        val domain = entity(currency = "EUR").toDomain()
        assertEquals(CurrencyCode.EUR, domain.currency)
        assertEquals("EUR", domain.toEntity().currency)
    }

    @Test
    fun `round-trip entity → domain → entity is stable`() {
        val original = entity(weekendDays = "0,5,6", clockStyle = "RETRO", hourlyRate = 75.5)
        val roundTripped = original.toDomain().toEntity(
            syncStatus = original.syncStatus,
            remoteId = original.remoteId,
        )
        assertEquals(original.weekendDays, roundTripped.weekendDays)
        assertEquals(original.clockStyle, roundTripped.clockStyle)
        assertEquals(original.hourlyRate, roundTripped.hourlyRate)
        assertEquals(original.dailyOvertimeThresholdMinutes, roundTripped.dailyOvertimeThresholdMinutes)
    }
}
