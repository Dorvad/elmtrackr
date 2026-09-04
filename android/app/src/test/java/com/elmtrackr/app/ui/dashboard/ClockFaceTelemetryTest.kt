package com.elmtrackr.app.ui.dashboard

import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.ui.settings.ClockFaceGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Stats-for-nerds pack: what its faces read, and what they must never compute.
 */
class ClockFaceTelemetryTest {

    private fun telemetry(elapsed: Int, goal: Int = 480) = ClockFaceTelemetry(
        elapsedMinutes = elapsed,
        goalMinutes = goal,
        earnedText = "$74.50",
        rateText = "$15.00/h",
        targetEarnedText = "$120.00 target",
        earned = 74.5,
        targetEarned = 120.0,
    )

    @Test
    fun `the reference fixture reads 04 colon 58 of eight hours`() {
        // The design document's shift: 62% of an eight-hour goal.
        val t = telemetry(298)
        assertEquals("04:58", t.elapsedClock)
        assertEquals("03:02", t.remainingClock)
        assertEquals("08:00", t.goalClock)
        assertEquals(0.62f, t.progress, 0.005f)
    }

    @Test
    fun `overtime reads no time left rather than a negative clock`() {
        // A shift past its goal is normal, and "-00:30 left" is not a thing a clock says.
        val t = telemetry(510)
        assertEquals("00:00", t.remainingClock)
        assertEquals("08:30", t.elapsedClock)
    }

    @Test
    fun `progress never exceeds one, so a face cannot draw past its own box`() {
        assertEquals(1f, telemetry(900).progress, 1e-6f)
        assertEquals(0f, telemetry(0).progress, 1e-6f)
    }

    @Test
    fun `a zero goal cannot divide by zero`() {
        // Reachable: dailyOvertimeThresholdMinutes is user-editable.
        val t = telemetry(elapsed = 120, goal = 0)
        assertEquals(0f, t.progress, 1e-6f)
        assertEquals(1, t.cellCount)
    }

    @Test
    fun `the grid is twelve cells to the hour, sized from the goal`() {
        assertEquals(96, telemetry(0, goal = 480).cellCount)
        assertEquals(72, telemetry(0, goal = 360).cellCount)
        // 4h58 is 59 whole five-minute cells.
        assertEquals(59, telemetry(298).cellsFilled)
    }

    @Test
    fun `filled cells never exceed the grid`() {
        val t = telemetry(elapsed = 10_000, goal = 480)
        assertEquals(t.cellCount, t.cellsFilled)
    }

    @Test
    fun `the clock pads both fields`() {
        assertEquals("00:05", telemetry(5).elapsedClock)
        assertEquals("01:00", telemetry(60).elapsedClock)
        assertEquals("10:00", telemetry(600).elapsedClock)
    }

    @Test
    fun `only the Stats-for-nerds faces draw their own reading`() {
        // The invariant that stops the dashboard printing the elapsed time twice. Stated
        // over the whole enum rather than the four, so a face added to this pack later
        // without the flag fails here.
        val nerds = ClockFaceGroup.NERDS.faces.map { it.toSupportedOrDefault() }.toSet()
        SupportedClockStyle.entries.forEach { style ->
            assertEquals(
                "$style: drawsOwnReading should be true only for the NERDS pack",
                style in nerds,
                style.drawsOwnReading(),
            )
        }
    }

    @Test
    fun `every face that draws its own reading is in a pack the user must choose`() {
        // These are the only faces that print an amount, so none of them may be bundled:
        // a user who never opted in must not have earnings appear on their dashboard.
        ClockStyle.entries
            .filter { it.toSupportedOrDefault().drawsOwnReading() }
            .forEach { face ->
                assertFalse(
                    "$face prints money and must not be bundled",
                    ClockFaceGroup.of(face).isBundled,
                )
            }
    }

    @Test
    fun `the Stats-for-nerds pack is four faces like every other pack`() {
        assertEquals(ClockFaceGroup.GROUP_SIZE, ClockFaceGroup.NERDS.faces.size)
        assertTrue(ClockFaceGroup.NERDS.faces.containsAll(
            listOf(ClockStyle.READOUT, ClockStyle.SPARKLINE, ClockStyle.GAUGE, ClockStyle.MATRIX),
        ))
    }

    @Test
    fun `the pack carries a version so its New ribbon expires on its own`() {
        assertEquals("1.4.0", ClockFaceGroup.NERDS.since)
        assertTrue(ClockFaceGroup.NERDS.isNewIn("1.4.0"))
        assertTrue(ClockFaceGroup.NERDS.isNewIn("1.5.2"))
        assertFalse("the ribbon must expire without a checklist", ClockFaceGroup.NERDS.isNewIn("1.6.0"))
    }
}
