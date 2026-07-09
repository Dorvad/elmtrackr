package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.compensation.RegionPresets
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.CompensationRules
import com.elmtrackr.app.domain.model.PremiumProfile
import com.elmtrackr.app.domain.model.PremiumType
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.StackingPolicy
import com.elmtrackr.app.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * Regression tests for the Wave-1 money-correctness fixes: premium profiles
 * must affect saved/displayed totals (not just the edit preview), and the
 * Israeli engine must honor configured deductions.
 */
class Wave1PayRegressionTest {

    private val settings = UserSettings(
        id = "cfg1", userId = "u1",
        dailyOvertimeThresholdMinutes = 480,
        weeklyOvertimeThresholdMinutes = 2400,
        weekendDays = listOf(5, 6),
        hourlyRate = 100.0,
    )

    private val premium = PremiumProfile(
        id = "prem-200",
        userId = "u1",
        name = "Night double",
        multiplier = 2.0,
        premiumType = PremiumType.HIGHEST_ONLY,
        isDefault = false,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    // Monday 08:00-12:00 UTC — 4h, no overtime, no weekend.
    private fun premiumShift() = Shift(
        id = "s1", userId = "u1",
        startTime = Instant.parse("2024-06-03T08:00:00Z"),
        endTime = Instant.parse("2024-06-03T12:00:00Z"),
        isSpecialDay = true,
        premiumProfileId = "prem-200",
    )

    private fun ilProfile(rules: CompensationRules = RegionPresets.forRegion(RegionCode.IL).rules) =
        CompensationProfile(
            id = "p-il", userId = "u1", name = "IL",
            regionCode = RegionCode.IL,
            currencyCode = "ILS", timezone = "UTC",
            baseHourlyRate = 100.0,
            rules = rules,
            stackingPolicy = StackingPolicy.HIGHEST_ONLY,
            isDefault = true,
        )

    @Test
    fun `sumMonthlyPay applies the premium multiplier when premium profiles are provided`() {
        val summary = PayrollCalculator.sumMonthlyPay(
            listOf(premiumShift()), settings, premiumProfiles = listOf(premium),
        )
        // 4h at 100/h x 2.0 premium.
        assertEquals(800.0, summary.totalGross, 0.001)
    }

    @Test
    fun `calculateShiftPayInContext applies the premium multiplier`() {
        val shift = premiumShift()
        val pay = PayrollCalculator.calculateShiftPayInContext(
            shift, listOf(shift), settings, premiumProfiles = listOf(premium),
        )
        assertEquals(800.0, pay?.totalGross ?: 0.0, 0.001)
    }

    @Test
    fun `premium shift without the profile list falls back to holiday rate, not premium`() {
        // Documents the fallback that made this a bug: callers omitting the
        // premium list price the shift at the manual-holiday multiplier.
        val shift = premiumShift()
        val pay = PayrollCalculator.calculateShiftPayInContext(shift, listOf(shift), settings)
        val holidayMultiplier = 1.5
        assertEquals(4 * 100.0 * holidayMultiplier, pay?.totalGross ?: 0.0, 0.001)
    }

    @Test
    fun `israeli engine honors percentage deductions`() {
        val rules = RegionPresets.forRegion(RegionCode.IL).rules.copy(
            deductionsEnabled = true,
            deductionsMode = "percentage",
            deductionsPercentage = 10.0,
        )
        val shift = Shift(
            id = "s2", userId = "u1",
            startTime = Instant.parse("2024-06-03T08:00:00Z"),
            endTime = Instant.parse("2024-06-03T12:00:00Z"),
            compensationProfileId = "p-il",
        )
        val pay = PayrollCalculator.calculateShiftPayInContext(
            shift, listOf(shift), settings, listOf(ilProfile(rules)),
        )
        val total = pay?.totalGross ?: 0.0
        assertEquals(400.0, total, 0.001)
        assertEquals(40.0, pay?.deductionsGross ?: 0.0, 0.001)
        assertEquals(360.0, pay?.netGross ?: 0.0, 0.001)
    }
}
