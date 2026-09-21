package com.elmtrackr.wear.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
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
    fun punchOut_doesNotDoubleCountAnOvernightShiftAlreadyRolledIntoToday() {
        val start = Instant.parse("2024-06-01T23:00:00Z").toEpochMilli()
        val rolledAt = Instant.parse("2024-06-02T01:00:00Z").toEpochMilli()
        val end = Instant.parse("2024-06-02T02:00:00Z").toEpochMilli()
        val running = WearLocalShift.punchIn(WearShiftSnapshot.signedOut(start), start, ZoneOffset.UTC)
        val rolled = WearLocalShift.rollToLocalDay(running, rolledAt, ZoneOffset.UTC)

        val next = WearLocalShift.punchOut(rolled, end, ZoneOffset.UTC)

        assertEquals(60, rolled.todayMinutes)
        assertEquals(120, next.todayMinutes)
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
    fun shouldDeferReplay_holdsOlderQueuedPunchesWhenPhoneHasNewerActiveShift() {
        val oldClockIn = WearPunchEvent(id = "a", isPunchIn = true, epochMillis = 1_000L)
        val oldClockOut = WearPunchEvent(id = "b", isPunchIn = false, epochMillis = 2_000L)
        val newerPhoneShift = WearShiftSnapshot(
            signedIn = true,
            isActive = true,
            shiftStartEpochMillis = 3_000L,
        )
        val olderPhoneShift = newerPhoneShift.copy(shiftStartEpochMillis = 500L)
        val idlePhone = newerPhoneShift.copy(isActive = false)

        assertTrue(WearLocalShift.shouldDeferReplay(oldClockIn, newerPhoneShift))
        assertTrue(WearLocalShift.shouldDeferReplay(oldClockOut, newerPhoneShift))
        assertFalse(WearLocalShift.shouldDeferReplay(oldClockIn, olderPhoneShift))
        assertFalse(WearLocalShift.shouldDeferReplay(oldClockOut, idlePhone))
        assertFalse(WearLocalShift.shouldDeferReplay(oldClockIn, null))
    }

    @Test
    fun livePunchSettledByPhone_rejectsStaleMatchingStateAfterTimeout() {
        val staleRunningPhone = WearShiftSnapshot.signedOut().copy(
            signedIn = true,
            isActive = true,
            shiftStartEpochMillis = 1_000L,
        )
        val staleIdlePhone = WearShiftSnapshot.signedOut().copy(
            signedIn = true,
            isActive = false,
            lastPunchEndEpochMillis = 1_000L,
        )

        assertFalse(WearLocalShift.livePunchSettledByPhone(true, 2_000L, staleRunningPhone))
        assertFalse(WearLocalShift.livePunchSettledByPhone(false, 2_000L, staleIdlePhone))
    }

    @Test
    fun livePunchSettledByPhone_acceptsPhoneSnapshotThatCarriesThisPunchTime() {
        val freshRunningPhone = WearShiftSnapshot.signedOut().copy(
            signedIn = true,
            isActive = true,
            shiftStartEpochMillis = 2_000L,
        )
        val freshIdlePhone = WearShiftSnapshot.signedOut().copy(
            signedIn = true,
            isActive = false,
            lastPunchEndEpochMillis = 2_000L,
        )

        assertTrue(WearLocalShift.livePunchSettledByPhone(true, 2_000L, freshRunningPhone))
        assertTrue(WearLocalShift.livePunchSettledByPhone(false, 2_000L, freshIdlePhone))
    }

    @Test
    fun shouldApplyPhoneSnapshot_rejectsStaleSignedOutOverLocalWork() {
        val local = WearLocalShift.punchIn(WearShiftSnapshot.signedOut(), 1_000L)
        val phone = WearShiftSnapshot.signedOut(500L)

        assertFalse(WearLocalShift.shouldApplyPhoneSnapshot(local, phone, hasPendingReplay = false))
    }

    @Test
    fun shouldApplyPhoneSnapshot_acceptsNewerSignedOutClearOnceReplayHasFinished() {
        val local = WearLocalShift.punchIn(WearShiftSnapshot.signedOut(), 1_000L)
        val phone = WearShiftSnapshot.signedOut(2_000L)

        assertTrue(WearLocalShift.shouldApplyPhoneSnapshot(local, phone, hasPendingReplay = false))
    }

    @Test
    fun shouldApplyPhoneSnapshot_acceptsSignedOutClearEvenAfterLocalRolloverUpdatedTheCache() {
        val local = WearLocalShift.punchIn(WearShiftSnapshot.signedOut(), 1_000L)
            .copy(updatedAtEpochMillis = 3_000L)
        val phone = WearShiftSnapshot.signedOut(2_000L)

        assertTrue(WearLocalShift.shouldApplyPhoneSnapshot(local, phone, hasPendingReplay = false))
    }

    @Test
    fun shouldApplyPhoneSnapshot_rejectsNewerSignedOutClearWhileReplayIsPending() {
        val local = WearLocalShift.punchIn(WearShiftSnapshot.signedOut(), 1_000L)
        val phone = WearShiftSnapshot.signedOut(2_000L)

        assertFalse(WearLocalShift.shouldApplyPhoneSnapshot(local, phone, hasPendingReplay = true))
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
        assertFalse(WearLocalShift.shouldFallbackToLocal("active_shift_newer"))
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
