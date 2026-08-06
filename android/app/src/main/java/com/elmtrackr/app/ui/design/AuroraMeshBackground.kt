package com.elmtrackr.app.ui.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraPeach
import com.elmtrackr.app.ui.theme.AuroraPlum
import com.elmtrackr.app.ui.theme.isAuroraDarkTheme
import com.elmtrackr.app.ui.components.motion.HOUR_MILLIS
import com.elmtrackr.app.ui.components.motion.rememberCurrentInstant
import java.time.ZoneId

private enum class AuroraPhase { Dawn, Day, Dusk, Night }

private fun hourToPhase(hour: Int): AuroraPhase = when {
    hour in 5..8 -> AuroraPhase.Dawn
    hour in 9..15 -> AuroraPhase.Day
    hour in 16..19 -> AuroraPhase.Dusk
    else -> AuroraPhase.Night
}

private fun phaseColors(phase: AuroraPhase, dark: Boolean): Triple<Color, Color, Color> = when (phase) {
    AuroraPhase.Dawn -> if (dark) {
        Triple(Color(0xFF2E2680), Color(0xFF6B4DB8), Color(0xFFB86B50))
    } else {
        Triple(Color(0xFF4133C8), Color(0xFF9A6BFF), Color(0xFFFF9E7D))
    }
    AuroraPhase.Day -> if (dark) {
        Triple(Color(0xFF2A2288), AuroraPlum.copy(alpha = 0.9f), AuroraAqua.copy(alpha = 0.85f))
    } else {
        Triple(AuroraIndigo, AuroraPlum, AuroraAqua)
    }
    AuroraPhase.Dusk -> if (dark) {
        Triple(Color(0xFF3A2A90), Color(0xFF8A45C8), Color(0xFFC85A90))
    } else {
        Triple(Color(0xFF6D4AFF), Color(0xFFB14DFF), Color(0xFFFF6FB0))
    }
    AuroraPhase.Night -> if (dark) {
        Triple(Color(0xFF1E1858), Color(0xFF5555AA), Color(0xFF18B0B8))
    } else {
        Triple(Color(0xFF3A2F8F), Color(0xFF7C7CFF), Color(0xFF22D8D0))
    }
}

/**
 * Soft animated aurora mesh matching the web dashboard background.
 */
@Composable
fun AuroraMeshBackground(
    modifier: Modifier = Modifier,
) {
    // Keyed on the ticking hour. `remember {}` with no key read the clock once
    // per composition and then kept that phase for the life of the screen, so a
    // background opened in the afternoon stayed on afternoon colours into the
    // night.
    val hour = rememberCurrentInstant(HOUR_MILLIS).atZone(ZoneId.systemDefault()).hour
    val phase = remember(hour) { hourToPhase(hour) }
    val dark = isAuroraDarkTheme()
    val (c0, c1, c2) = remember(phase, dark) { phaseColors(phase, dark) }
    // Held at the midpoint rather than an extreme when motion is reduced: drift
    // is a symmetric ±offset, so 0.5f is the composition the blobs spend most of
    // their time near. This is the app background — it was the single largest
    // animation still running with the reduce-motion setting on.
    val drift = if (auroraMotionEnabled()) {
        val transition = rememberInfiniteTransition(label = "aurora-mesh")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 14_000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "aurora-drift",
        )
        animated
    } else {
        0.5f
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val driftX = (drift - 0.5f) * w * 0.06f
        val driftY = (drift - 0.5f) * h * 0.04f

        fun blob(center: Offset, radius: Float, color: Color, alpha: Float) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }

        blob(Offset(w * 0.18f + driftX, h * 0.14f + driftY), w * 0.38f, c0, if (dark) 0.28f else 0.22f)
        blob(Offset(w * 0.88f - driftX, h * 0.08f + driftY), w * 0.40f, c1, if (dark) 0.26f else 0.20f)
        blob(Offset(w * 0.78f + driftX, h * 0.92f - driftY), w * 0.46f, c2, if (dark) 0.24f else 0.18f)
        blob(Offset(w * 0.08f - driftX, h * 0.88f - driftY), w * 0.44f, c1, if (dark) 0.24f else 0.18f)
        if (phase == AuroraPhase.Dawn || phase == AuroraPhase.Dusk) {
            blob(Offset(w * 0.52f, h * 0.42f + driftY), w * 0.30f, AuroraPeach, if (dark) 0.14f else 0.10f)
        }
    }
}
