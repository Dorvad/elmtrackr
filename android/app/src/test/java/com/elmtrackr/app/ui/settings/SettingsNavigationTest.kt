package com.elmtrackr.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The navigation rules that decide whether a chosen clock face survives long
 * enough to be saved.
 *
 * Both were wrong at once, and together they made the fifteen faces outside the
 * quick picks impossible to apply: the gallery returned to the hub instead of the
 * appearance screen that carries the Save bar, and the hop between the two screens
 * counted as abandoning a detail screen, so the tap that chose a face raised a
 * prompt offering to discard it.
 */
class SettingsNavigationTest {

    @Test
    fun `the gallery returns to the screen that saves the face`() {
        assertEquals(
            SettingsDestination.APPEARANCE,
            SettingsDestination.CLOCK_FACES.backDestination(),
        )
    }

    @Test
    fun `the hub is the end of the road`() {
        assertEquals(null, SettingsDestination.HUB.backDestination())
    }

    @Test
    fun `moving between the appearance screen and the gallery is one edit`() {
        assertTrue(
            sharesPendingEdits(SettingsDestination.APPEARANCE, SettingsDestination.CLOCK_FACES),
        )
        assertTrue(
            sharesPendingEdits(SettingsDestination.CLOCK_FACES, SettingsDestination.APPEARANCE),
        )
    }

    /**
     * The guard still has to fire on the way out, or a chosen face disappears with
     * nothing said about it.
     */
    @Test
    fun `leaving the clock face screens is not`() {
        assertFalse(sharesPendingEdits(SettingsDestination.CLOCK_FACES, SettingsDestination.HUB))
        assertFalse(sharesPendingEdits(SettingsDestination.APPEARANCE, SettingsDestination.HUB))
        assertFalse(
            sharesPendingEdits(SettingsDestination.APPEARANCE, SettingsDestination.FEATURES),
        )
        assertFalse(sharesPendingEdits(SettingsDestination.HUB, SettingsDestination.APPEARANCE))
    }

    /**
     * Every destination reachable from the settings hub needs a way back, or the
     * only exit is the system back gesture.
     */
    @Test
    fun `every destination but the hub has a parent`() {
        SettingsDestination.entries
            .filter { it != SettingsDestination.HUB }
            .forEach { destination ->
                assertTrue(
                    "${destination.name} has no back destination",
                    destination.backDestination() != null,
                )
            }
    }
}
