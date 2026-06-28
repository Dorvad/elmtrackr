package com.elmtrackr.wear.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
}
