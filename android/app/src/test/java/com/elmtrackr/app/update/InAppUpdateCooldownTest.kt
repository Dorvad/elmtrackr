package com.elmtrackr.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InAppUpdateCooldownTest {

    @Test
    fun `flexible prompt allowed when never dismissed`() {
        assertTrue(InAppUpdateCooldown.shouldOfferFlexiblePrompt(null, now = 1_000L))
    }

    @Test
    fun `flexible prompt blocked during cooldown`() {
        val dismissedAt = 1_000L
        val withinCooldown = dismissedAt + InAppUpdateCooldown.FLEXIBLE_DISMISS_COOLDOWN_MS - 1
        assertFalse(InAppUpdateCooldown.shouldOfferFlexiblePrompt(dismissedAt, withinCooldown))
    }

    @Test
    fun `flexible prompt allowed after cooldown expires`() {
        val dismissedAt = 1_000L
        val afterCooldown = dismissedAt + InAppUpdateCooldown.FLEXIBLE_DISMISS_COOLDOWN_MS
        assertTrue(InAppUpdateCooldown.shouldOfferFlexiblePrompt(dismissedAt, afterCooldown))
    }

    @Test
    fun `play store fallback allowed when never shown`() {
        assertTrue(InAppUpdateCooldown.shouldShowPlayStoreFallback(null, now = 1_000L))
    }

    @Test
    fun `play store fallback blocked during cooldown`() {
        val shownAt = 5_000L
        val withinCooldown = shownAt + InAppUpdateCooldown.PLAY_STORE_FALLBACK_COOLDOWN_MS - 1
        assertFalse(InAppUpdateCooldown.shouldShowPlayStoreFallback(shownAt, withinCooldown))
    }

    @Test
    fun `play store fallback allowed after cooldown expires`() {
        val shownAt = 5_000L
        val afterCooldown = shownAt + InAppUpdateCooldown.PLAY_STORE_FALLBACK_COOLDOWN_MS
        assertTrue(InAppUpdateCooldown.shouldShowPlayStoreFallback(shownAt, afterCooldown))
    }
}
