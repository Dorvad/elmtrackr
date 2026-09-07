package com.elmtrackr.app.ui.dashboard

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The Evolution faces: a creature that grows a form an hour, and reaches its last at the
 * eighth.
 *
 * The pack's four species are in `EvolutionCreatures.kt`; this file is what makes one of
 * them a clock. Two things it has to get right, and they pull against each other.
 *
 * ### The hour has to be legible without counting pixels
 *
 * A creature that changes only on the hour is a clock nobody can read: for fifty-nine
 * minutes it says nothing. So the whole-hour change — the new form — is joined by two
 * continuous readings of the same number. The track above the creature is eight pips,
 * one an hour, and the pip for the hour being worked fills as that hour goes; and the
 * creature itself telegraphs the change, glowing as the hour closes and haloed for the
 * first minutes of the new one. Someone who looks up mid-afternoon can see both which
 * hour they are in and how much of it is left, and the evolution when it lands is an
 * event rather than a redraw they missed.
 *
 * ### It has to feel alive, which is not the same as animated
 *
 * Everything here moves in whole pixels. A pixel drawing offset by a fraction of a pixel
 * is a blurred pixel drawing, and blur is the one thing that would give the illusion
 * away. Within that, the creature breathes, blinks, glances about, and its leaves and
 * flames flutter half a beat off its body — the point being that no two frames of a
 * still shift are quite the same, which is what separates a character from a sprite.
 *
 * Two behaviours carry most of it. The eyes: authored as a two-pixel `ek`/`ke` pair, so
 * this file can find them by shape and re-emit them shut for a blink or with the pupils
 * moved a pixel to glance left and right. And sleep: with no shift running the creature
 * naps, eyes shut, sleep bubbles beside its head. Which is also the honest reading of
 * that state — the day is not growing — and it is why the pack still says something
 * while clocked out, the same reason Sprout and Metro read whole-day progress rather
 * than a running gate.
 *
 * Every offset here is zero at `pulse = 0`, which is the frame the card holds whenever
 * no shift is running or the device asks for reduced motion. So the nap is a still: a
 * creature with its eyes closed, not a creature animating slowly.
 */

/**
 * One authored pixel, on the shared 312×176 face box.
 *
 * Sized against the tallest form there is: twenty-one rows at 3.6dp is 76dp, which fits
 * under the hour track with the composed reading above it. Larger and the eighth-hour
 * creature would grow into the track; smaller and the pixels stop reading as pixels.
 */
private val PixelSize = 3.6.dp

/** Where the creature stands. Every form is drawn up from this line; see the art's KDoc. */
private val GroundY = 158.dp

// The hour track: eight pips above the creature and below the composed reading.
private val TrackY = 70.dp
private val PipSize = 6.dp
private val PipGap = 4.dp

private const val TAU = 2f * PI.toFloat()

/** How many motes drift off a working creature. Three reads as life; more reads as weather. */
private const val MOTE_COUNT = 3

/**
 * Draws [species] at the form [growthHours] has earned.
 *
 * [growthHours] is the scene's own 0..8 count of hours worked today, which is what makes
 * the eighth hour the final form without this file knowing anything about the day goal.
 * [accent] arrives already flipped to premium peach in overtime, the one signal every
 * face in the app shares, so the track and the halo turn with it for free.
 */
internal fun DrawScope.drawEvolutionFace(
    species: EvolutionSpecies,
    growthHours: Float,
    overtime: Boolean,
    pulse: Float,
    running: Boolean,
    foreground: Color,
    accent: Color,
) {
    val stage = floor(growthHours).toInt().coerceIn(0, EVOLUTION_FINAL_STAGE)
    // How far into the hour being worked. Pinned at one on the final form: there is no
    // next hour to be part-way through, and a track pip that kept filling would promise
    // an evolution that is not coming.
    val intoHour = if (stage >= EVOLUTION_FINAL_STAGE) {
        1f
    } else {
        (growthHours - stage).coerceIn(0f, 1f)
    }
    val form = species.forms[stage]

    val px = PixelSize.toPx()
    val ground = GroundY.toPx()
    val centreX = size.width / 2f
    val spriteWidth = form.first().length * px
    val spriteHeight = form.size * px

    // The breath, in whole pixels and zero at the start of the phase. Awake it is a full
    // pixel each way; asleep it is a single slow rise, which is a smaller, slower motion
    // than a working creature's without needing a second animation to drive it.
    val breath = sin(pulse * TAU)
    val bob = if (running) breath.roundToInt() else if (breath > 0.7f) 1 else 0
    // Leaves and flames flutter at twice the body's rate, so the silhouette is never
    // moving all of one piece.
    val flutter = if (running) sin(pulse * 2f * TAU).roundToInt() else 0
    // The shell rocks where a creature bobs: an egg has no lungs.
    val hatching = stage == 0
    val originX = centreX - spriteWidth / 2f + (if (hatching) bob else 0) * px
    val originY = ground - spriteHeight + (if (hatching) 0 else bob) * px
    val bodyCentre = Offset(centreX, originY + spriteHeight * 0.55f)

    // Behind the creature: the hour closing, the hour just turned, the final form, and
    // overtime. A gradient rather than a flat disc for the two glows -- a translucent
    // circle at a single alpha reads as a coloured plate behind the creature, which is
    // the one thing a glow must not look like.
    val glowRadius = spriteHeight * 0.66f
    fun glow(alpha: Float) = drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = alpha), Color.Transparent),
            center = bodyCentre,
            radius = glowRadius,
        ),
        radius = glowRadius,
        center = bodyCentre,
    )
    if (running && stage < EVOLUTION_FINAL_STAGE && intoHour > 0.92f) {
        // Ripening: the next form is minutes away and the creature says so.
        glow(0.18f + 0.2f * sin(pulse * PI.toFloat()))
    } else if (stage >= EVOLUTION_FINAL_STAGE) {
        // The final form keeps a quiet aura for the rest of the day. It is the one state
        // the pack cannot announce with a change of form -- there is no ninth hour to
        // evolve into -- so it is announced by never going back to looking ordinary.
        glow(0.12f + 0.06f * sin(pulse * PI.toFloat()))
    }
    if (running && stage in 1 until EVOLUTION_FINAL_STAGE && intoHour < 0.06f) {
        // Just evolved: a halo that expands and clears, through the first minutes of the
        // new hour. Driven by the pulse rather than by intoHour, which advances over
        // minutes -- a ring that took four of them to expand would not read as a burst.
        drawCircle(
            color = accent.copy(alpha = (1f - pulse) * 0.42f),
            radius = spriteHeight * 0.5f + pulse * spriteHeight * 0.5f,
            center = bodyCentre,
            style = Stroke(1.5.dp.toPx()),
        )
    }
    if (overtime) {
        drawCircle(
            color = accent.copy(alpha = 0.16f),
            radius = glowRadius,
            center = bodyCentre,
            style = Stroke(1.dp.toPx()),
        )
    }

    // The ground it stands on, so a bobbing creature reads as bobbing rather than
    // drifting.
    drawOval(
        color = foreground.copy(alpha = 0.1f),
        topLeft = Offset(centreX - spriteWidth * 0.4f, ground - 2.dp.toPx()),
        size = Size(spriteWidth * 0.8f, 5.dp.toPx()),
    )

    drawCreature(
        form = form,
        ramp = species.ramp,
        originX = originX,
        originY = originY,
        px = px,
        running = running,
        pulse = pulse,
        flutter = flutter,
    )

    if (running) {
        drawMotes(species.mote, centreX, spriteWidth, ground, accent, pulse)
    } else {
        drawSleep(centreX, spriteWidth, originY, foreground, pulse)
    }
    drawHourTrack(stage, intoHour, accent, foreground, running, pulse)
}

/**
 * Paints one form, pixel by pixel, with the living parts re-emitted as they move.
 *
 * The eye and flourish rules are matched on the characters rather than on coordinates,
 * so a sprite edited by hand keeps its blink and its flutter without anyone having to
 * update a table of pixel positions here.
 */
private fun DrawScope.drawCreature(
    form: List<String>,
    ramp: EvolutionRamp,
    originX: Float,
    originY: Float,
    px: Float,
    running: Boolean,
    pulse: Float,
    flutter: Int,
) {
    // Roughly a tenth of the phase, so a blink lands about every two seconds. Asleep the
    // eyes are simply shut.
    val shut = !running || (pulse > 0.90f && pulse < 0.965f)
    val gaze = when {
        !running -> 0
        pulse > 0.30f && pulse < 0.42f -> 1
        pulse > 0.56f && pulse < 0.68f -> -1
        else -> 0
    }
    // Only what is above the eyes flutters: a mane at the shoulder swaying with the
    // leaves would read as wind rather than as the creature.
    val browRow = form.indexOfFirst { row -> row.any { it == 'e' || it == 'k' } }

    form.forEachIndexed { row, line ->
        val y = originY + row * px
        var col = 0
        while (col < line.length) {
            val cell = line[col]
            val next = if (col + 1 < line.length) line[col + 1] else ' '
            val isEye = (cell == 'e' && next == 'k') || (cell == 'k' && next == 'e')
            if (isEye) {
                val left = originX + col * px
                if (shut) {
                    // A closed eye is a lid the width of the open one, not a smaller eye.
                    drawRect(ramp.outline, Offset(left, y), Size(px * 2f, px))
                } else {
                    val pupilLeft = when {
                        gaze < 0 -> true
                        gaze > 0 -> false
                        else -> cell == 'k'
                    }
                    drawRect(
                        if (pupilLeft) EvolutionEyeInk else EvolutionEyeWhite,
                        Offset(left, y),
                        Size(px, px),
                    )
                    drawRect(
                        if (pupilLeft) EvolutionEyeWhite else EvolutionEyeInk,
                        Offset(left + px, y),
                        Size(px, px),
                    )
                }
                col += 2
                continue
            }
            val ink = ramp.inkOf(cell)
            if (ink != null) {
                val dx = if (cell == 'a' && browRow > 0 && row < browRow) flutter else 0
                drawRect(ink, Offset(originX + (col + dx) * px, y), Size(px, px))
            }
            col++
        }
    }
}

/**
 * The eight hours as pips, filling one an hour.
 *
 * The continuous half of the reading. The pip for the hour being worked fills from the
 * bottom as the hour goes and carries a soft halo, so the track answers both "which
 * hour" and "how much of it is left" at a glance.
 */
private fun DrawScope.drawHourTrack(
    stage: Int,
    intoHour: Float,
    accent: Color,
    foreground: Color,
    running: Boolean,
    pulse: Float,
) {
    val pip = PipSize.toPx()
    val gap = PipGap.toPx()
    val span = EVOLUTION_FINAL_STAGE * pip + (EVOLUTION_FINAL_STAGE - 1) * gap
    val left = (size.width - span) / 2f
    val top = TrackY.toPx()
    val corner = CornerRadius(1.dp.toPx())
    for (index in 0 until EVOLUTION_FINAL_STAGE) {
        val hour = index + 1
        val x = left + index * (pip + gap)
        if (running && hour == stage + 1) {
            val halo = 2.dp.toPx()
            drawRoundRect(
                color = accent.copy(alpha = 0.3f * (1f - pulse)),
                topLeft = Offset(x - halo, top - halo),
                size = Size(pip + halo * 2f, pip + halo * 2f),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
        drawRoundRect(
            color = foreground.copy(alpha = 0.14f),
            topLeft = Offset(x, top),
            size = Size(pip, pip),
            cornerRadius = corner,
        )
        val fill = when {
            hour <= stage -> 1f
            hour == stage + 1 -> intoHour
            else -> 0f
        }
        if (fill > 0f) {
            drawRoundRect(
                color = accent,
                topLeft = Offset(x, top + pip * (1f - fill)),
                size = Size(pip, pip * fill),
                cornerRadius = corner,
            )
        }
    }
}

/** What a working creature gives off: spores, embers, bubbles or arcs, rising and clearing. */
private fun DrawScope.drawMotes(
    mote: EvolutionMote,
    centreX: Float,
    spriteWidth: Float,
    ground: Float,
    accent: Color,
    pulse: Float,
) {
    val px = PixelSize.toPx()
    val rise = 54.dp.toPx()
    repeat(MOTE_COUNT) { index ->
        val phase = (pulse + index / MOTE_COUNT.toFloat()) % 1f
        val drift = sin(phase * TAU + index) * 5.dp.toPx()
        val x = centreX + (index - 1) * (spriteWidth * 0.42f) + drift
        val y = ground - 8.dp.toPx() - phase * rise
        val alpha = (1f - phase) * 0.55f
        when (mote) {
            EvolutionMote.BUBBLE -> drawCircle(
                color = accent.copy(alpha = alpha),
                radius = px * (0.6f + phase * 0.8f),
                center = Offset(x, y),
                style = Stroke(1.dp.toPx()),
            )
            EvolutionMote.ARC -> {
                drawRect(accent.copy(alpha = alpha), Offset(x, y), Size(px, px))
                drawRect(accent.copy(alpha = alpha * 0.6f), Offset(x + px, y - px), Size(px, px))
            }
            else -> drawRect(
                color = accent.copy(alpha = alpha),
                topLeft = Offset(x, y),
                size = Size(px, px),
            )
        }
    }
}

/** The nap: two bubbles off the side of a sleeping head. */
private fun DrawScope.drawSleep(
    centreX: Float,
    spriteWidth: Float,
    originY: Float,
    foreground: Color,
    pulse: Float,
) {
    repeat(2) { index ->
        val phase = (pulse + index / 2f) % 1f
        drawCircle(
            color = foreground.copy(alpha = (1f - phase) * 0.3f),
            radius = (1.5f + phase * 3.5f).dp.toPx(),
            center = Offset(
                centreX + spriteWidth * 0.42f + phase * 12.dp.toPx(),
                originY + 6.dp.toPx() - phase * 24.dp.toPx(),
            ),
            style = Stroke(1.dp.toPx()),
        )
    }
}
