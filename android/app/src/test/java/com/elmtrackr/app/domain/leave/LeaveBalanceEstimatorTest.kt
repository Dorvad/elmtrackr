package com.elmtrackr.app.domain.leave

import com.elmtrackr.app.domain.model.AbsenceAllocation
import com.elmtrackr.app.domain.model.AbsenceType
import com.elmtrackr.app.domain.model.LeaveBalanceSnapshot
import com.elmtrackr.app.domain.model.LeaveBalanceUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class LeaveBalanceEstimatorTest {

    private fun snapshot(
        asOf: String,
        balance: Double,
        unit: LeaveBalanceUnit = LeaveBalanceUnit.DAYS,
        type: AbsenceType = AbsenceType.SICK,
        createdAt: Instant = Instant.EPOCH,
        id: String = "snap-$asOf-$balance",
    ) = LeaveBalanceSnapshot(
        id = id,
        userId = "u1",
        workplaceId = "wp-a",
        balanceType = type,
        balance = balance,
        unit = unit,
        asOfDate = LocalDate.parse(asOf),
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun allocation(
        date: String,
        units: Double = 1.0,
        unit: LeaveBalanceUnit = LeaveBalanceUnit.DAYS,
        workplaceId: String = "wp-a",
    ) = AbsenceAllocation(
        id = "alloc-$date-$units",
        userId = "u1",
        absenceEventId = "event-1",
        workplaceId = workplaceId,
        affectedDate = LocalDate.parse(date),
        entitlementUnits = units,
        unit = unit,
    )

    private fun estimate(
        latest: LeaveBalanceSnapshot?,
        allocations: List<AbsenceAllocation>,
        standardDayMinutes: Int? = null,
    ) = LeaveBalanceEstimator.estimate(
        workplaceId = "wp-a",
        balanceType = AbsenceType.SICK,
        latestSnapshot = latest,
        allocations = allocations,
        standardDayMinutes = standardDayMinutes,
    )

    @Test
    fun `estimated balance is the payslip balance minus what was reported after it`() {
        val result = estimate(
            latest = snapshot("2026-07-31", 12.0),
            allocations = listOf(allocation("2026-08-05"), allocation("2026-08-08", units = 0.5)),
        )

        assertEquals(1.5, result.unitsUsedSinceSnapshot, 0.0001)
        assertEquals(10.5, result.estimatedBalance!!, 0.0001)
        assertEquals(12.0, result.official!!.balance, 0.0001)
    }

    @Test
    fun `a newer payslip balance becomes the new starting point`() {
        // 31 Jul said 12 days, one was used on 5 Aug, and 31 Aug says 13. The
        // estimate starts from 13 and forgets the July arithmetic entirely.
        val snapshots = listOf(snapshot("2026-07-31", 12.0), snapshot("2026-08-31", 13.0))

        val latest = LeaveBalanceEstimator.latestSnapshot(snapshots)
        val result = estimate(latest = latest, allocations = listOf(allocation("2026-08-05")))

        assertEquals(LocalDate.of(2026, 8, 31), latest!!.asOfDate)
        assertEquals(0.0, result.unitsUsedSinceSnapshot, 0.0001)
        assertEquals(13.0, result.estimatedBalance!!, 0.0001)
    }

    @Test
    fun `leave on or before the balance date is not deducted again`() {
        // A payslip dated the 31st already accounts for everything up to and
        // including that day.
        val result = estimate(
            latest = snapshot("2026-07-31", 12.0),
            allocations = listOf(
                allocation("2026-07-20"),
                allocation("2026-07-31"),
                allocation("2026-08-01"),
            ),
        )

        assertEquals(1.0, result.unitsUsedSinceSnapshot, 0.0001)
        assertEquals(11.0, result.estimatedBalance!!, 0.0001)
    }

    @Test
    fun `a negative estimate is reported rather than clamped to zero`() {
        val result = estimate(
            latest = snapshot("2026-07-31", 1.0),
            allocations = listOf(allocation("2026-08-05"), allocation("2026-08-06", units = 0.5)),
        )

        assertEquals(-0.5, result.estimatedBalance!!, 0.0001)
        assertTrue(result.isNegative)
    }

    @Test
    fun `with no payslip balance the estimate is unknown rather than zero`() {
        val result = estimate(latest = null, allocations = listOf(allocation("2026-08-05")))

        assertNull(result.estimatedBalance)
        assertFalse(result.hasOfficialBalance)
        assertEquals(1.0, result.unitsUsedSinceSnapshot, 0.0001)
    }

    @Test
    fun `no accrual is added between payslips`() {
        // Three months after the snapshot with nothing reported, the estimate is
        // still exactly the snapshot: V1 never invents accrued days.
        val result = estimate(latest = snapshot("2026-05-31", 8.0), allocations = emptyList())

        assertEquals(8.0, result.estimatedBalance!!, 0.0001)
    }

    @Test
    fun `hours are converted into days when the workplace has a standard day`() {
        val result = estimate(
            latest = snapshot("2026-07-31", 10.0, unit = LeaveBalanceUnit.DAYS),
            allocations = listOf(allocation("2026-08-05", units = 4.0, unit = LeaveBalanceUnit.HOURS)),
            standardDayMinutes = 480,
        )

        assertEquals(0.5, result.unitsUsedSinceSnapshot, 0.0001)
        assertEquals(9.5, result.estimatedBalance!!, 0.0001)
        assertEquals(0, result.unconvertibleCount)
    }

    @Test
    fun `without a standard day mismatched units are reported instead of guessed`() {
        val result = estimate(
            latest = snapshot("2026-07-31", 10.0, unit = LeaveBalanceUnit.DAYS),
            allocations = listOf(allocation("2026-08-05", units = 4.0, unit = LeaveBalanceUnit.HOURS)),
            standardDayMinutes = null,
        )

        assertEquals(1, result.unconvertibleCount)
        assertEquals(0.0, result.unitsUsedSinceSnapshot, 0.0001)
        assertEquals(10.0, result.estimatedBalance!!, 0.0001)
    }

    @Test
    fun `a balance kept in hours deducts hours directly`() {
        val result = estimate(
            latest = snapshot("2026-07-31", 80.0, unit = LeaveBalanceUnit.HOURS),
            allocations = listOf(allocation("2026-08-05", units = 6.0, unit = LeaveBalanceUnit.HOURS)),
        )

        assertEquals(74.0, result.estimatedBalance!!, 0.0001)
        assertEquals(LeaveBalanceUnit.HOURS, result.unit)
    }

    @Test
    fun `the latest snapshot wins on date, then on when it was entered`() {
        // Correcting a balance for a date already entered supersedes the earlier
        // row without erasing it from history.
        val first = snapshot("2026-07-31", 12.0, createdAt = Instant.ofEpochSecond(1_000), id = "first")
        val correction = snapshot("2026-07-31", 11.0, createdAt = Instant.ofEpochSecond(2_000), id = "second")

        val latest = LeaveBalanceEstimator.latestSnapshot(listOf(first, correction))

        assertEquals("second", latest!!.id)
        assertEquals(11.0, latest.balance, 0.0001)
    }

    @Test
    fun `no snapshots at all resolves to nothing`() {
        assertNull(LeaveBalanceEstimator.latestSnapshot(emptyList()))
    }

    @Test
    fun `removing reported leave restores the estimated balance`() {
        val official = snapshot("2026-07-31", 12.0)
        val before = estimate(official, listOf(allocation("2026-08-05"), allocation("2026-08-08")))

        val afterDeletingOne = estimate(official, listOf(allocation("2026-08-05")))

        assertEquals(10.0, before.estimatedBalance!!, 0.0001)
        assertEquals(11.0, afterDeletingOne.estimatedBalance!!, 0.0001)
    }

    @Test
    fun `editing a reported day changes the estimated balance`() {
        val official = snapshot("2026-07-31", 12.0)

        val fullDay = estimate(official, listOf(allocation("2026-08-05", units = 1.0)))
        val halfDay = estimate(official, listOf(allocation("2026-08-05", units = 0.5)))

        assertEquals(11.0, fullDay.estimatedBalance!!, 0.0001)
        assertEquals(11.5, halfDay.estimatedBalance!!, 0.0001)
    }

    @Test
    fun `conversion refuses a zero-length standard day`() {
        assertNull(
            LeaveBalanceEstimator.convert(
                units = 4.0,
                from = LeaveBalanceUnit.HOURS,
                to = LeaveBalanceUnit.DAYS,
                standardDayMinutes = 0,
            ),
        )
    }

    @Test
    fun `converting between the same unit is the identity`() {
        assertEquals(
            2.5,
            LeaveBalanceEstimator.convert(2.5, LeaveBalanceUnit.DAYS, LeaveBalanceUnit.DAYS, null)!!,
            0.0,
        )
    }
}
