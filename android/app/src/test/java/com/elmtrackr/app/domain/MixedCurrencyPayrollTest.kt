package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.compensation.RegionPresets
import com.elmtrackr.app.domain.leave.LeaveEarningsBase
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.PayByCurrency
import com.elmtrackr.app.domain.model.PremiumProfile
import com.elmtrackr.app.domain.model.PremiumType
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.StackingPolicy
import com.elmtrackr.app.domain.model.Task
import com.elmtrackr.app.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.util.Locale

/**
 * Two jobs paid in two currencies must never become one number.
 *
 * Job A pays 4,000 ILS and Job B 1,000 USD. There is no exchange rate in the app, so
 * "5,000" — under either sign — is not an amount of anything, and every total a person
 * sees or exports has to come from the per-currency figures. These tests pin the domain
 * side of that: the summary, the aggregates built beside it, and the formatter every
 * surface renders through.
 */
class MixedCurrencyPayrollTest {

    private fun profile(id: String, currency: String, rate: Double?, isDefault: Boolean = false) = CompensationProfile(
        id = id,
        userId = "u1",
        name = id,
        regionCode = RegionCode.CUSTOM,
        currencyCode = currency,
        timezone = "UTC",
        baseHourlyRate = rate,
        // Weekend off so the fixtures below are plain hours at the base rate.
        rules = RegionPresets.forRegion(RegionCode.CUSTOM).rules.copy(weekendEnabled = false),
        stackingPolicy = StackingPolicy.HIGHEST_ONLY,
        isDefault = isDefault,
    )

    /** 8 hours at 500/h = 4,000 ILS per shift. */
    private val jobA = profile("job-a", "ILS", 500.0, isDefault = true)

    /** 8 hours at 125/h = 1,000 USD per shift. */
    private val jobB = profile("job-b", "USD", 125.0)

    /** A third job, also in shekels: 4 hours at 50/h = 200 ILS. */
    private val jobC = profile("job-c", "ILS", 50.0)

    private val settings = UserSettings(
        id = "cfg",
        userId = "u1",
        timezone = "UTC",
        defaultCompensationProfileId = jobA.id,
        currencyCode = "ILS",
    )

    private fun shift(id: String, day: Int, hours: Int, profile: CompensationProfile) = Shift(
        id = id,
        userId = "u1",
        // Monday 8 Jan 2024 onwards, 09:00 UTC.
        startTime = Instant.parse("2024-01-%02dT09:00:00Z".format(day)),
        endTime = Instant.parse("2024-01-%02dT%02d:00:00Z".format(day, 9 + hours)),
        breakMinutes = 0,
        compensationProfileId = profile.id,
    )

    private val shiftA = shift("a", 8, 8, jobA)
    private val shiftB = shift("b", 9, 8, jobB)
    private val shiftC = shift("c", 10, 4, jobC)

    private fun sum(vararg shifts: Shift, profiles: List<CompensationProfile> = listOf(jobA, jobB, jobC)) =
        PayrollCalculator.sumMonthlyPay(shifts.toList(), settings, profiles)

    // ── The summary ───────────────────────────────────────────────────────────

    @Test
    fun `a single-currency month has one entry carrying exactly the summary's figures`() {
        val summary = sum(shiftA, shiftC)
        val only = summary.byCurrency.single()

        assertEquals("ILS", summary.currencyCode)
        assertEquals("ILS", only.currencyCode)
        assertEquals(summary.totalGross, only.totalGross, 0.0)
        assertEquals(summary.regularGross, only.regularGross, 0.0)
        assertEquals(summary.overtimeGross, only.overtimeGross, 0.0)
        assertEquals(summary.specialGross, only.specialGross, 0.0)
        assertEquals(summary.deductionsGross, only.deductionsGross, 0.0)
        assertEquals(summary.netGross, only.netGross, 0.0)
        assertEquals(listOf(only), summary.paidByCurrency)
        assertEquals(listOf("ILS"), summary.grossPay.currencies)
        assertEquals(4200.0, summary.grossPay["ILS"], 0.001)
    }

    @Test
    fun `two jobs in two currencies give two totals and no combined one`() {
        val summary = sum(shiftA, shiftB)

        assertNull(summary.currencyCode)
        assertEquals(listOf("ILS", "USD"), summary.paidByCurrency.map { it.currencyCode })
        assertEquals(4000.0, summary.paidByCurrency[0].totalGross, 0.001)
        assertEquals(1000.0, summary.paidByCurrency[1].totalGross, 0.001)
        assertTrue(summary.grossPay.isMixed)
        assertNull("no single figure exists", summary.grossPay.singleOrNull())
        summary.byCurrency.forEach { assertFalse(it.totalGross == 5000.0) }
    }

    @Test
    fun `three jobs in two currencies add only within a currency`() {
        val summary = sum(shiftA, shiftB, shiftC)

        assertEquals(4200.0, summary.grossPay["ILS"], 0.001)
        assertEquals(1000.0, summary.grossPay["USD"], 0.001)
        assertEquals(2, summary.byCurrency.size)
        // The per-currency regular split adds up inside each currency.
        summary.byCurrency.forEach { totals ->
            assertEquals(
                totals.totalGross,
                totals.regularGross + totals.overtimeGross + totals.specialGross,
                0.001,
            )
        }
    }

    @Test
    fun `an empty month has no currency and nothing to show`() {
        val summary = sum()

        assertTrue(summary.byCurrency.isEmpty())
        assertTrue(summary.paidByCurrency.isEmpty())
        assertTrue(summary.grossPay.isEmpty)
    }

    @Test
    fun `a currency that earned nothing does not turn a single total into a mixed one`() {
        val unpaid = profile("job-b", "USD", 0.0)
        val summary = sum(shiftA, shiftB, profiles = listOf(jobA, unpaid))

        assertEquals(listOf("ILS"), summary.paidByCurrency.map { it.currencyCode })
        val (code, amount) = summary.grossPay.singleOrNull()!!
        assertEquals("ILS", code)
        assertEquals(4000.0, amount, 0.001)
    }

    @Test
    fun `deductions and net stay in the currency they belong to`() {
        val percent = jobA.copy(
            rules = jobA.rules.copy(deductionsEnabled = true, deductionsMode = "percentage", deductionsPercentage = 10.0),
        )
        val fixed = jobB.copy(
            rules = jobB.rules.copy(deductionsEnabled = true, deductionsMode = "fixed", deductionsFixedAmount = 50.0),
        )
        val summary = sum(shiftA, shiftB, profiles = listOf(percent, fixed))
        val ils = summary.byCurrency.first { it.currencyCode == "ILS" }
        val usd = summary.byCurrency.first { it.currencyCode == "USD" }

        assertEquals(400.0, ils.deductionsGross, 0.001)
        assertEquals(3600.0, ils.netGross, 0.001)
        assertEquals(50.0, usd.deductionsGross, 0.001)
        assertEquals(950.0, usd.netGross, 0.001)
    }

    @Test
    fun `a deduction larger than one currency's gross does not reach into another`() {
        val fixed = jobB.copy(
            rules = jobB.rules.copy(deductionsEnabled = true, deductionsMode = "fixed", deductionsFixedAmount = 2500.0),
        )
        val summary = sum(shiftA, shiftB, profiles = listOf(jobA, fixed))

        assertEquals(0.0, summary.byCurrency.first { it.currencyCode == "USD" }.netGross, 0.0)
        assertEquals(4000.0, summary.byCurrency.first { it.currencyCode == "ILS" }.netGross, 0.001)
    }

    @Test
    fun `a premium raises pay only in its own shift's currency`() {
        val premium = PremiumProfile(
            id = "double",
            userId = "u1",
            name = "Double",
            multiplier = 2.0,
            premiumType = PremiumType.HIGHEST_ONLY,
        )
        val doubled = shiftB.copy(isSpecialDay = true, premiumProfileId = premium.id)
        val summary = PayrollCalculator.sumMonthlyPay(
            listOf(shiftA, doubled), settings, listOf(jobA, jobB, jobC), listOf(premium),
        )

        assertEquals(4000.0, summary.grossPay["ILS"], 0.001)
        assertEquals(2000.0, summary.grossPay["USD"], 0.001)
    }

    // ── Aggregates built beside the summary ───────────────────────────────────

    @Test
    fun `weekly and task totals are per currency and agree with the month`() {
        val month = listOf(shiftA, shiftB, shiftC)
        val summary = sum(*month.toTypedArray())
        val weeks = WeeklyBreakdownBuilder.groupByWeek(
            shifts = month,
            settings = settings,
            profiles = listOf(jobA, jobB, jobC),
        )
        val weekly = weeks.mapNotNull { it.pay }.fold(PayByCurrency.EMPTY) { acc, pay -> acc + pay }
        assertEquals(summary.grossPay, weekly)

        val task = Task(id = "t", userId = "u1", name = "Support", icon = "💼", hourlyRate = 0.0)
        val tasks = TaskMonthlyReportBuilder.build(
            shifts = month.map { it.copy(taskId = task.id, taskNameSnapshot = task.name) },
            settings = settings,
            tasks = listOf(task),
            profiles = listOf(jobA, jobB, jobC),
        )
        assertEquals(summary.grossPay, tasks.single().totalPay)
    }

    @Test
    fun `insights refuse to average or rank across currencies`() {
        val mixed = ReportInsightsBuilder.build(listOf(shiftA, shiftB), settings, listOf(jobA, jobB))!!
        assertTrue(mixed.payInMultipleCurrencies)
        assertNull(mixed.avgPayPerShift)
        assertNull(mixed.highestEarningAmount)
        assertNull(mixed.payCurrencyCode)

        val single = ReportInsightsBuilder.build(listOf(shiftA, shiftC), settings, listOf(jobA, jobC))!!
        assertFalse(single.payInMultipleCurrencies)
        assertEquals("ILS", single.payCurrencyCode)
        assertEquals(2100.0, single.avgPayPerShift!!, 0.001)
        assertEquals(4000.0, single.highestEarningAmount!!, 0.001)
    }

    @Test
    fun `a month paid in two currencies is not averaged into a leave day rate`() {
        // February at one currency, March at two, both at the same workplace.
        val february = shiftA.copy(
            id = "feb",
            startTime = Instant.parse("2024-02-05T09:00:00Z"),
            endTime = Instant.parse("2024-02-05T17:00:00Z"),
        )
        val march = listOf(shiftA, shiftB).map { s ->
            s.copy(
                id = "mar-${s.id}",
                startTime = s.startTime.plusSeconds(60L * 24 * 60 * 60),
                endTime = s.endTime!!.plusSeconds(60L * 24 * 60 * 60),
            )
        }
        val history = LeaveEarningsBase.build(
            shifts = listOf(february) + march,
            settings = settings,
            profiles = listOf(jobA, jobB),
            reference = YearMonth.of(2024, 4),
        )

        assertEquals(listOf(YearMonth.of(2024, 2)), history.months.map { it.yearMonth })
        assertEquals(4000.0, history.months.single().eligibleGross, 0.001)
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private val withCode: (String, String) -> String = { number, code -> "$number $code" }

    @Test
    fun `one currency formats exactly as it always has`() {
        val pay = PayByCurrency.of("ILS", 4000.0)

        assertEquals(
            MoneyFormatter.format(4000.0, "ILS", Locale.US),
            MoneyFormatter.formatEach(pay, Locale.US, withCode = withCode),
        )
    }

    @Test
    fun `two currencies format as two labelled amounts and never as their sum`() {
        val pay = PayByCurrency.of("ILS", 4000.0) + PayByCurrency.of("USD", 1000.0)
        val text = MoneyFormatter.formatEach(pay, Locale.US, withCode = withCode)

        assertEquals("4,000.00 ILS · 1,000.00 USD", text)
        assertFalse(text.contains("5,000"))
    }

    @Test
    fun `zero buckets are dropped and order is stable`() {
        val pay = PayByCurrency.from(listOf("USD" to 10.0, "ILS" to 0.0, "EUR" to 5.0, "USD" to -10.0))

        assertEquals(listOf("EUR"), pay.currencies)
        assertFalse(pay.isMixed)
        assertEquals(
            listOf("EUR", "ILS", "USD"),
            (PayByCurrency.of("USD", 1.0) + PayByCurrency.of("ILS", 1.0) + PayByCurrency.of("EUR", 1.0)).currencies,
        )
    }
}
