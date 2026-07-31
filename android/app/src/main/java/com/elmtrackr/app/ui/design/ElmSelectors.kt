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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.ui.theme.AuroraIndigo

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
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(3.dp)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val shape = RoundedCornerShape(9.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (selected) {
                            Modifier
                                .shadow(
                                    elevation = 2.dp,
                                    shape = shape,
                                    clip = false,
                                    ambientColor = AuroraIndigo.copy(alpha = 0.10f),
                                    spotColor = AuroraIndigo.copy(alpha = 0.35f),
                                )
                                .background(MaterialTheme.colorScheme.surface, shape)
                        } else {
                            Modifier
                        },
                    )
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = {
                            AuroraHaptics.toggle(haptic)
                            onSelect(index)
                        },
                    )
                    .defaultMinSize(minHeight = 40.dp)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
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
    val shape = RoundedCornerShape(10.dp)
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
            .defaultMinSize(minHeight = 40.dp)
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
