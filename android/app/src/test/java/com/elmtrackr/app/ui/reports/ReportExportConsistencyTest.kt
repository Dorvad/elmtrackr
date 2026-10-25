package com.elmtrackr.app.ui.reports

import com.elmtrackr.app.domain.HoursFormatter
import com.elmtrackr.app.domain.MonthlyReportBuilder
import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.compensation.RegionPresets
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.OvertimeTier
import com.elmtrackr.app.domain.model.PayCategory
import com.elmtrackr.app.domain.model.PremiumProfile
import com.elmtrackr.app.domain.model.PremiumType
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.StackingPolicy
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.time.WorkTimezone
import com.elmtrackr.app.fake.FakeCompensationProfilesRepository
import com.elmtrackr.app.fake.FakeCurrentUserProvider
import com.elmtrackr.app.fake.FakePremiumProfilesRepository
import com.elmtrackr.app.fake.FakeProjectsRepository
import com.elmtrackr.app.fake.FakeRefundReceiptStorage
import com.elmtrackr.app.fake.FakeRefundsRepository
import com.elmtrackr.app.fake.FakeReportsRepository
import com.elmtrackr.app.fake.FakeReviewPromptRecorder
import com.elmtrackr.app.fake.FakeSettingsRepository
import com.elmtrackr.app.fake.FakeShiftsRepository
import com.elmtrackr.app.fake.FakeTasksRepository
import com.elmtrackr.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * The CSV must classify every shift exactly as the screen does.
 *
 * `buildCsvContent` passed `emptyList()` for premium profiles, so a shift worked
 * under a premium was special-rate time in the Reports rows and the PDF but
 * ordinary time in the file sent to payroll. The fixtures below cover the rules
 * that move minutes between columns — overtime tiers, weekly rest, a premium, a
 * shift across midnight, a pay week across the 1st, two profiles with different
 * weekend and overtime settings — and assert the export against the same calls
 * the screen makes, rather than against literals that could drift together.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportExportConsistencyTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settingsRepo = FakeSettingsRepository()

    private fun buildVm(): ReportsViewModel {
        settingsRepo.setSettings(settings)
        return ReportsViewModel(
            FakeReportsRepository(),
            FakeShiftsRepository(),
            FakeTasksRepository(),
            settingsRepo,
            FakeCurrentUserProvider(),
            FakeRefundsRepository(),
            FakeCompensationProfilesRepository(),
            FakePremiumProfilesRepository(),
            FakeRefundReceiptStorage(),
            FakeProjectsRepository(),
            FakeReviewPromptRecorder(),
            computationDispatcher = mainDispatcherRule.dispatcher,
        )
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    /** The Israeli preset: 125% then 150% daily overtime, weekly rest from Friday 17:00. */
    private val israeli = CompensationProfile(
        id = "p-il",
        userId = "u1",
        name = "Main job",
        regionCode = RegionCode.IL,
        currencyCode = "ILS",
        timezone = "Asia/Jerusalem",
        baseHourlyRate = 60.0,
        rules = RegionPresets.forRegion(RegionCode.IL).rules,
        stackingPolicy = RegionPresets.forRegion(RegionCode.IL).stackingPolicy,
        isDefault = true,
    )

    /** A second job with its own week: Saturday-only weekend and overtime after 7 hours. */
    private val custom = CompensationProfile(
        id = "p-custom",
        userId = "u1",
        name = "Evening job",
        regionCode = RegionCode.CUSTOM,
        currencyCode = "ILS",
        timezone = "Asia/Jerusalem",
        baseHourlyRate = 45.0,
        rules = RegionPresets.forRegion(RegionCode.CUSTOM).rules.copy(
            dailyStandardMinutes = 420,
            dailyOvertimeTiers = listOf(OvertimeTier(420, 1.25), OvertimeTier(540, 1.5)),
            weekendDays = listOf(6),
            weekendEnabled = true,
            weekendMultiplier = 1.5,
        ),
        stackingPolicy = StackingPolicy.HIGHEST_ONLY,
    )

    private val holiday = PremiumProfile(
        id = "prem-holiday",
        userId = "u1",
        name = "Holiday",
        multiplier = 2.0,
        premiumType = PremiumType.HIGHEST_ONLY,
    )

    private val settings = UserSettings(
        id = "cfg1",
        userId = "u1",
        timezone = "Asia/Jerusalem",
        regionCode = RegionCode.IL,
        defaultCompensationProfileId = "p-il",
        hourlyRate = 60.0,
    )

    private val profiles = listOf(israeli, custom)
    private val premiumProfiles = listOf(holiday)

    private fun shift(id: String, start: String, end: String, profile: String = "p-il") = Shift(
        id = id,
        userId = "u1",
        // Jerusalem is UTC+2 in January; the comments below give local time.
        startTime = Instant.parse(start),
        endTime = Instant.parse(end),
        breakMinutes = 0,
        compensationProfileId = profile,
    )

    /** Sun 31 Dec 2023, 08:00–18:00: last month, same pay week as 1–6 January. */
    private val priorMonthLongDay = shift("dec31", "2023-12-31T06:00:00Z", "2023-12-31T16:00:00Z")

    /** Mon 1 Jan, 08:00–16:00: an ordinary day. */
    private val ordinary = shift("jan01", "2024-01-01T06:00:00Z", "2024-01-01T14:00:00Z")

    /** Tue 2 Jan, 07:00–19:00: twelve hours, through both overtime tiers. */
    private val longDay = shift("jan02", "2024-01-02T05:00:00Z", "2024-01-02T17:00:00Z")

    /** Wed 3 Jan, 08:00–14:00 under the holiday premium, after ordinary days that week. */
    private val premiumDay = shift("jan03", "2024-01-03T06:00:00Z", "2024-01-03T12:00:00Z")
        .copy(isSpecialDay = true, premiumProfileId = "prem-holiday")

    /** Fri 5 Jan 20:00 → Sat 6 Jan 02:00: weekly rest, and across midnight. */
    private val fridayNight = shift("jan05", "2024-01-05T18:00:00Z", "2024-01-06T00:00:00Z")

    /** Tue 9 Jan 22:00 → Wed 10 Jan 06:00: a weekday night across midnight. */
    private val weekdayNight = shift("jan09", "2024-01-09T20:00:00Z", "2024-01-10T04:00:00Z")

    /** Thu 11 Jan, 09:00–19:00 at the second job: past its 7-hour standard. */
    private val customLongDay = shift("jan11", "2024-01-11T07:00:00Z", "2024-01-11T17:00:00Z", "p-custom")

    /** Sat 13 Jan, 09:00–13:00 at the second job: its own weekend. */
    private val customSaturday = shift("jan13", "2024-01-13T07:00:00Z", "2024-01-13T11:00:00Z", "p-custom")

    private val month = listOf(ordinary, longDay, premiumDay, fridayNight, weekdayNight, customLongDay, customSaturday)
    private val payContext = month + priorMonthLongDay

    private fun export(): List<String> = buildVm().buildCsvContent(
        shifts = month,
        settings = settings,
        profiles = profiles,
        contextShifts = payContext,
        year = 2024,
        month = 1,
        premiumProfiles = premiumProfiles,
    ).lines()

    /** The data row for [shift], found by its local date and start time. */
    private fun List<String>.rowFor(shift: Shift): List<String> {
        val zone = WorkTimezone.zoneFor(settings)
        val date = WorkTimezone.shiftLocalDate(shift, zone).toString()
        val start = shift.startTime.atZone(zone).toLocalTime().toString()
        return first { it.startsWith("$date,") && it.contains(" $start") }.split(",")
    }

    /** What the Reports screen's row computes for [shift] (see `ShiftReportRow`). */
    private fun screenBreakdown(shift: Shift) = MonthlyReportBuilder.buildShiftBreakdown(
        shift, settings, profiles, premiumProfiles, payContext,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `every exported row matches the on-screen classification of the same shift`() {
        val rows = export()
        month.forEach { shift ->
            val cells = rows.rowFor(shift)
            val screen = screenBreakdown(shift)
            val label = "${shift.id}: $cells vs $screen"
            assertEquals(label, HoursFormatter.csv(screen.totalMinutes), cells[4])
            assertEquals(label, HoursFormatter.csv(screen.regularMinutes), cells[5])
            assertEquals(label, HoursFormatter.csv(screen.overtimeMinutes), cells[6])
            assertEquals(label, HoursFormatter.csv(screen.weekendMinutes), cells[7])
        }
    }

    @Test
    fun `a premium shift is special time in the export, as on screen`() {
        val cells = export().rowFor(premiumDay)

        // 6 hours, all of them at the holiday premium: none is ordinary time.
        assertEquals("6.00", cells[4])
        assertEquals("0.00", cells[5])
        assertEquals("6.00", cells[7])
    }

    @Test
    fun `the special-time hours are the minutes the pay engine prices at the premium`() {
        val pay = PayrollCalculator.calculateShiftPayInContext(
            premiumDay, payContext, settings, profiles, premiumProfiles,
        )!!
        val premiumMinutes = pay.brackets
            .filter { it.category == PayCategory.PREMIUM }
            .sumOf { it.minutes }

        assertEquals(screenBreakdown(premiumDay).weekendMinutes, premiumMinutes)
    }

    @Test
    fun `the total row is the on-screen monthly report`() {
        val total = export().first { it.startsWith("TOTAL") }.split(",")
        val screen = MonthlyReportBuilder.buildMonthlyReport(
            2024, 1, month, settings, profiles, premiumProfiles, payContext,
        )

        assertEquals(HoursFormatter.csv(screen.totalMinutes), total[4])
        assertEquals(HoursFormatter.csv(screen.regularMinutes), total[5])
        assertEquals(HoursFormatter.csv(screen.overtimeMinutes), total[6])
        assertEquals(HoursFormatter.csv(screen.weekendMinutes), total[7])
        assertEquals("${month.size} shifts", total[9])
    }

    @Test
    fun `rows add up to the total within the documented rounding`() {
        val rows = export()
        val total = rows.first { it.startsWith("TOTAL") }.split(",")
        // Each cell rounds its own minutes to 0.01 h; the total rounds the exact sum.
        val tolerance = 0.005 * month.size
        listOf(4, 5, 6, 7).forEach { column ->
            val summed = month.sumOf { rows.rowFor(it)[column].toDouble() }
            assertEquals("column $column", total[column].toDouble(), summed, tolerance)
        }
    }

    @Test
    fun `the week across the 1st carries last month's hours into January's overtime`() {
        val withContext = screenBreakdown(longDay)
        val withoutContext = MonthlyReportBuilder.buildShiftBreakdown(
            longDay, settings, profiles, premiumProfiles, month,
        )
        // Sanity check on the fixture: the prior-month day matters to this week…
        assertTrue(
            "fixture: the 31 Dec shift should change 2 Jan's split",
            withContext != withoutContext || withContext.overtimeMinutes > 0,
        )
        // …and the export uses it, because it is handed the same window.
        assertEquals(HoursFormatter.csv(withContext.overtimeMinutes), export().rowFor(longDay)[6])
    }

    @Test
    fun `overnight shifts are dated by their start in the work zone and marked overnight`() {
        val rows = export()
        val friday = rows.rowFor(fridayNight)
        val tuesday = rows.rowFor(weekdayNight)

        assertEquals("2024-01-05", friday[0])
        assertEquals("2024-01-05 20:00", friday[1])
        assertEquals("2024-01-06 02:00", friday[2])
        assertEquals("Yes", friday[8])
        assertEquals("2024-01-09", tuesday[0])
        assertEquals("Yes", tuesday[8])
    }

    @Test
    fun `each profile's own weekend and overtime rules decide its rows`() {
        val rows = export()

        // 10 hours against the second job's 7-hour standard.
        assertEquals("3.00", rows.rowFor(customLongDay)[6])
        // Saturday is that job's weekend.
        assertEquals("4.00", rows.rowFor(customSaturday)[7])
        // The ordinary Monday at the first job stays ordinary.
        assertEquals("8.00", rows.rowFor(ordinary)[5])
    }
}
