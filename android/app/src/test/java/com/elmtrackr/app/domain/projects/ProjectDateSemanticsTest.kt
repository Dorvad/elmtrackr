package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment
import com.elmtrackr.app.domain.money.Money
import com.elmtrackr.app.domain.money.ProjectFee
import com.elmtrackr.app.domain.money.TaxMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Billing dates are calendar dates, not instants.
 *
 * A due date is a day someone agreed on. Storing it as a moment in time would
 * make it a different day in Auckland than in Los Angeles, and would move a
 * project in and out of "overdue" as the user travels or as the clock passes
 * midnight in a zone they do not live in. The persisted form is an epoch day
 * and the comparison is [LocalDate.isAfter] in the user's own work timezone,
 * which is what these tests pin down.
 */
class ProjectDateSemanticsTest {

    private fun fee(amount: String = "1000", currency: String = "USD") =
        ProjectFee.from(BigDecimal(amount), currency, TaxMode.NONE, BigDecimal.ZERO)

    private fun record(
        billedOn: LocalDate,
        dueOn: LocalDate?,
        cancelledAt: Instant? = null,
    ) = ProjectBillingRecord(
        id = "b1",
        userId = "u1",
        projectId = "p1",
        fee = fee(),
        billedOn = billedOn,
        dueOn = dueOn,
        cancelledAt = cancelledAt,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun payment(amount: String, paidOn: LocalDate) = ProjectPayment(
        id = "pay-$amount",
        userId = "u1",
        projectId = "p1",
        billingRecordId = "b1",
        paidOn = paidOn,
        amount = Money.of(amount, "USD"),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun resolve(
        today: LocalDate,
        dueOn: LocalDate? = LocalDate.of(2026, 7, 20),
        payments: List<ProjectPayment> = emptyList(),
        cancelledAt: Instant? = null,
    ) = ProjectBillingStatusResolver.resolve(
        currencyCode = "USD",
        billingRecords = listOf(record(LocalDate.of(2026, 7, 1), dueOn, cancelledAt)),
        payments = payments,
        today = today,
    )

    // ── Date-only storage ────────────────────────────────────────────────────

    @Test
    fun `an epoch day round-trips to the same calendar date`() {
        val due = LocalDate.of(2026, 7, 20)

        assertEquals(due, LocalDate.ofEpochDay(due.toEpochDay()))
    }

    @Test
    fun `an epoch day is the same calendar date in every timezone`() {
        // The property that makes the storage choice correct: the number does
        // not encode a moment, so no zone can reinterpret it.
        val due = LocalDate.of(2026, 7, 20)
        val zones = listOf("Pacific/Auckland", "Asia/Jerusalem", "UTC", "America/Los_Angeles")

        zones.forEach { zone ->
            // Reading it back does not consult a zone at all.
            assertEquals(
                "epoch day changed meaning in $zone",
                due,
                LocalDate.ofEpochDay(due.toEpochDay()),
            )
            assertTrue(ZoneId.of(zone).id.isNotEmpty())
        }
    }

    // ── The boundary ─────────────────────────────────────────────────────────

    @Test
    fun `due today is not overdue`() {
        val status = resolve(today = LocalDate.of(2026, 7, 20))

        assertFalse(status.isOverdue)
        assertEquals(0L, status.daysUntilDue)
        assertNull(status.daysOverdue)
    }

    @Test
    fun `the day after the due date is overdue by one`() {
        val status = resolve(today = LocalDate.of(2026, 7, 21))

        assertTrue(status.isOverdue)
        assertEquals(1L, status.daysOverdue)
    }

    @Test
    fun `the day before the due date is not overdue`() {
        val status = resolve(today = LocalDate.of(2026, 7, 19))

        assertFalse(status.isOverdue)
        assertEquals(1L, status.daysUntilDue)
    }

    // ── Near midnight, across zones ──────────────────────────────────────────

    @Test
    fun `the date just before midnight and just after are different days`() {
        // Whichever zone the user works in, "today" is that zone's calendar day.
        // These two instants are one minute apart and must resolve to different
        // days in Jerusalem — the day flip is the only thing that can move a
        // record into overdue.
        val zone = ZoneId.of("Asia/Jerusalem")
        val before = Instant.parse("2026-07-20T20:59:00Z").atZone(zone).toLocalDate()
        val after = Instant.parse("2026-07-20T21:01:00Z").atZone(zone).toLocalDate()

        assertEquals(LocalDate.of(2026, 7, 20), before)
        assertEquals(LocalDate.of(2026, 7, 21), after)

        assertFalse(resolve(today = before).isOverdue)
        assertTrue(resolve(today = after).isOverdue)
    }

    @Test
    fun `the same instant can be two different days in two zones`() {
        // The reason "today" must come from the user's work timezone and not
        // from the device's default: at this instant it is already the 21st in
        // Auckland and still the 20th in Los Angeles, so the same record is
        // overdue for one user and not the other. Both answers are correct.
        val instant = Instant.parse("2026-07-20T12:00:00Z")
        val auckland = instant.atZone(ZoneId.of("Pacific/Auckland")).toLocalDate()
        val losAngeles = instant.atZone(ZoneId.of("America/Los_Angeles")).toLocalDate()

        assertEquals(LocalDate.of(2026, 7, 21), auckland)
        assertEquals(LocalDate.of(2026, 7, 20), losAngeles)

        assertTrue(resolve(today = auckland).isOverdue)
        assertFalse(resolve(today = losAngeles).isOverdue)
    }

    @Test
    fun `a due date is never shifted by the zone it is read in`() {
        // The stored date itself is stable even where the instant is not.
        val stored = LocalDate.of(2026, 7, 20).toEpochDay()

        listOf("Pacific/Kiritimati", "Pacific/Niue", "UTC").forEach { zone ->
            val status = ProjectBillingStatusResolver.resolve(
                currencyCode = "USD",
                billingRecords = listOf(
                    record(LocalDate.of(2026, 7, 1), LocalDate.ofEpochDay(stored)),
                ),
                payments = emptyList(),
                today = Instant.parse("2026-07-20T12:00:00Z")
                    .atZone(ZoneId.of(zone)).toLocalDate(),
            )

            assertEquals(LocalDate.of(2026, 7, 20), status.dueOn)
        }
    }

    // ── Interaction with the rest of the rules ───────────────────────────────

    @Test
    fun `a fully paid record is not overdue however late the date`() {
        val status = resolve(
            today = LocalDate.of(2027, 1, 1),
            payments = listOf(payment("1000", LocalDate.of(2026, 7, 2))),
        )

        assertFalse(status.isOverdue)
        assertEquals(ProjectBillingStatus.PAID, status.status)
    }

    @Test
    fun `a cancelled record never goes overdue`() {
        val status = resolve(
            today = LocalDate.of(2027, 1, 1),
            cancelledAt = Instant.parse("2026-07-05T00:00:00Z"),
        )

        assertFalse(status.isOverdue)
    }

    @Test
    fun `a record with no due date never goes overdue`() {
        val status = resolve(today = LocalDate.of(2027, 1, 1), dueOn = null)

        assertFalse(status.isOverdue)
        assertNull(status.daysUntilDue)
        assertNull(status.daysOverdue)
    }

    @Test
    fun `a partly paid record past its due date is overdue`() {
        val status = resolve(
            today = LocalDate.of(2026, 7, 25),
            payments = listOf(payment("400", LocalDate.of(2026, 7, 10))),
        )

        assertTrue(status.isOverdue)
        assertEquals(5L, status.daysOverdue)
        assertEquals(Money.of("600", "USD"), status.outstanding)
    }

    @Test
    fun `days overdue counts calendar days, not elapsed hours`() {
        // 30 days after a 20 July due date, regardless of any daylight-saving
        // transition in between.
        val status = resolve(today = LocalDate.of(2026, 8, 19))

        assertEquals(30L, status.daysOverdue)
    }
}
