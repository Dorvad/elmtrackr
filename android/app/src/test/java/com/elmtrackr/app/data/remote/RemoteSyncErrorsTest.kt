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
}
