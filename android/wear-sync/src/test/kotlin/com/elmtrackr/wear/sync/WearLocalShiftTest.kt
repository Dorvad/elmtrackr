package com.elmtrackr.wear.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class WearLocalShiftTest {

    @Test
    fun punchIn_startsAShiftOnASignedOutWatch() {
        val now = 1_700_000_000_000L
        val next = WearLocalShift.punchIn(WearShiftSnapshot.signedOut(now), now)

        assertTrue(next.signedIn)
        assertTrue(next.isActive)
        assertEquals(now, next.shiftStartEpochMillis)
        assertEquals("watch-$now", next.shiftId)
        assertEquals("22:13", WearLocalShift.formatHm(now, ZoneOffset.UTC))
    }

    @Test
    fun punchIn_isIdempotentWhileAlreadyActive() {
        val started = WearLocalShift.punchIn(WearShiftSnapshot.signedOut(), 1_000L)
        val again = WearLocalShift.punchIn(started, 2_000L)

        assertEquals(1_000L, again.shiftStartEpochMillis)
        assertEquals(started.shiftId, again.shiftId)
    }

    @Test
    fun punchOut_addsElapsedMinutesToTodayAndClearsTheActiveShift() {
        val start = 1_700_000_000_000L
        val end = start + 90 * 60_000L
        val running = WearLocalShift.punchIn(WearShiftSnapshot.signedOut(start), start)
            .copy(todayMinutes = 10)
        val next = WearLocalShift.punchOut(running, end)

        assertFalse(next.isActive)
        assertEquals(0L, next.shiftStartEpochMillis)
        assertEquals(end, next.lastPunchEndEpochMillis)
        assertEquals(100, next.todayMinutes)
    }

    @Test
    fun shouldApplyPhoneSnapshot_rejectsSignedOutOverLocalWork() {
        val local = WearLocalShift.punchIn(WearShiftSnapshot.signedOut(), 1_000L)
        val phone = WearShiftSnapshot.signedOut(2_000L)

        assertFalse(WearLocalShift.shouldApplyPhoneSnapshot(local, phone, hasPendingReplay = false))
    }

    @Test
    fun shouldApplyPhoneSnapshot_rejectsAnyPhoneStateWhileReplayIsPending() {
        val local = WearLocalShift.punchIn(WearShiftSnapshot.signedOut(), 1_000L)
        val phone = WearShiftSnapshot(signedIn = true, isActive = false, updatedAtEpochMillis = 2_000L)

        assertFalse(WearLocalShift.shouldApplyPhoneSnapshot(local, phone, hasPendingReplay = true))
    }

    @Test
    fun shouldApplyPhoneSnapshot_acceptsSignedInOnceReplayHasFinished() {
        val local = WearLocalShift.punchIn(WearShiftSnapshot.signedOut(), 1_000L)
        val phone = WearShiftSnapshot(
            signedIn = true,
            isActive = true,
            shiftStartEpochMillis = 1_000L,
            updatedAtEpochMillis = 2_000L,
        )

        assertTrue(WearLocalShift.shouldApplyPhoneSnapshot(local, phone, hasPendingReplay = false))
    }

    @Test
    fun shouldFallbackToLocal_coversTheUnpairedAndUnsignedReasons() {
        assertTrue(WearLocalShift.shouldFallbackToLocal("phone_unreachable"))
        assertTrue(WearLocalShift.shouldFallbackToLocal("not_signed_in"))
        assertTrue(WearLocalShift.shouldFallbackToLocal("timeout"))
        assertTrue(WearLocalShift.shouldFallbackToLocal("sync_disabled"))
        assertTrue(WearLocalShift.shouldFallbackToLocal("app_locked"))
        assertFalse(WearLocalShift.shouldFallbackToLocal("clock_in_failed"))
        assertFalse(WearLocalShift.shouldFallbackToLocal("no_active_shift"))
    }

    @Test
    fun mergeConsent_keepsLocalShiftState() {
        val local = WearLocalShift.punchIn(WearShiftSnapshot.signedOut(), 1_000L)
        val phone = WearShiftSnapshot.signedOut().copy(crashReportingEnabled = false)

        val merged = WearLocalShift.mergeConsent(local, phone)
        assertTrue(merged.isActive)
        assertFalse(merged.crashReportingEnabled)
    }
}
