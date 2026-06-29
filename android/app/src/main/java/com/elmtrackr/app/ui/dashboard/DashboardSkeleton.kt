package com.elmtrackr.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.ui.design.rememberShimmerBrush
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.Spacing

/**
 * Skeleton loading screen for the Dashboard.
 *
 * Mimics the layout of DashboardReady with an animated shimmer:
 *   - Header logo row
 *   - Large clock card placeholder
 *   - Two rows of stat card placeholders (2 cards each)
 *   - Recent shifts section header + 3 row placeholders
 *
 * Respects MaterialTheme.colorScheme so it works in light and dark mode.
 */
@Composable
fun DashboardSkeleton(modifier: Modifier = Modifier) {
    val shimmer = rememberShimmerBrush()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md)
            .widthIn(max = 448.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Spacing.lg))

        // Header row (logo placeholder)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShimmerPill(width = 140.dp, height = 24.dp, shape = CornerRadius.Small, brush = shimmer)
        }

        Spacer(Modifier.height(Spacing.lg))

        // Clock card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(CornerRadius.Large))
                .background(shimmer),
        )

        Spacer(Modifier.height(Spacing.md))

        // Stat row 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            ShimmerStatCard(Modifier.weight(1f), brush = shimmer)
            ShimmerStatCard(Modifier.weight(1f), brush = shimmer)
        }

        Spacer(Modifier.height(Spacing.sm))

        // Stat row 2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            ShimmerStatCard(Modifier.weight(1f), brush = shimmer)
            ShimmerStatCard(Modifier.weight(1f), brush = shimmer)
        }

        Spacer(Modifier.height(Spacing.lg))

        // Section label placeholder
        ShimmerPill(
            width = 100.dp, height = 12.dp,
            shape = CornerRadius.Small, brush = shimmer,
            modifier = Modifier.align(Alignment.Start),
        )

        Spacer(Modifier.height(Spacing.sm))

        // Shift row placeholders
        repeat(3) { i ->
            if (i > 0) Spacer(Modifier.height(Spacing.xs))
            ShimmerShiftRow(brush = shimmer)
        }

        Spacer(Modifier.height(Spacing.xl))
    }
}

// ── Skeleton primitives ───────────────────────────────────────────────────────

@Composable
private fun ShimmerPill(
    width: Dp,
    height: Dp,
    shape: Dp,
    brush: Brush,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(shape))
            .background(brush),
    )
}

@Composable
private fun ShimmerStatCard(modifier: Modifier = Modifier, brush: Brush) {
    Box(
        modifier = modifier
            .height(88.dp)
            .clip(RoundedCornerShape(CornerRadius.Large))
            .background(brush),
    )
}

@Composable
private fun ShimmerShiftRow(brush: Brush) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(CornerRadius.Medium))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // Shift-type stripe
        Box(
            Modifier
                .width(4.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(brush),
        )
        // Two text lines
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ShimmerPill(width = 120.dp, height = 12.dp, shape = CornerRadius.Small, brush = brush)
            ShimmerPill(width = 80.dp,  height = 10.dp, shape = CornerRadius.Small, brush = brush)
        }
        // Right value (pay/hours)
        ShimmerPill(width = 56.dp, height = 14.dp, shape = CornerRadius.Small, brush = brush)
    }
}
