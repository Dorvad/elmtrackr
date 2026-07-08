package com.elmtrackr.app.domain.compensation

import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.CompensationRules
import com.elmtrackr.app.domain.model.CompensationSnapshot
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.StackingPolicy
import com.elmtrackr.app.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant

class ShiftCompensationHelperTest {

    private val settings = UserSettings(
        id = "s1",
        userId = "u1",
        timezone = "UTC",
        defaultCompensationProfileId = "p1",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun profile(rules: CompensationRules) = CompensationProfile(
        id = "p1",
        userId = "u1",
        name = "Main job",
        regionCode = RegionCode.IL,
        currencyCode = "ILS",
        timezone = "UTC",
        baseHourlyRate = 50.0,
        rules = rules,
        stackingPolicy = StackingPolicy.HIGHEST_ONLY,
        isDefault = true,
    )

    private fun snapshotWith(rules: CompensationRules) = CompensationSnapshot(
        profileId = "p1",
        profileName = "Main job",
        regionCode = RegionCode.IL,
        currencyCode = "ILS",
        timezone = "UTC",
        baseHourlyRate = 50.0,
        rules = rules,
        stackingPolicy = StackingPolicy.HIGHEST_ONLY,
        calculatedAt = Instant.EPOCH,
    )

    // 2024-06-07 is a Friday (JS day 5); 0=Sun … 6=Sat.
    private fun fridayShift(snapshot: CompensationSnapshot?) = Shift(
        id = "sh1",
        userId = "u1",
        startTime = Instant.parse("2024-06-07T09:00:00Z"),
        endTime = Instant.parse("2024-06-07T17:00:00Z"),
        compensationProfileId = "p1",
        compensationSnapshot = snapshot,
    )

    @Test
    fun `rebuilding a snapshot picks up the current profile rules`() {
        val staleRules = CompensationRules(weekendEnabled = true, weekendDays = listOf(5, 6))
        val currentRules = CompensationRules(weekendEnabled = true, weekendDays = listOf(6))
        val shift = fridayShift(snapshotWith(staleRules))

        val rebuilt = ShiftCompensationHelper.buildClockOutSnapshot(
            shift, settings, listOf(profile(currentRules)),
        )

        assertEquals(listOf(6), rebuilt.rules.weekendDays)
    }

    @Test
    fun `friday shift loses weekend premium after weekend days shrink to saturday`() {
        val staleRules = CompensationRules(weekendEnabled = true, weekendDays = listOf(5, 6))
        val currentRules = CompensationRules(weekendEnabled = true, weekendDays = listOf(6))
        val shift = fridayShift(snapshotWith(staleRules))

        val rebuilt = ShiftCompensationHelper.buildClockOutSnapshot(
            shift, settings, listOf(profile(currentRules)),
        )
        val saved = shift.copy(compensationSnapshot = rebuilt)

        assertFalse(
            CompensationResolver.isWeekendShift(saved, settings, listOf(profile(currentRules))),
        )
    }
}
