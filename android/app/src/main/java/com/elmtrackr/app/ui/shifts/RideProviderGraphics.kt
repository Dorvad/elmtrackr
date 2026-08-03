package com.elmtrackr.app.ui.shifts

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.elmtrackr.app.ui.theme.CornerRadius
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius as GeometryCornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.RefundProvider
import com.elmtrackr.app.ui.design.auroraMotionEnabled
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class RidePalette(
    val skyTop: Color,
    val skyBottom: Color,
    val far: Color,
    val near: Color,
    val road: Color,
    val vehicle: Color,
    val accent: Color,
)

@Composable
internal fun RideProviderSelector(
    selected: RefundProvider,
    onSelect: (RefundProvider) -> Unit,
) {
    Column {
        Text(stringResource(R.string.shifts_ride_provider), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(RefundProvider.entries.size) { index ->
                val provider = RefundProvider.entries[index]
                RideProviderCard(provider, provider == selected) { onSelect(provider) }
            }
        }
    }
}

@Composable
private fun RideProviderCard(
    provider: RefundProvider,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // Two infinite transitions that ran regardless of the reduce-motion setting.
    // Held at rest they still read correctly: `travel` starts the road at its
    // origin and `bob` sits the vehicle level rather than mid-bounce.
    val motionEnabled = auroraMotionEnabled()
    val travel: Float
    val bob: Float
    if (motionEnabled) {
        val transition = rememberInfiniteTransition(label = "ride-${provider.name}")
        val animatedTravel by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(if (selected) 900 else 1500)),
            label = "road",
        )
        val animatedBob by transition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(if (selected) 850 else 1200), RepeatMode.Reverse),
            label = "bob",
        )
        travel = animatedTravel
        bob = animatedBob
    } else {
        travel = 0f
        bob = 0f
    }
    val palette = ridePalette(provider)
    val cardContentDescription = if (selected) {
        stringResource(R.string.shifts_ride_provider_a11y_selected, provider.label)
    } else {
        stringResource(R.string.shifts_ride_provider_a11y, provider.label)
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(238.dp)
            .height(154.dp)
            .semantics { contentDescription = cardContentDescription },
        shape = RoundedCornerShape(CornerRadius.Large),
        border = BorderStroke(
            if (selected) 3.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                drawRideScene(provider, palette, travel, bob)
            }
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .background(Color.White.copy(alpha = .9f), RoundedCornerShape(50))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                ) {
                    Text(
                        provider.label,
                        color = providerLabelColor(provider),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                if (selected) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier.size(20.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✓", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private val RefundProvider.label: String
    get() = name.lowercase().replaceFirstChar(Char::uppercase)

private fun ridePalette(provider: RefundProvider): RidePalette = when (provider) {
    RefundProvider.LIME -> RidePalette(
        Color(0xFFEAFBE8), Color(0xFFCFF3CB), Color(0xFFA9E59E), Color(0xFF8DD680),
        Color(0xFF3E9A30), Color(0xFFF4F6F4), Color(0xFF3FC81E),
    )
    RefundProvider.DOTT -> RidePalette(
        Color(0xFFE9F1FE), Color(0xFFCFE0FA), Color(0xFFA8C4F0), Color(0xFF86A9E6),
        Color(0xFF6F8CB8), Color(0xFF2E6FD6), Color(0xFFE0162B),
    )
    RefundProvider.BIRD -> RidePalette(
        Color(0xFFE4F4FC), Color(0xFFC4E2F0), Color(0xFFA0C8E4), Color(0xFF78AEC8),
        Color(0xFF448EB0), Color(0xFFD8D9DC), Color(0xFF00A3FF),
    )
    RefundProvider.TAXI -> RidePalette(
        Color(0xFFC4CAD6), Color(0xFFB2BAC8), Color(0xFF7A8298), Color(0xFF596174),
        Color(0xFF424957), Color.White, Color(0xFFFFD000),
    )
    RefundProvider.OTHER -> RidePalette(
        Color(0xFFEDE9FE), Color(0xFFDDE7FA), Color(0xFFB9B1E8), Color(0xFF9187CE),
        Color(0xFF655C94), Color(0xFF5B4DF2), Color(0xFF26C6DA),
    )
}

private fun providerLabelColor(provider: RefundProvider): Color = when (provider) {
    RefundProvider.LIME -> Color(0xFF1E7A10)
    RefundProvider.DOTT -> Color(0xFF1B4FA8)
    RefundProvider.BIRD -> Color(0xFF006BBF)
    RefundProvider.TAXI -> Color(0xFF6B5400)
    RefundProvider.OTHER -> Color(0xFF4133C8)
}

private fun DrawScope.drawRideScene(
    provider: RefundProvider,
    palette: RidePalette,
    travel: Float,
    bob: Float,
) {
    drawRect(palette.skyTop)
    drawRect(
        palette.skyBottom,
        topLeft = Offset(0f, size.height * .42f),
        size = Size(size.width, size.height * .58f),
    )

    val cloudOffset = (travel * size.width * .55f) % (size.width + 70f)
    drawCloud(size.width - cloudOffset, size.height * .22f)
    drawCloud(size.width * .42f - cloudOffset, size.height * .36f, .7f)

    repeat(5) { index ->
        val x = ((index * size.width / 3f) - travel * size.width * .32f) % (size.width * 1.65f)
        val left = if (x < -size.width * .25f) x + size.width * 1.65f else x
        drawOval(
            palette.far,
            topLeft = Offset(left - 35f, size.height * (.43f + (index % 2) * .06f)),
            size = Size(size.width * .48f, size.height * .42f),
        )
    }
    repeat(4) { index ->
        val x = ((index * size.width / 2.4f) - travel * size.width * .55f) % (size.width * 1.55f)
        val left = if (x < -size.width * .3f) x + size.width * 1.55f else x
        drawOval(
            palette.near,
            topLeft = Offset(left - 45f, size.height * .56f),
            size = Size(size.width * .55f, size.height * .34f),
        )
    }

    val roadTop = size.height * .74f
    drawRect(palette.road, Offset(0f, roadTop), Size(size.width, size.height - roadTop))
    drawLine(Color.White.copy(alpha = .55f), Offset(0f, roadTop), Offset(size.width, roadTop), 2.5f)
    val dashWidth = size.width * .13f
    val dashGap = dashWidth * 1.9f
    val dashOffset = travel * dashGap
    var x = -dashGap - dashOffset
    while (x < size.width + dashGap) {
        drawLine(
            Color.White.copy(alpha = .9f),
            Offset(x, roadTop + (size.height - roadTop) * .55f),
            Offset(x + dashWidth, roadTop + (size.height - roadTop) * .55f),
            4f,
            StrokeCap.Round,
        )
        x += dashGap
    }

    val vehicleCenter = Offset(size.width * .53f, roadTop - 4f + bob * 2.5f)
    when (provider) {
        RefundProvider.TAXI -> drawTaxi(vehicleCenter, palette, travel)
        RefundProvider.OTHER -> drawOtherRide(vehicleCenter, palette, travel)
        else -> drawScooter(vehicleCenter, palette, travel)
    }
}

private fun DrawScope.drawCloud(x: Float, y: Float, scale: Float = 1f) {
    val color = Color.White.copy(alpha = .78f)
    drawOval(color, Offset(x, y), Size(52f * scale, 16f * scale))
    drawCircle(color, 13f * scale, Offset(x + 16f * scale, y + 2f * scale))
    drawCircle(color, 10f * scale, Offset(x + 33f * scale, y + 5f * scale))
}

private fun DrawScope.drawWheel(center: Offset, radius: Float, spin: Float, accent: Color) {
    drawCircle(Color(0xFF222228), radius, center)
    drawCircle(accent, radius * .6f, center)
    repeat(4) { index ->
        val angle = spin * PI * 2 + index * PI / 4
        val delta = Offset(cos(angle).toFloat() * radius * .52f, sin(angle).toFloat() * radius * .52f)
        drawLine(Color.White.copy(alpha = .5f), center - delta, center + delta, 1.8f, StrokeCap.Round)
    }
    drawCircle(Color(0xFFD7DBE5), radius * .16f, center)
}

private fun DrawScope.drawScooter(center: Offset, palette: RidePalette, travel: Float) {
    val wheelRadius = size.minDimension * .085f
    val back = Offset(center.x - size.width * .16f, center.y)
    val front = Offset(center.x + size.width * .15f, center.y)
    drawOval(Color.Black.copy(alpha = .2f), Offset(back.x - wheelRadius, center.y + wheelRadius), Size(front.x - back.x + wheelRadius * 2, wheelRadius * .55f))
    drawWheel(back, wheelRadius, travel * 2f, palette.accent)
    drawWheel(front, wheelRadius, travel * 2f, palette.accent)
    val deckY = center.y - wheelRadius * .45f
    drawLine(palette.vehicle, Offset(back.x, deckY), Offset(front.x - wheelRadius * .15f, deckY), wheelRadius * .65f, StrokeCap.Round)
    drawLine(Color(0xFF2C2E33), Offset(back.x + wheelRadius * .2f, deckY - 2f), Offset(front.x - wheelRadius * .45f, deckY - 2f), 3f, StrokeCap.Round)
    val stemBottom = Offset(front.x - wheelRadius * .25f, deckY)
    val stemTop = Offset(front.x - size.width * .08f, center.y - size.height * .37f)
    drawLine(palette.vehicle, stemBottom, stemTop, wheelRadius * .55f, StrokeCap.Round)
    drawLine(Color(0xFF2C2E33), Offset(stemTop.x - wheelRadius, stemTop.y), Offset(stemTop.x + wheelRadius * .7f, stemTop.y + 1f), wheelRadius * .35f, StrokeCap.Round)
    drawRoundRect(
        palette.accent,
        Offset(stemTop.x + 2f, stemTop.y + wheelRadius * .8f),
        Size(wheelRadius * 1.05f, wheelRadius * 1.65f),
        GeometryCornerRadius(wheelRadius * .35f),
    )
}

private fun DrawScope.drawTaxi(center: Offset, palette: RidePalette, travel: Float) {
    val wheelRadius = size.minDimension * .08f
    val back = Offset(center.x - size.width * .16f, center.y)
    val front = Offset(center.x + size.width * .16f, center.y)
    val bodyLeft = back.x - wheelRadius * .8f
    val bodyTop = center.y - size.height * .25f
    drawOval(Color.Black.copy(alpha = .22f), Offset(bodyLeft, center.y + wheelRadius), Size(front.x - back.x + wheelRadius * 3f, wheelRadius * .55f))
    drawRoundRect(palette.vehicle, Offset(bodyLeft, bodyTop), Size(front.x - back.x + wheelRadius * 2f, size.height * .22f), GeometryCornerRadius(9f))
    drawRoundRect(Color(0xFFAACCE0), Offset(back.x, bodyTop - size.height * .11f), Size(front.x - back.x, size.height * .13f), GeometryCornerRadius(8f))
    drawLine(Color.White, Offset(center.x, bodyTop - size.height * .1f), Offset(center.x, bodyTop), 3f)
    drawRoundRect(palette.accent, Offset(center.x - 22f, bodyTop - size.height * .15f), Size(44f, 14f), GeometryCornerRadius(4f))
    drawWheel(back, wheelRadius, travel * 2.2f, Color(0xFF4A4A52))
    drawWheel(front, wheelRadius, travel * 2.2f, Color(0xFF4A4A52))
}

private fun DrawScope.drawOtherRide(center: Offset, palette: RidePalette, travel: Float) {
    val radius = size.minDimension * .075f
    val back = Offset(center.x - size.width * .14f, center.y)
    val front = Offset(center.x + size.width * .14f, center.y)
    drawWheel(back, radius, travel * 1.8f, palette.accent)
    drawWheel(front, radius, travel * 1.8f, palette.accent)
    drawLine(palette.vehicle, back, Offset(center.x, center.y - size.height * .18f), 7f, StrokeCap.Round)
    drawLine(palette.vehicle, Offset(center.x, center.y - size.height * .18f), front, 7f, StrokeCap.Round)
    drawLine(palette.vehicle, back, front, 6f, StrokeCap.Round)
    drawCircle(palette.accent, radius * .7f, Offset(center.x, center.y - size.height * .25f))
}
