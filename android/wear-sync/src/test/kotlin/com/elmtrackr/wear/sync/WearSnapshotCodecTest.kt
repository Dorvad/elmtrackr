package com.elmtrackr.wear.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearSnapshotCodecTest {

    @Test
    fun encodeDecode_roundTrip() {
        val snapshot = WearShiftSnapshot(
            signedIn = true,
            isActive = true,
            shiftId = "shift-1",
            shiftStartEpochMillis = 1_700_000_000_000L,
            startTimeLabel = "09:00",
            todayMinutes = 120,
            dailyGoalMinutes = 480,
            updatedAtEpochMillis = 1_700_000_100_000L,
        )
        val encoded = WearSnapshotCodec.encode(snapshot)
        val decoded = WearSnapshotCodec.decode(encoded)
        assertNotNull(decoded)
        assertEquals(snapshot, decoded)
    }

    @Test
    fun punchResult_roundTrip() {
        val result = PunchResult(success = false, errorCode = "not_signed_in")
        val decoded = WearSnapshotCodec.decodePunchResult(WearSnapshotCodec.encodePunchResult(result))
        assertNotNull(decoded)
        assertEquals(result, decoded)
    }

    @Test
    fun punchCommand_roundTripAndEmptyPayload() {
        val command = WearPunchCommand(epochMillis = 1_700_000_000_000L, userId = "user-1")
        val decoded = WearSnapshotCodec.decodePunchCommand(WearSnapshotCodec.encodePunchCommand(command))
        assertEquals(command, decoded)
        assertEquals(null, WearSnapshotCodec.decodePunchCommand(ByteArray(0)))
    }

    @Test
    fun punchLog_roundTrip() {
        val log = WearPunchEventLog(
            events = listOf(
                WearPunchEvent(id = "e1", isPunchIn = true, epochMillis = 1L),
                WearPunchEvent(id = "e2", isPunchIn = false, epochMillis = 2L),
            ),
        )
        val decoded = WearSnapshotCodec.decodePunchLog(WearSnapshotCodec.encodePunchLog(log))
        assertEquals(log, decoded)
    }

    @Test
    fun corruptPunchLog_returnsNullInsteadOfEmptyQueue() {
        assertNull(WearSnapshotCodec.decodePunchLog("{not-json"))
    }
}
