package com.elmtrackr.app.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearConnectionStatusTest {

    @Test
    fun `unavailable reports no watches and no api`() {
        val status = WearConnectionStatus.unavailable()
        assertFalse(status.wearApiAvailable)
        assertFalse(status.anyWatchConnected)
        assertNull(status.featuredWatch)
    }

    @Test
    fun `node with the capability counts as app installed`() {
        val status = WearConnectionStatus.from(
            connectedNodes = listOf(Triple("a", "Pixel Watch", true)),
            capabilityNodeIds = setOf("a"),
        )
        assertTrue(status.anyWatchWithApp)
        assertTrue(status.watches.single().appInstalled)
    }

    @Test
    fun `node without the capability is connected but app-less`() {
        val status = WearConnectionStatus.from(
            connectedNodes = listOf(Triple("a", "Galaxy Watch", true)),
            capabilityNodeIds = emptySet(),
        )
        assertTrue(status.anyWatchConnected)
        assertFalse(status.anyWatchWithApp)
    }

    @Test
    fun `watches sort app-first then nearby-first`() {
        val status = WearConnectionStatus.from(
            connectedNodes = listOf(
                Triple("far-noapp", "Zeta", false),
                Triple("near-noapp", "Alpha", true),
                Triple("far-app", "Omega", false),
            ),
            capabilityNodeIds = setOf("far-app"),
        )
        assertEquals(listOf("far-app", "near-noapp", "far-noapp"), status.watches.map { it.id })
    }

    @Test
    fun `featured watch prefers app plus nearby, then app, then nearby`() {
        val appAndNearby = WearConnectionStatus.from(
            connectedNodes = listOf(
                Triple("a", "A", false),
                Triple("b", "B", true),
            ),
            capabilityNodeIds = setOf("a", "b"),
        )
        assertEquals("b", appAndNearby.featuredWatch?.id)

        val appOnly = WearConnectionStatus.from(
            connectedNodes = listOf(
                Triple("a", "A", false),
                Triple("b", "B", true),
            ),
            capabilityNodeIds = setOf("a"),
        )
        assertEquals("a", appOnly.featuredWatch?.id)

        val nearbyOnly = WearConnectionStatus.from(
            connectedNodes = listOf(
                Triple("a", "A", false),
                Triple("b", "B", true),
            ),
            capabilityNodeIds = emptySet(),
        )
        assertEquals("b", nearbyOnly.featuredWatch?.id)
    }
}
