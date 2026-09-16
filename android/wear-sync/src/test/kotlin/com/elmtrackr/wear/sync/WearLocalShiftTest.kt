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
        val next = WearLocalShift.punchIn(WearShiftSnapshot.signedOut(now), now, ZoneOffset.UTC)

        assertTrue(next.signedIn)
        assertTrue(next.isActive)
        assertEquals(now, next.shiftStartEpochMillis)
        assertEquals("watch-$now", next.shiftId)
        assertEquals("22:13", WearLocalShift.formatHm(now, ZoneOffset.UTC))
        assertEquals(WearLocalShift.todayEpochDay(now, ZoneOffset.UTC), next.todayEpochDay)
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
        val running = WearLocalShift.punchIn(WearShiftSnapshot.signedOut(start), start, ZoneOffset.UTC)
            .copy(todayMinutes = 10)
        val next = WearLocalShift.punchOut(running, end, ZoneOffset.UTC)

        assertFalse(next.isActive)
        assertEquals(0L, next.shiftStartEpochMillis)
        assertEquals(end, next.lastPunchEndEpochMillis)
        assertEquals(100, next.todayMinutes)
        assertEquals(WearLocalShift.todayEpochDay(start, ZoneOffset.UTC), next.todayEpochDay)
    }

    @Test
    fun punchOut_creditsOnlyTheMinutesOnTodayWhenTheShiftCrossedMidnight() {
        val start = 1_700_000_000_000L
        val end = start + 3 * 60 * 60_000L
        val running = WearLocalShift.punchIn(WearShiftSnapshot.signedOut(start), start, ZoneOffset.UTC)
        val next = WearLocalShift.punchOut(running, end, ZoneOffset.UTC)
        val expected = WearLocalShift.minutesOnLocalDay(start, end, end, ZoneOffset.UTC)

        assertEquals(expected, next.todayMinutes)
        assertTrue(expected in 70..80)
        assertEquals(WearLocalShift.todayEpochDay(end, ZoneOffset.UTC), next.todayEpochDay)
    }

    @Test
    fun rollToLocalDay_zerosCompletedTotalsAtMidnight() {
        val late = 1_700_000_000_000L
        val nextMorning = late + 10 * 60 * 60_000L
        val completed = WearShiftSnapshot.signedOut(late).copy(
            todayMinutes = 100,
            todayEpochDay = WearLocalShift.todayEpochDay(late, ZoneOffset.UTC),
        )
        val rolled = WearLocalShift.rollToLocalDay(completed, nextMorning, ZoneOffset.UTC)

        assertEquals(0, rolled.todayMinutes)
        assertEquals(WearLocalShift.todayEpochDay(nextMorning, ZoneOffset.UTC), rolled.todayEpochDay)
    }

    @Test
    fun rollToLocalDay_leavesUnstampedTotalsAlone() {
        val late = 1_700_000_000_000L
        val nextMorning = late + 10 * 60 * 60_000L
        val legacy = WearShiftSnapshot.signedOut(late).copy(todayMinutes = 100, todayEpochDay = 0L)

        assertEquals(legacy, WearLocalShift.rollToLocalDay(legacy, nextMorning, ZoneOffset.UTC))
    }

    @Test
    fun replayEventSettled_dropsALostAckWhenThePhoneAlreadyMatches() {
        val inEvent = WearPunchEvent(id = "a", isPunchIn = true, epochMillis = 1L)
        val outEvent = WearPunchEvent(id = "b", isPunchIn = false, epochMillis = 2L)
        val timeout = PunchResult(success = false, errorCode = "timeout")
        val noShift = PunchResult(success = false, errorCode = "no_active_shift")
        val unreachable = PunchResult(success = false, errorCode = "phone_unreachable")
        val runningPhone = WearShiftSnapshot.signedOut().copy(
            signedIn = true,
            isActive = true,
            shiftStartEpochMillis = 1L,
        )
        val idlePhone = WearShiftSnapshot.signedOut().copy(signedIn = true, isActive = false)

        assertTrue(WearLocalShift.replayEventSettled(inEvent, PunchResult(success = true), idlePhone))
        assertTrue(WearLocalShift.replayEventSettled(inEvent, timeout, runningPhone))
        assertTrue(WearLocalShift.replayEventSettled(outEvent, noShift, runningPhone))
        assertTrue(WearLocalShift.replayEventSettled(outEvent, timeout, idlePhone))
        assertFalse(WearLocalShift.replayEventSettled(inEvent, timeout, idlePhone))
        assertFalse(WearLocalShift.replayEventSettled(outEvent, timeout, runningPhone))
        assertFalse(WearLocalShift.replayEventSettled(inEvent, timeout, null))
        assertFalse(WearLocalShift.replayEventSettled(outEvent, timeout, idlePhone.copy(updatedAtEpochMillis = 1L)))
        assertFalse(WearLocalShift.replayEventSettled(inEvent, unreachable, runningPhone))
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
        assertFalse(WearLocalShift.shouldFallbackToLocal("clock_in_failed"))
        assertFalse(WearLocalShift.shouldFallbackToLocal("no_active_shift"))
    }

    @Test
    fun shouldFallbackToLocal_doesNotBypassThePhoneAppLock() {
        assertFalse(WearLocalShift.shouldFallbackToLocal("app_locked"))
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
