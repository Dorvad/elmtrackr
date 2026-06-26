package com.elmtrackr.app.ui.reports

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.ui.design.rememberShimmerBrush
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.Spacing

/**
 * Skeleton placeholder for the Reports screen while data loads.
 * Mirrors the tab bar, month navigator, distribution card, and stat grid layout.
 */
@Composable
fun ReportsSkeleton(modifier: Modifier = Modifier) {
    val shimmer = rememberShimmerBrush()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        ShimmerBlock(width = 120.dp, height = 28.dp, corner = CornerRadius.Small, brush = shimmer)

        // Tab bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CornerRadius.Large))
                .background(shimmer)
                .padding(Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            ShimmerBlock(
                modifier = Modifier.weight(1f),
                height = 40.dp,
                corner = CornerRadius.Medium,
                brush = shimmer,
            )
            ShimmerBlock(
                modifier = Modifier.weight(1f),
                height = 40.dp,
                corner = CornerRadius.Medium,
                brush = shimmer,
            )
        }

        // Month navigator
        ShimmerBlock(
            modifier = Modifier.fillMaxWidth(),
            height = 48.dp,
            corner = CornerRadius.Large,
            brush = shimmer,
        )

        // Distribution card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CornerRadius.Large))
                .background(shimmer)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            ShimmerBlock(width = 140.dp, height = 12.dp, corner = CornerRadius.Small, brush = shimmer)
            ShimmerBlock(width = 100.dp, height = 28.dp, corner = CornerRadius.Small, brush = shimmer)
            ShimmerBlock(modifier = Modifier.fillMaxWidth(), height = 10.dp, corner = CornerRadius.Small, brush = shimmer)
            ShimmerBlock(modifier = Modifier.fillMaxWidth(), height = 44.dp, corner = CornerRadius.Medium, brush = shimmer)
        }

        // Stat grid
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ShimmerBlock(modifier = Modifier.weight(1f), height = 72.dp, corner = CornerRadius.Medium, brush = shimmer)
            ShimmerBlock(modifier = Modifier.weight(1f), height = 72.dp, corner = CornerRadius.Medium, brush = shimmer)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ShimmerBlock(modifier = Modifier.weight(1f), height = 72.dp, corner = CornerRadius.Medium, brush = shimmer)
            ShimmerBlock(modifier = Modifier.weight(1f), height = 72.dp, corner = CornerRadius.Medium, brush = shimmer)
        }

        // Weekly section label + card
        ShimmerBlock(width = 160.dp, height = 12.dp, corner = CornerRadius.Small, brush = shimmer)
        ShimmerBlock(modifier = Modifier.fillMaxWidth(), height = 120.dp, corner = CornerRadius.Large, brush = shimmer)

        Spacer(Modifier.height(Spacing.sm))
    }
}

@Composable
private fun ShimmerBlock(
    modifier: Modifier = Modifier,
    width: Dp? = null,
    height: Dp,
    corner: Dp,
    brush: Brush,
) {
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .height(height)
            .clip(RoundedCornerShape(corner))
            .background(brush),
    )
}
