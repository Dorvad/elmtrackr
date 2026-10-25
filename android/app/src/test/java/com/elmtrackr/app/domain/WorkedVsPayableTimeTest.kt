package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.compensation.RegionPresets
import com.elmtrackr.app.domain.compensation.ShiftClassifier
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.CompensationRules
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.RoundingRules
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Worked time and paid time are different quantities, and the dashboard, the
 * clock-out recap and Reports now show both. These tests pin what each one means
 * and that the explanation adds up to the figure the engines price.
 */
class WorkedVsPayableTimeTest {

    private fun shift(
        start: String,
        end: String?,
        breakMinutes: Int = 0,
        id: String = "s1",
        profileId: String? = null,
    ) = Shift(
        id = id,
        userId = "u1",
        startTime = Instant.parse(start),
        endTime = end?.let { Instant.parse(it) },
        breakMinutes = breakMinutes,
        compensationProfileId = profileId,
    )

    private val roundToQuarter = RoundingRules(enabled = true, incrementMinutes = 15, direction = "nearest")

    @Test
    fun `plain rules pay exactly the time worked`() {
        val time = PayrollCalculator.payableTime(
            shift("2026-10-05T09:00:00Z", "2026-10-05T17:00:00Z", breakMinutes = 30),
            CompensationRules(),
        )!!

        assertEquals(480, time.elapsedMinutes)
        assertEquals(450, time.workedMinutes)
        assertEquals(30, time.unpaidBreakMinutes)
        assertEquals(450, time.payableMinutes)
        assertFalse(time.payableDiffersFromWorked)
    }

    @Test
    fun `rounding changes paid time but never worked time`() {
        // 7h 42m worked — the example in the Wave 2 brief.
        val time = PayrollCalculator.payableTime(
            shift("2026-10-05T09:00:00Z", "2026-10-05T16:42:00Z"),
            CompensationRules(rounding = roundToQuarter),
        )!!

        assertEquals(462, time.workedMinutes)
        assertEquals(465, time.payableMinutes)
        assertEquals(3, time.roundingMinutes)
        assertTrue(time.payableDiffersFromWorked)
    }

    @Test
    fun `a minimum shift tops up paid time and records by how much`() {
        val time = PayrollCalculator.payableTime(
            shift("2026-10-05T09:00:00Z", "2026-10-05T10:10:00Z"),
            CompensationRules(minimumShiftMinutes = 240),
        )!!

        assertEquals(70, time.workedMinutes)
        assertEquals(240, time.payableMinutes)
        assertEquals(170, time.minimumTopUpMinutes)
    }

    @Test
    fun `paid breaks are paid but not counted as worked`() {
        val time = PayrollCalculator.payableTime(
            shift("2026-10-05T09:00:00Z", "2026-10-05T17:00:00Z", breakMinutes = 30),
            CompensationRules(paidBreaks = true),
        )!!

        assertEquals(450, time.workedMinutes)
        assertEquals(30, time.paidBreakMinutes)
        assertEquals(0, time.unpaidBreakMinutes)
        assertEquals(480, time.payableMinutes)
    }

    @Test
    fun `an automatically deducted break is unpaid and not worked`() {
        val time = PayrollCalculator.payableTime(
            shift("2026-10-05T09:00:00Z", "2026-10-05T17:00:00Z"),
            CompensationRules(autoDeductBreakMinutes = 30),
        )!!

        assertEquals(30, time.autoBreakMinutes)
        assertEquals(30, time.unpaidBreakMinutes)
        assertEquals(450, time.workedMinutes)
        assertEquals(450, time.payableMinutes)
    }

    @Test
    fun `the explanation always adds up to the priced minutes`() {
        val ruleSets = listOf(
            CompensationRules(),
            CompensationRules(paidBreaks = true),
            CompensationRules(autoDeductBreakMinutes = 30),
            CompensationRules(rounding = roundToQuarter),
            CompensationRules(rounding = roundToQuarter.copy(direction = "up"), minimumShiftMinutes = 120),
            CompensationRules(rounding = roundToQuarter.copy(direction = "down"), minimumShiftMinutes = 60),
            CompensationRules(paidBreaks = true, rounding = roundToQuarter, minimumShiftMinutes = 180),
        )
        val shifts = listOf(
            shift("2026-10-05T09:00:00Z", "2026-10-05T09:07:00Z"),
            shift("2026-10-05T09:00:00Z", "2026-10-05T09:30:00Z", breakMinutes = 30),
            shift("2026-10-05T09:00:00Z", "2026-10-05T16:53:00Z", breakMinutes = 20),
            shift("2026-10-05T22:00:00Z", "2026-10-06T07:11:00Z", breakMinutes = 45),
        )
        for (rules in ruleSets) for (s in shifts) {
            val time = PayrollCalculator.payableTime(s, rules)!!
            assertEquals(
                "payable must equal worked + paid break + rounding + minimum for $rules / ${s.endTime}",
                time.payableMinutes,
                time.workedMinutes + time.paidBreakMinutes + time.roundingMinutes + time.minimumTopUpMinutes,
            )
            // The figure the engines price is unchanged by the refactor.
            assertEquals(PayrollCalculator.payableNetMinutes(s, rules), time.payableMinutes)
        }
    }

    @Test
    fun `an active shift has no paid time yet`() {
        assertNull(PayrollCalculator.payableTime(shift("2026-10-05T09:00:00Z", null), CompensationRules()))
    }

    @Test
    fun `the monthly report keeps worked and paid minutes apart`() {
        val profile = CompensationProfile(
            id = "p1",
            userId = "u1",
            name = "Cafe",
            regionCode = RegionCode.US,
            currencyCode = "USD",
            timezone = "UTC",
            baseHourlyRate = 20.0,
            rules = RegionPresets.forRegion(RegionCode.US).rules.copy(rounding = roundToQuarter),
            stackingPolicy = RegionPresets.forRegion(RegionCode.US).stackingPolicy,
            isDefault = true,
        )
        val settings = UserSettings(id = "cfg", userId = "u1", defaultCompensationProfileId = "p1")
        val shifts = listOf(
            shift("2026-10-05T09:00:00Z", "2026-10-05T16:42:00Z", id = "a", profileId = "p1"), // 462 → 465
            shift("2026-10-06T09:00:00Z", "2026-10-06T13:07:00Z", id = "b", profileId = "p1"), // 247 → 240
        )

        val report = MonthlyReportBuilder.buildMonthlyReport(2026, 10, shifts, settings, listOf(profile))

        assertEquals(709, report.workedMinutes)
        assertEquals(705, report.totalMinutes)
        assertTrue(report.payableDiffersFromWorked)
        assertEquals(
            report.totalMinutes,
            report.regularMinutes + report.overtimeMinutes + report.weekendMinutes,
        )
        assertEquals(-4, report.roundingMinutes)
        assertEquals(
            report.totalMinutes,
            report.workedMinutes + report.paidBreakMinutes + report.roundingMinutes + report.minimumTopUpMinutes,
        )
        assertEquals(setOf(com.elmtrackr.app.domain.model.PaidTimeReason.ROUNDING), report.paidTimeReasons)
    }

    @Test
    fun `reasons name only the rules that changed paid time`() {
        val time = PayrollCalculator.payableTime(
            shift("2026-10-05T09:00:00Z", "2026-10-05T10:00:00Z", breakMinutes = 10),
            CompensationRules(paidBreaks = true, minimumShiftMinutes = 120),
        )!!
        assertEquals(
            setOf(
                com.elmtrackr.app.domain.model.PaidTimeReason.MINIMUM_SHIFT,
                com.elmtrackr.app.domain.model.PaidTimeReason.PAID_BREAKS,
            ),
            time.reasons,
        )
        assertTrue(
            PayrollCalculator.payableTime(
                shift("2026-10-05T09:00:00Z", "2026-10-05T17:00:00Z", breakMinutes = 30),
                CompensationRules(),
            )!!.reasons.isEmpty(),
        )
    }

    @Test
    fun `classification reports worked minutes, not the payable figure`() {
        val profile = CompensationProfile(
            id = "p1",
            userId = "u1",
            name = "Cafe",
            regionCode = RegionCode.US,
            currencyCode = "USD",
            timezone = "UTC",
            baseHourlyRate = 20.0,
            rules = RegionPresets.forRegion(RegionCode.US).rules.copy(minimumShiftMinutes = 240),
            stackingPolicy = RegionPresets.forRegion(RegionCode.US).stackingPolicy,
            isDefault = true,
        )
        val settings = UserSettings(id = "cfg", userId = "u1", defaultCompensationProfileId = "p1")
        val short = shift("2026-10-05T09:00:00Z", "2026-10-05T10:00:00Z", profileId = "p1")

        val classification = ShiftClassifier.classify(short, listOf(short), settings, listOf(profile))!!

        assertEquals(60, classification.workedMinutes)
        assertEquals(240, classification.payableMinutes)
    }

    @Test
    fun `the Israeli engine classifies worked minutes the same way`() {
        val profile = CompensationProfile(
            id = "p1",
            userId = "u1",
            name = "Office",
            regionCode = RegionCode.IL,
            currencyCode = "ILS",
            timezone = "Asia/Jerusalem",
            baseHourlyRate = 50.0,
            rules = RegionPresets.forRegion(RegionCode.IL).rules.copy(rounding = roundToQuarter),
            stackingPolicy = RegionPresets.forRegion(RegionCode.IL).stackingPolicy,
            isDefault = true,
        )
        val settings = UserSettings(id = "cfg", userId = "u1", defaultCompensationProfileId = "p1")
        // A Monday in Israel; 7h 53m worked rounds to 8h 00m paid.
        val s = shift("2026-10-05T06:00:00Z", "2026-10-05T13:53:00Z", profileId = "p1")

        val month = ShiftClassifier.classifyMonth(listOf(s), listOf(s), settings, listOf(profile))

        assertEquals(473, month.getValue("s1").workedMinutes)
        assertEquals(480, month.getValue("s1").payableMinutes)
    }
}
