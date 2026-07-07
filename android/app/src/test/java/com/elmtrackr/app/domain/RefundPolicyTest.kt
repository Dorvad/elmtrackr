package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.RefundAction
import com.elmtrackr.app.domain.model.Shift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class RefundPolicyTest {

    private val zone = ZoneId.of("UTC")

    @Test
    fun `completed shift is eligible for both directions`() {
        val shift = completedShift()

        assertTrue(RefundPolicy.checkToWorkEligibility(shift, zone).eligible)
        assertTrue(RefundPolicy.checkFromWorkEligibility(shift, zone).eligible)
        assertTrue(RefundPolicy.isEligibleForRefund(shift, zone))
    }

    @Test
    fun `active shift is not eligible`() {
        val shift = Shift(
            id = "active",
            userId = "user",
            startTime = Instant.parse("2024-01-08T12:00:00Z"),
            endTime = null,
        )

        assertFalse(RefundPolicy.checkToWorkEligibility(shift, zone).eligible)
        assertFalse(RefundPolicy.checkFromWorkEligibility(shift, zone).eligible)
        assertFalse(RefundPolicy.isEligibleForRefund(shift, zone))
    }

    @Test
    fun `unresolved only counts remind later shifts`() {
        val shift = completedShift().copy(refundAction = RefundAction.REMIND_LATER)

        assertTrue(RefundPolicy.isUnresolved(shift, zone))
        assertEquals(1, RefundPolicy.countUnresolved(listOf(shift), zone))
    }

    @Test
    fun `completed shift without action is not unresolved`() {
        val shift = completedShift()

        assertFalse(RefundPolicy.isUnresolved(shift, zone))
        assertEquals(0, RefundPolicy.countUnresolved(listOf(shift), zone))
    }

    private fun completedShift(): Shift = Shift(
        id = "shift",
        userId = "user",
        startTime = Instant.parse("2024-01-08T12:00:00Z"),
        endTime = Instant.parse("2024-01-08T17:00:00Z"),
    )
}
