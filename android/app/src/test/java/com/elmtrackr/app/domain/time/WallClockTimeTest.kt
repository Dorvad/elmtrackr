package com.elmtrackr.app.domain.time

import com.elmtrackr.app.domain.compensation.CompensationRulesCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wall-clock strings in `rules_json` reach the pay engines, and used to be
 * parsed with `time.split(":")[0].toInt()` — which throws on anything that is not
 * two integers around a colon, from inside a pay calculation, on a value that
 * arrives over the wire.
 *
 * The settings screen validates before it will save one, but the server is not
 * the settings screen, and the contract's rule is that nothing crashes on synced
 * data. `CompensationRulesCodec` read them with `optString` and no validation, so
 * a malformed value in a synced profile got through intact.
 */
class WallClockTimeTest {

    @Test
    fun `parses the shapes the app writes`() {
        assertEquals(0, WallClockTime.parseMinutesOfDayOrNull("00:00"))
        assertEquals(22 * 60, WallClockTime.parseMinutesOfDayOrNull("22:00"))
        assertEquals(17 * 60, WallClockTime.parseMinutesOfDayOrNull("17:00"))
        assertEquals(23 * 60 + 59, WallClockTime.parseMinutesOfDayOrNull("23:59"))
        // A single-digit hour is what the settings field's own regex allows.
        assertEquals(6 * 60 + 30, WallClockTime.parseMinutesOfDayOrNull("6:30"))
        assertEquals(6 * 60, WallClockTime.parseMinutesOfDayOrNull(" 06:00 "))
    }

    @Test
    fun `answers null rather than throwing on anything else`() {
        listOf(
            null, "", "   ", "22", "22:", ":00", "22:60", "24:00", "25:00",
            "-1:00", "22:0", "22:000", "ten o'clock", "22:00:00", "22.00", "22h00",
        ).forEach { assertNull("expected null for <$it>", WallClockTime.parseMinutesOfDayOrNull(it)) }
    }

    @Test
    fun `the boundary values are the real ones`() {
        // 23:59 is valid and 24:00 is not — a day has 1,440 minutes and the last
        // one is 1,439.
        assertTrue(WallClockTime.isValid("23:59"))
        assertFalse(WallClockTime.isValid("24:00"))
        assertEquals(1_439, WallClockTime.parseMinutesOfDayOrNull("23:59"))
    }

    /**
     * The codec drops an unreadable value on the way in, so a stored one that can
     * never parse does not have to be defended against on every shift.
     */
    @Test
    fun `a malformed rest start decodes to no boundary`() {
        val rules = CompensationRulesCodec.decode(
            """{"weekend":{"restStartTime":"nonsense"}}""",
        )

        assertNull(rules.weeklyRestStartTime)
    }

    @Test
    fun `a malformed night window decodes to the shipped default`() {
        val rules = CompensationRulesCodec.decode(
            """{"night":{"startTime":"99:99","endTime":""}}""",
        )

        assertEquals("22:00", rules.nightStartTime)
        assertEquals("06:00", rules.nightEndTime)
    }

    @Test
    fun `a valid rest start survives the codec`() {
        val rules = CompensationRulesCodec.decode(
            """{"weekend":{"restStartTime":"17:00"}}""",
        )

        assertEquals("17:00", rules.weeklyRestStartTime)
    }
}
