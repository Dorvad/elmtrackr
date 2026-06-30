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
}
