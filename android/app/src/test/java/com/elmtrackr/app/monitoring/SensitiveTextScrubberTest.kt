package com.elmtrackr.app.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What may and may not leave the device inside a crash report.
 *
 * Each case is a message this app can genuinely produce. The rule the tests enforce is
 * the same one the scrubber documents: remove the values, keep the shape, because the
 * shape is what makes the report diagnostic.
 */
class SensitiveTextScrubberTest {

    private fun scrub(s: String) = SensitiveTextScrubber.scrub(s)!!

    @Test
    fun `a Postgres unique violation loses the row but keeps the constraint`() {
        val actual = scrub(
            "duplicate key value violates unique constraint \"shifts_user_id_start_time_key\"; " +
                "Key (user_id, start_time)=(3f2b8a10-4c5d-4e6f-8a9b-0c1d2e3f4a5b, " +
                "2026-07-11 06:00:00+00) already exists.",
        )
        assertTrue(actual.contains("shifts_user_id_start_time_key"))
        assertTrue(actual.contains("Key (user_id, start_time)=([redacted])"))
        assertFalse(actual.contains("3f2b8a10"))
        assertFalse(actual.contains("2026-07-11 06:00:00"))
    }

    @Test
    fun `a failing-row detail is emptied`() {
        val actual = scrub("Failing row contains (1, 3f2b8a10-4c5d-4e6f-8a9b-0c1d2e3f4a5b, 62.50).")
        assertFalse(actual.contains("62.50"))
        assertFalse(actual.contains("3f2b8a10"))
        assertTrue(actual.contains("Failing row contains ([redacted])"))
    }

    @Test
    fun `a JWT never survives, in a header or on its own`() {
        val jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NSJ9.dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        assertEquals("[redacted]", scrub(jwt))
        assertEquals("Authorization: Bearer [redacted]", scrub("Authorization: Bearer $jwt"))
    }

    @Test
    fun `api keys and tokens are stripped from a URL`() {
        val actual = scrub("GET https://abc.supabase.co/rest/v1/shifts?apikey=sb_secret_9f3&select=*")
        assertFalse(actual.contains("sb_secret_9f3"))
        assertTrue(actual.contains("apikey=[redacted]"))
        // The path still says which endpoint failed.
        assertTrue(actual.contains("/rest/v1/shifts"))
    }

    @Test
    fun `an email address does not travel`() {
        val actual = scrub("Invalid login credentials for worker.name+tag@example.co.uk")
        assertFalse(actual.contains("example.co.uk"))
        assertTrue(actual.startsWith("Invalid login credentials for"))
    }

    @Test
    fun `bare user ids are removed wherever they appear`() {
        val actual = scrub("no rows returned for user 3F2B8A10-4C5D-4E6F-8A9B-0C1D2E3F4A5B")
        assertEquals("no rows returned for user [redacted]", actual)
    }

    @Test
    fun `an ordinary message is left exactly as it was`() {
        // Over-redaction costs debuggability, so the common case must be untouched.
        val message = "Shift 'active' not found; SQLiteConstraintException at ShiftDao.update line 42"
        assertEquals(message, scrub(message))
    }

    @Test
    fun `hours and money are not identifiers and stay readable`() {
        val message = "expected 516 payable minutes, got 540 (gross 1234.56 ILS)"
        assertEquals(message, scrub(message))
    }

    @Test
    fun `null and empty pass straight through`() {
        assertNull(SensitiveTextScrubber.scrub(null))
        assertEquals("", SensitiveTextScrubber.scrub(""))
    }
}
