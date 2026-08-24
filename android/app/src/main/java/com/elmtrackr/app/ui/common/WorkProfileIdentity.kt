package com.elmtrackr.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.Layout
import com.elmtrackr.app.ui.theme.Spacing
import kotlin.math.abs

/**
 * The visual identity of a work profile: a colour and an emoji.
 *
 * Deliberately the same two tokens a task carries, drawn from the same palette. A
 * job and a task are two levels of one question — what am I clocking into — and
 * the selector above the clock stacks them, so they read as a hierarchy only if
 * they share a visual language. What separates the levels is *shape*: a profile is
 * a rounded square, a task is a circle, everywhere both appear.
 *
 * The emoji set is places and roles rather than the activities
 * [com.elmtrackr.app.ui.tasks.TASK_EMOJI_OPTIONS] offers, for the same reason —
 * two lists of the same glyphs would make the levels indistinguishable.
 */
object WorkProfileIdentity {

    /** Places and roles, to a task list's verbs and tools. */
    val EMOJI_OPTIONS: List<String> = listOf(
        "🏢", "🏥", "🏫", "🍽️", "🛒", "☕", "🏭", "🚚", "🏗️", "💼", "🖥️", "🎓",
    )

    /**
     * The task palette, unchanged. One palette across both levels is the point; a
     * second set of near-identical hues would be drift, not distinction.
     */
    val COLOR_OPTIONS: List<String> = com.elmtrackr.app.ui.tasks.TASK_COLOR_OPTIONS

    /** Emoji for a profile that has never been given one. */
    const val FALLBACK_EMOJI: String = "💼"

    /**
     * Colour for [profile], or a fallback derived from its id.
     *
     * Derived rather than fixed so two profiles the user never styled still look
     * different from each other, and keyed on the id so a profile's fallback does
     * not move when it is renamed.
     */
    fun colorHexFor(profile: CompensationProfile): String =
        profile.color?.takeIf { parseIdentityColor(it) != null }
            ?: COLOR_OPTIONS[abs(profile.id.hashCode().coerceAtLeast(Int.MIN_VALUE + 1)) % COLOR_OPTIONS.size]

    fun emojiFor(profile: CompensationProfile): String =
        profile.icon?.takeIf { it.isNotBlank() } ?: FALLBACK_EMOJI
}

/**
 * Parses a `#RRGGBB` string, returning null for anything else.
 *
 * Shared by tasks and work profiles so one malformed-colour rule covers both;
 * `parseTaskColor` delegates here.
 */
fun parseIdentityColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    val normalized = hex.trim().removePrefix("#")
    if (normalized.length != 6) return null
    return runCatching { Color(0xFF000000 or normalized.toLong(16)) }.getOrNull()
}

/**
 * A work profile's emoji on its colour, as a rounded tile.
 *
 * The rounded square is what tells a job from a task at a glance; task identity is
 * a circle wherever it appears.
 */
@Composable
fun WorkProfileTile(
    colorHex: String?,
    emoji: String,
    modifier: Modifier = Modifier,
    size: Dp = Layout.identityTile,
) {
    val tint = parseIdentityColor(colorHex) ?: MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(CornerRadius.Small)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(tint.copy(alpha = 0.18f))
            .border(Spacing.s1, tint.copy(alpha = 0.45f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}
