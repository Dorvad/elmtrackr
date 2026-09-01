package com.elmtrackr.app.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elmtrackr.app.ui.design.auroraMotionEnabled

/**
 * The Retro face's split-flap board: every digit sits on its own card, and a
 * digit change folds the top half down over the bottom — the airport departure
 * board, in amber.
 *
 * A drawing, not a screen: its dimensions are illustration geometry and its
 * hex values are pigment, same as [drawVinylFace]'s disc — which is why the
 * file sits on the design-budget exemption list.
 *
 * Per-cell animation is the point here, not an accident: only the cell whose
 * digit actually changed runs a flip, so a ticking second costs one small
 * animation, two at a rollover. (The general elapsed display deliberately
 * avoids per-digit animation — see `LiveClockTimer` — but this face exists for
 * it.) With reduced motion the digits swap in place and nothing folds.
 */
@Composable
internal fun RetroFlipBoard(
    text: String,
    digitColor: Color,
    surface: Color,
    modifier: Modifier = Modifier,
) {
    // Digits read left-to-right in either layout direction, exactly like the
    // plain text timer this replaces; the merged node reads the whole time at
    // once instead of eight fragments. Deliberately not a live region, for the
    // reasons documented on LiveClockTimer.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier.semantics(mergeDescendants = true) { contentDescription = text },
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            text.forEach { char ->
                if (char == ':') FlipSeparator(digitColor) else FlipCell(char, digitColor, surface)
            }
        }
    }
}

/** `hh:mm:ss`, matching what `LiveClockTimer` prints for a running shift. */
internal fun flipElapsedText(elapsedSeconds: Long): String {
    val total = elapsedSeconds.coerceAtLeast(0L)
    val hh = (total / 3600).toString().padStart(2, '0')
    val mm = (total / 60 % 60).toString().padStart(2, '0')
    val ss = (total % 60).toString().padStart(2, '0')
    return "$hh:$mm:$ss"
}

// Card pigments: darker than the face's own amber-brown so the board reads as
// hardware mounted on it, with the upper half a shade brighter — flap displays
// catch the light on top.
internal val FlipCardTop = Color(0xFF261F13)
internal val FlipCardBottom = Color(0xFF1C160C)
internal val FlipSeam = Color(0xFF0E0B06)

private val CellWidth = 33.dp
private val CellHeight = 56.dp
private val CellCorner = 6.dp
private const val FLIP_MILLIS = 260

@Composable
private fun FlipCell(char: Char, digitColor: Color, surface: Color) {
    val motion = auroraMotionEnabled()
    var current by remember { mutableStateOf(char) }
    var previous by remember { mutableStateOf(char) }
    // 0 = flap just released, 1 = settled on the new digit.
    val flip = remember { Animatable(1f) }

    LaunchedEffect(char, motion) {
        if (char == current) return@LaunchedEffect
        previous = current
        current = char
        if (motion) {
            flip.snapTo(0f)
            flip.animateTo(1f, tween(FLIP_MILLIS, easing = FastOutSlowInEasing))
        } else {
            flip.snapTo(1f)
        }
    }

    val t = flip.value
    Box(Modifier.size(CellWidth, CellHeight)) {
        // Static top half already shows the incoming digit — the falling flap
        // uncovers it. The static bottom keeps the old digit until the flap
        // passes the seam and covers it for the rest of the fold.
        FlipHalf(current, top = true, digitColor = digitColor)
        FlipHalf(if (t >= 0.5f) current else previous, top = false, digitColor = digitColor)

        if (t < 1f) {
            if (t < 0.5f) {
                // First half of the fold: the old digit's top half tips forward.
                FlipHalf(
                    previous, top = true, digitColor = digitColor,
                    shade = t,
                    flap = Modifier.graphicsLayer {
                        rotationX = t / 0.5f * -90f
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        cameraDistance = 10f * density
                    },
                )
            } else {
                // Second half: the new digit's bottom half falls into place.
                FlipHalf(
                    current, top = false, digitColor = digitColor,
                    shade = 1f - t,
                    flap = Modifier.graphicsLayer {
                        rotationX = (1f - t) / 0.5f * 90f
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        cameraDistance = 10f * density
                    },
                )
            }
        }

        // The seam, and the side hinges the flaps ride on.
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(1.5.dp)
                .background(FlipSeam),
        )
        Box(Modifier.align(Alignment.CenterStart).size(2.5.dp, 9.dp).background(surface))
        Box(Modifier.align(Alignment.CenterEnd).size(2.5.dp, 9.dp).background(surface))
    }
}

/**
 * One half of a card, clipped to show only the matching half of its digit.
 *
 * The digit itself is laid out at full cell height inside the half-height,
 * clipped box; the quarter-height offset counteracts the centering that
 * [requiredSize] applies when it overflows, parking the digit's centre exactly
 * on the seam.
 */
@Composable
private fun BoxScope.FlipHalf(
    char: Char,
    top: Boolean,
    digitColor: Color,
    flap: Modifier = Modifier,
    shade: Float = 0f,
) {
    val shape = if (top) {
        RoundedCornerShape(topStart = CellCorner, topEnd = CellCorner)
    } else {
        RoundedCornerShape(bottomStart = CellCorner, bottomEnd = CellCorner)
    }
    Box(
        Modifier
            .align(if (top) Alignment.TopCenter else Alignment.BottomCenter)
            .size(CellWidth, CellHeight / 2)
            .then(flap)
            .clip(shape)
            .background(if (top) FlipCardTop else FlipCardBottom),
    ) {
        Box(
            Modifier
                .offset(y = if (top) CellHeight / 4 else -(CellHeight / 4))
                .requiredSize(CellWidth, CellHeight),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                char.toString(),
                style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
                color = digitColor,
            )
        }
        // A folding flap turns away from the light.
        if (shade > 0f) {
            Box(
                Modifier
                    .size(CellWidth, CellHeight / 2)
                    .background(Color.Black.copy(alpha = 0.45f * (shade / 0.5f).coerceIn(0f, 1f))),
            )
        }
    }
}

@Composable
private fun FlipSeparator(digitColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(4.dp).clip(CircleShape).background(digitColor.copy(alpha = 0.7f)))
        Box(Modifier.size(4.dp).clip(CircleShape).background(digitColor.copy(alpha = 0.7f)))
    }
}
