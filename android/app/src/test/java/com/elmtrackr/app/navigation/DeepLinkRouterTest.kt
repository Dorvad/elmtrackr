package com.elmtrackr.app.navigation

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkRouterTest {

    @Test
    fun `starts with nothing pending`() = runTest {
        assertNull(DeepLinkRouter().pending.value)
    }

    @Test
    fun `submitted link stays pending until consumed`() = runTest {
        val router = DeepLinkRouter()
        router.submit("elmtrackr://projects")
        assertEquals("elmtrackr://projects", router.pending.value)

        router.consume("elmtrackr://projects")
        assertNull(router.pending.value)
    }

    @Test
    fun `consuming a stale link does not drop a newer one`() = runTest {
        val router = DeepLinkRouter()
        router.submit("elmtrackr://projects/1")
        router.submit("elmtrackr://projects/2")

        router.consume("elmtrackr://projects/1")

        assertEquals("elmtrackr://projects/2", router.pending.value)
    }
}
