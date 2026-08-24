package com.elmtrackr.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.MaterialTheme

/**
 * Only the four roles the previous theme already set are overridden here.
 *
 * Wear Compose Material 3's `ColorScheme` carries a good deal more than this —
 * the surface-container ladder, `onSurfaceVariant`, `outline` — and it is
 * tempting to map the phone's whole dark scheme onto it. It is not done,
 * because nothing in this app reads those roles: the watch draws on black and
 * colours its own text from [AuroraInk] / [AuroraInk2] / [AuroraOutline]
 * explicitly, so mapping them would add build surface that no pixel depends on.
 * Add a role here when a composable actually reads it.
 */
@Composable
fun WearAuroraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            // Pure black background per the Wear OS quality guidelines (blends
            // with the bezel, saves power on AMOLED). Brand color stays in the
            // accents: the bolt mark, the goal ring, and the status dot.
            background = Color.Black,
            onBackground = AuroraInk,
            primary = AuroraIndigo,
            secondary = AuroraPlum,
        ),
        content = content,
    )
}
