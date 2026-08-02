package com.elmtrackr.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.Elevation
import com.elmtrackr.app.ui.theme.auroraShadow

/**
 * Pill-style segmented control from the Aurora reference: a tinted track with
 * the selected segment lifted on a white pill.
 *
 * Each segment is a radio-style selectable so a screen reader announces
 * "selected, one of two" the same way the Material segmented button did.
 */
@Composable
fun ElmSegmentedPillRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Per-option enabled flags; null means every option is selectable. */
    enabledOptions: List<Boolean>? = null,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(CornerRadius.Small))
            .padding(3.dp)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val enabled = enabledOptions?.getOrNull(index) ?: true
            val shape = RoundedCornerShape(CornerRadius.SegmentPill)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (selected) {
                            Modifier
                                .auroraShadow(Elevation.Selector, shape)
                                .background(MaterialTheme.colorScheme.surface, shape)
                        } else {
                            Modifier
                        },
                    )
                    .selectable(
                        selected = selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = {
                            AuroraHaptics.toggle(haptic)
                            onSelect(index)
                        },
                    )
                    .defaultMinSize(minHeight = 48.dp)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                val ink = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (enabled) ink else ink.copy(alpha = 0.38f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Aurora choice chip: hairline border, quiet at rest, primary-container tint
 * when selected — the CHIP_ON / CHIP_OFF pair from the reference frames.
 */
@Composable
fun ElmChoiceChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(CornerRadius.Chip)
    Row(
        modifier = modifier
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                },
                shape = shape,
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .selectable(
                selected = selected,
                role = Role.Button,
                onClick = {
                    AuroraHaptics.toggle(haptic)
                    onClick()
                },
            )
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Two-line chip body used for project pickers: name over a quieter subtitle. */
@Composable
fun ElmChipLabel(
    title: String,
    subtitle: String?,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
