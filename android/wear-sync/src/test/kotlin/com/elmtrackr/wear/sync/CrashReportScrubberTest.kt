package com.elmtrackr.wear.sync

import io.sentry.Breadcrumb
import io.sentry.SentryEvent
import io.sentry.protocol.Message
import io.sentry.protocol.Request
import io.sentry.protocol.SentryException
import io.sentry.protocol.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What may and may not leave the device inside a Sentry event.
 *
 * [SensitiveTextScrubberTest] covers the text rules. These cover the fields those rules
 * are applied to, which is the half that was missing: every case here is a real field
 * of a real event that used to travel untouched.
 */
class CrashReportScrubberTest {

    private val userId = "3f2b8a10-4c5d-4e6f-8a9b-0c1d2e3f4a5b"

    @Test
    fun `an http breadcrumb keeps the endpoint and loses the query`() {
        val breadcrumb = Breadcrumb().apply {
            type = "http"
            category = "http"
            setData("url", "https://abc.supabase.co/rest/v1/shifts?user_id=eq.$userId&select=*")
            setData("method", "GET")
            setData("status_code", 500)
        }

        CrashReportScrubber.scrub(breadcrumb)

        val url = breadcrumb.getData("url") as String
        assertTrue("the endpoint is the diagnostic and stays", url.startsWith("https://abc.supabase.co/rest/v1/shifts"))
        assertFalse("the user id does not travel", url.contains(userId))
        assertFalse("nor does anything else in the query", url.contains("select="))
        assertEquals("GET", breadcrumb.getData("method"))
        assertEquals("non-text data is left alone", 500, breadcrumb.getData("status_code"))
    }

    @Test
    fun `a storage path loses its identifiers even with no query string`() {
        val breadcrumb = Breadcrumb().apply {
            setData("url", "https://abc.supabase.co/storage/v1/object/receipts/$userId/receipt.jpg")
        }

        CrashReportScrubber.scrub(breadcrumb)

        val url = breadcrumb.getData("url") as String
        assertFalse(url.contains(userId))
        assertTrue("the bucket still says which upload failed", url.contains("/storage/v1/object/receipts/"))
    }

    @Test
    fun `a query breadcrumb value is dropped whole`() {
        val breadcrumb = Breadcrumb().apply {
            setData("http.query", "workplace_name=eq.Acme%20Warehouse")
        }

        CrashReportScrubber.scrub(breadcrumb)

        assertEquals("[redacted]", breadcrumb.getData("http.query"))
    }

    @Test
    fun `a breadcrumb message is still scrubbed`() {
        val breadcrumb = Breadcrumb().apply { message = "sync failed for worker@example.com" }

        CrashReportScrubber.scrub(breadcrumb)

        assertEquals("sync failed for [redacted]", breadcrumb.message)
    }

    @Test
    fun `the request context keeps the endpoint and nothing else`() {
        val event = SentryEvent().apply {
            request = Request().apply {
                url = "https://abc.supabase.co/rest/v1/shifts"
                method = "PATCH"
                queryString = "id=eq.$userId"
                cookies = "sb-access-token=abc123"
                headers = mapOf("apikey" to "sb_secret_9f3", "Authorization" to "Bearer abc.def.ghi")
                data = """{"notes":"late shift, covered for Dana"}"""
                fragment = "top"
            }
        }

        CrashReportScrubber.scrub(event)

        val request = event.request!!
        assertEquals("https://abc.supabase.co/rest/v1/shifts", request.url)
        assertEquals("the method is shape, not content", "PATCH", request.method)
        assertEquals("[redacted]", request.queryString)
        assertNull(request.cookies)
        assertNull(request.headers)
        assertNull(request.data)
        assertNull(request.fragment)
    }

    @Test
    fun `an exception value and the event message are scrubbed`() {
        val event = SentryEvent().apply {
            message = Message().apply {
                formatted = "insert failed for $userId"
                message = "insert failed for %s"
                params = listOf(userId)
            }
            exceptions = listOf(
                SentryException().apply {
                    type = "PostgrestRestException"
                    value = "Key (user_id, start_time)=($userId, 2026-07-11 06:00:00+00) already exists."
                },
            )
        }

        CrashReportScrubber.scrub(event)

        assertEquals("insert failed for [redacted]", event.message!!.formatted)
        assertEquals(listOf("[redacted]"), event.message!!.params)
        val value = event.exceptions!!.single().value!!
        assertTrue("the constraint is the diagnostic", value.contains("Key (user_id, start_time)=([redacted])"))
        assertFalse(value.contains(userId))
    }

    @Test
    fun `an identifiable user is reduced to the install id`() {
        val event = SentryEvent().apply {
            user = User().apply {
                id = "installation-id-kept"
                email = "worker@example.com"
                username = "worker"
                ipAddress = "203.0.113.7"
            }
        }

        CrashReportScrubber.scrub(event)

        val user = event.user!!
        assertEquals("installation-id-kept", user.id)
        assertNull(user.email)
        assertNull(user.username)
        assertNull(user.ipAddress)
    }

    @Test
    fun `extras are scrubbed`() {
        val event = SentryEvent().apply {
            setExtra("last_synced_shift", userId)
            setExtra("pending_count", 4)
        }

        CrashReportScrubber.scrub(event)

        assertEquals("[redacted]", event.getExtra("last_synced_shift"))
        assertEquals(4, event.getExtra("pending_count"))
    }

    @Test
    fun `an empty event is left alone rather than thrown at`() {
        val event = SentryEvent()

        CrashReportScrubber.scrub(event)

        assertNull(event.message)
        assertNull(event.request)
    }
}
