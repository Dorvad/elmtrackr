package com.elmtrackr.app.ui.shifts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

@Composable
fun ShiftsSkeleton(modifier: Modifier = Modifier) {
    val shimmer = rememberShimmerBrush()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        ShimmerBlock(modifier = Modifier.fillMaxWidth(), height = 140.dp, corner = CornerRadius.Large, brush = shimmer)
        repeat(2) {
            ShimmerBlock(modifier = Modifier.fillMaxWidth(), height = 180.dp, corner = CornerRadius.Medium, brush = shimmer)
        }
        ShimmerBlock(modifier = Modifier.fillMaxWidth(), height = 52.dp, corner = CornerRadius.Large, brush = shimmer)
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
