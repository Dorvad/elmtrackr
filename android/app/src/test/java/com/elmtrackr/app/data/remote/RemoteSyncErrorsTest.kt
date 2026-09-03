package com.elmtrackr.app.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteSyncErrorsTest {

    @Test
    fun `detects missing tasks table from PGRST205`() {
        val error = RuntimeException(
            "Could not find the table 'public.tasks' in the schema cache (PGRST205)",
        )
        assertTrue(RemoteSyncErrors.isMissingRemoteTable(error, "tasks"))
    }

    @Test
    fun `ignores unrelated errors`() {
        val error = RuntimeException("permission denied for table shifts")
        assertFalse(RemoteSyncErrors.isMissingRemoteTable(error, "tasks"))
    }

    @Test
    fun `auth expiry matches jwt and refresh token failures`() {
        assertTrue(RemoteSyncErrors.isAuthExpired(RuntimeException("JWT expired")))
        assertTrue(RemoteSyncErrors.isAuthExpired(RuntimeException("PGRST301: JWT invalid")))
        assertTrue(RemoteSyncErrors.isAuthExpired(RuntimeException("Invalid Refresh Token: Already Used")))
        assertTrue(
            RemoteSyncErrors.isAuthExpired(
                RuntimeException("request failed", RuntimeException("invalid JWT: token is expired")),
            ),
        )
    }

    @Test
    fun `auth expiry ignores unrelated failures`() {
        assertFalse(RemoteSyncErrors.isAuthExpired(RuntimeException("connection reset")))
        assertFalse(RemoteSyncErrors.isAuthExpired(RuntimeException("Could not find the table 'public.tasks' (PGRST205)")))
        assertFalse(RemoteSyncErrors.isAuthExpired(RuntimeException(null as String?)))
    }

    // ── Transient vs permanent ────────────────────────────────────────────────
    // Everything unrecognised used to become FAILED, and FAILED is deliberately
    // excluded from the immediate retry path by hasRetryablePendingWork — so a
    // clock-out pushed on a flaky connection waited for the fifteen-minute
    // periodic run and sat in "unsynced changes" until then. A dropped connection
    // is not a rejection.

    @Test
    fun `network failures are transient`() {
        listOf(
            java.net.SocketTimeoutException("timeout"),
            java.net.UnknownHostException("Unable to resolve host \"example.supabase.co\""),
            java.io.IOException("Connection reset"),
            RuntimeException("HTTP 503 Service Unavailable"),
            RuntimeException("HTTP 502 Bad Gateway"),
            RuntimeException("Received status code 429 from server: Too Many Requests"),
        ).forEach {
            assertTrue("expected transient: $it", RemoteSyncErrors.isTransient(it))
        }
    }

    @Test
    fun `a transient failure nested in a cause is still found`() {
        val wrapped = RuntimeException("push failed", java.net.SocketTimeoutException("timeout"))

        assertTrue(RemoteSyncErrors.isTransient(wrapped))
    }

    /**
     * The narrowness is the point.
     *
     * A rejection the server will repeat must stay FAILED. Treating an unknown
     * error as retryable is how a row that can never succeed becomes an endless
     * sync loop — the failure the August work existed to close.
     */
    @Test
    fun `real rejections are not transient`() {
        listOf(
            RuntimeException("""duplicate key value violates unique constraint "shifts_pkey" (23505)"""),
            RuntimeException("""violates foreign key constraint "tasks_compensation_profile_id_fkey" (23503)"""),
            RuntimeException("""new row violates row-level security policy"""),
            RuntimeException("""invalid input syntax for type numeric"""),
            RuntimeException("""JWT expired"""),
        ).forEach {
            assertFalse("expected permanent: $it", RemoteSyncErrors.isTransient(it))
        }
    }
}
