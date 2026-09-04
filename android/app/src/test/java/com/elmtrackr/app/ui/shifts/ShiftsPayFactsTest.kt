package com.elmtrackr.app.ui.shifts

import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

/**
 * The Shifts list used to derive every row's money and both badges inside a
 * composition `remember`, on the main thread. [buildShiftsPayFacts] now does that work
 * in `ShiftsViewModel` on the computation dispatcher and hands the result down.
 *
 * These tests are the guard on that move: the precomputed list and the in-line list
 * must be the same list. If a future change makes one path smarter than the other, a
 * user sees one figure on screen and a different one in Reports — the divergence class
 * Wave B existed to close.
 */
class ShiftsPayFactsTest {

    private val zone = ZoneId.of("Asia/Jerusalem")
    private val month = YearMonth.of(2026, 7)

    private val settings = UserSettings(
        id = "s1",
        userId = "u1",
        timezone = "Asia/Jerusalem",
        hourlyRate = 60.0,
        currency = CurrencyCode.ILS,
        currencyCode = "ILS",
        regionCode = RegionCode.IL,
        weekendDays = listOf(5, 6),
        dailyOvertimeThresholdMinutes = 480,
        weeklyOvertimeThresholdMinutes = 2520,
    )

    private fun shift(id: String, start: String, end: String) = Shift(
        id = id,
        userId = "u1",
        startTime = Instant.parse(start),
        endTime = Instant.parse(end),
    )

    /**
     * A full week plus a long day, so the fixture exercises the daily ladder, the
     * weekly ladder, a Friday split by the 17:00 rest boundary and a Saturday.
     * Times are UTC; Jerusalem is UTC+3 in July.
     */
    private val shifts = listOf(
        // 08:00-16:00 local: 480 minutes, under the IL 516-minute (8h36) standard.
        shift("mon", "2026-07-06T05:00:00Z", "2026-07-06T13:00:00Z"),
        shift("tue", "2026-07-07T05:00:00Z", "2026-07-07T14:00:00Z"),
        shift("wed", "2026-07-08T05:00:00Z", "2026-07-08T14:00:00Z"),
        shift("thu", "2026-07-09T05:00:00Z", "2026-07-09T16:00:00Z"),
        shift("fri", "2026-07-10T05:00:00Z", "2026-07-10T14:06:00Z"),
        shift("sat", "2026-07-11T06:00:00Z", "2026-07-11T14:00:00Z"),
    )

    private fun items(payFacts: ShiftsPayFacts?) = buildShiftsLazyListItems(
        shifts = shifts,
        activeShift = null,
        month = month,
        settings = settings,
        profiles = emptyList(),
        zone = zone,
        locale = Locale.US,
        payContextShifts = shifts,
        payFacts = payFacts,
    )

    private fun facts() = buildShiftsPayFacts(
        shifts = shifts,
        activeShift = null,
        settings = settings,
        profiles = emptyList(),
        premiumProfiles = emptyList(),
        zone = zone,
        payContextShifts = shifts,
    )

    @Test
    fun `the precomputed list is identical to the in-line list`() {
        assertEquals(items(null), items(facts()))
    }

    @Test
    fun `every week card total survives the move`() {
        val inline = items(null).filterIsInstance<ShiftsLazyListItem.SectionHeader>()
        val precomputed = items(facts()).filterIsInstance<ShiftsLazyListItem.SectionHeader>()
        assertTrue(inline.isNotEmpty())
        assertEquals(inline.map { it.section.weekStart }, precomputed.map { it.section.weekStart })
        assertEquals(inline.map { it.section.pay }, precomputed.map { it.section.pay })
        assertEquals(
            inline.map { it.section.totalMinutes },
            precomputed.map { it.section.totalMinutes },
        )
    }

    @Test
    fun `rows carry real money, so the equality above is not comparing two sets of nulls`() {
        val rows = items(facts()).filterIsInstance<ShiftsLazyListItem.ShiftEntry>()
        assertEquals(shifts.size, rows.size)
        rows.forEach { assertNotNull("row ${it.shift.id} has no pay", it.display?.payGross) }
        assertTrue(rows.all { (it.display?.payGross ?: 0.0) > 0.0 })
    }

    @Test
    fun `a shift past the daily standard is badged as overtime and one under it is not`() {
        val byId = items(facts()).filterIsInstance<ShiftsLazyListItem.ShiftEntry>()
            .associateBy { it.shift.id }
        // Thursday runs 660 minutes against the IL standard of 516; Monday runs 480.
        assertTrue(byId.getValue("thu").display!!.hasOt)
        assertFalse(byId.getValue("mon").display!!.hasOt)
    }

    @Test
    fun `the Saturday is badged weekend, not overtime`() {
        val sat = items(facts()).filterIsInstance<ShiftsLazyListItem.ShiftEntry>()
            .first { it.shift.id == "sat" }.display!!
        assertTrue(sat.weekend)
        assertFalse(sat.hasOt)
    }

    @Test
    fun `facts are keyed by shift id, and an unknown id falls back to computing in line`() {
        // buildShiftRowDisplay must never render a row against another shift's money.
        // A missing key means "not precomputed", not "zero".
        val facts = facts()
        val stranger = shift("stranger", "2026-07-13T05:00:00Z", "2026-07-13T16:00:00Z")
        val row = buildShiftRowDisplay(
            stranger, settings, emptyList(), shifts + stranger,
            zone = zone,
            locale = Locale.US,
            facts = facts.perShift[stranger.id],
        )
        assertNotNull(row.payGross)
        assertTrue(row.hasOt)
    }

    @Test
    fun `no settings means no facts and no crash`() {
        val none = buildShiftsPayFacts(
            shifts = shifts,
            activeShift = null,
            settings = null,
            profiles = emptyList(),
            premiumProfiles = emptyList(),
            zone = zone,
        )
        assertEquals(ShiftsPayFacts.EMPTY, none)
    }
}
