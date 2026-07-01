package com.elmtrackr.app.ui.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalHapticFeedback

@Composable
fun Modifier.auroraRowClickable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    haptic: HapticFeedback = LocalHapticFeedback.current,
    hapticAction: (HapticFeedback) -> Unit = AuroraHaptics::navigationTap,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this
        .auroraPressScale(interactionSource)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = {
                hapticAction(haptic)
                onClick()
            },
        )
}
