package com.elmtrackr.app.ui.design

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
 *
 * Segments share the width equally, which only works while every label fits its
 * share on one line. Four options at phone width leave under 50dp of text per
 * segment, and "Multiplicative", "Base-plus-premium" or "Fixed amount per month"
 * were cut off mid-word. So each label is measured first — at the current font
 * scale, in the wider selected weight — and if any would not fit, the same
 * segments are stacked one per line in the same track. Labels that fit keep the
 * row exactly as before.
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
    val labelStyle = MaterialTheme.typography.labelLarge
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val boundedWidth = constraints.hasBoundedWidth
        val stacked = remember(options, maxWidth, labelStyle, density, boundedWidth) {
            if (!boundedWidth || options.isEmpty()) {
                false
            } else {
                val textWidthPx = with(density) {
                    ((maxWidth - SegmentTrackPadding * 2) / options.size - SegmentPaddingHorizontal * 2).toPx()
                }
                options.any { label ->
                    measurer.measure(
                        text = label,
                        style = labelStyle.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        softWrap = false,
                    ).size.width > textWidthPx
                }
            }
        }
        val track = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(CornerRadius.Small))
            .padding(SegmentTrackPadding)
            .selectableGroup()
        val segment: @Composable (Int, String, Modifier) -> Unit = { index, label, segmentModifier ->
            PillSegment(
                label = label,
                selected = index == selectedIndex,
                enabled = enabledOptions?.getOrNull(index) ?: true,
                stacked = stacked,
                onClick = {
                    AuroraHaptics.toggle(haptic)
                    onSelect(index)
                },
                modifier = segmentModifier,
            )
        }
        if (stacked) {
            Column(track, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                options.forEachIndexed { index, label -> segment(index, label, Modifier.fillMaxWidth()) }
            }
        } else {
            Row(track, verticalAlignment = Alignment.CenterVertically) {
                options.forEachIndexed { index, label -> segment(index, label, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PillSegment(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    stacked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(CornerRadius.SegmentPill)
    Box(
        modifier = modifier
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
                onClick = onClick,
            )
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = SegmentPaddingHorizontal, vertical = 10.dp),
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
            // One line in the row, where the width was checked before choosing it;
            // stacked, a full-width line can still wrap rather than clip.
            maxLines = if (stacked) 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val SegmentTrackPadding = 3.dp
private val SegmentPaddingHorizontal = 12.dp

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
