package com.elmtrackr.app.ui.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role

/**
 * A tappable row with the app's press scale in place of a ripple.
 *
 * [role] defaults to [Role.Button]. It used to be absent, so TalkBack read the
 * twelve rows built on this — settings entries, sync retries, the task and leave
 * pickers — as plain text with "double-tap to activate" and nothing saying what
 * kind of control it was. Pass [onClickLabel] where "activate" undersells the
 * action, and `null` for [role] only when the caller sets a more specific one.
 */
@Composable
fun Modifier.auroraRowClickable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
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
            onClickLabel = onClickLabel,
            role = role,
            onClick = {
                hapticAction(haptic)
                onClick()
            },
        )
}
