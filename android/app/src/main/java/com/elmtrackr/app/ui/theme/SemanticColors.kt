package com.elmtrackr.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colour roles.
 *
 * Material 3 ships `error` and nothing else, so "this number went up", "this
 * shift ran into overtime" and "here is a hint" were each expressed with a raw
 * hex at the point of use. Three different greens for *positive* had accumulated
 * across the app — `#10B981`, `#22C55E` and `#1E9E63` — none of them named.
 *
 * Only `success` is a genuinely new hue. `warning` reuses the existing Peach
 * family and `info` reuses Aqua, both already Aurora brand tokens, so the app
 * gains vocabulary without gaining colours.
 *
 * Every role is split into a **fill** and an **ink**. A saturated mid-tone that
 * reads well as a bar, a dot or a chip background does not survive being used as
 * body text — `#10B981` on white is roughly 2.5:1. Use `fill` for surfaces and
 * indicators, `ink` for anything the user has to read. `DarkThemeContrastTest`
 * enforces the split.
 */
@Immutable
data class AuroraSemanticColors(
    /** Indicators, bars, dots, chip backgrounds at full strength. */
    val success: Color,
    /** Text and icons on a light/neutral surface. */
    val successInk: Color,
    /** Tinted container behind [successInk]. */
    val successContainer: Color,
    /** Text drawn on top of [success]. */
    val onSuccess: Color,

    val warning: Color,
    val warningInk: Color,
    val warningContainer: Color,
    val onWarning: Color,

    val info: Color,
    val infoInk: Color,
    val infoContainer: Color,
    val onInfo: Color,
)

// ── Success ───────────────────────────────────────────────────────────────────
/** Fill only. Fails AA as text — use [AuroraSuccessInk] for anything readable. */
val AuroraSuccess = Color(0xFF10B981)
val AuroraSuccessDeep = Color(0xFF0D9488)
val AuroraSuccessInk = Color(0xFF0B7A55)
val AuroraSuccessBg = Color(0xFFE6F7F0)

val AuroraDarkSuccess = Color(0xFF34D399)
val AuroraDarkSuccessInk = Color(0xFF7DE9BC)
val AuroraDarkSuccessBg = Color(0xFF11332A)

// ── Warning (Peach family) ────────────────────────────────────────────────────
/**
 * The readable end of the Peach ramp. [AuroraPeachDeep] is the *graphic* accent
 * — clock-face rings, progress arcs — and sits near 2.7:1 on the overtime
 * container, so it must not be used for text.
 */
val AuroraWarningInk = Color(0xFFA8481F)

// ── Info (Aqua family) ────────────────────────────────────────────────────────
val AuroraInfoInk = Color(0xFF0A6E78)
val AuroraInfoBg = Color(0xFFE4F8FA)

val AuroraDarkInfoInk = Color(0xFF6FE3EE)
val AuroraDarkInfoBg = Color(0xFF10323A)

// ── Accent used by the insight cards ──────────────────────────────────────────
val AuroraRose = Color(0xFFF43F5E)
val AuroraRoseDeep = Color(0xFFDB2777)
val AuroraDarkRose = Color(0xFFFB7185)
val AuroraPlumDeep = Color(0xFF6D28D9)

/** Names the light half of the synced-pill pair, which was an unnamed literal. */
val AuroraSyncedBg = Color(0xFFE8E5FF)

val AuroraSemanticLight = AuroraSemanticColors(
    success = AuroraSuccess,
    successInk = AuroraSuccessInk,
    successContainer = AuroraSuccessBg,
    onSuccess = AuroraNavy,

    warning = AuroraPeach,
    warningInk = AuroraWarningInk,
    warningContainer = AuroraOvertimeBg,
    onWarning = AuroraNavy,

    info = AuroraAqua,
    infoInk = AuroraInfoInk,
    infoContainer = AuroraInfoBg,
    onInfo = AuroraNavy,
)

val AuroraSemanticDark = AuroraSemanticColors(
    success = AuroraDarkSuccess,
    successInk = AuroraDarkSuccessInk,
    successContainer = AuroraDarkSuccessBg,
    onSuccess = AuroraNavy,

    warning = AuroraPeach,
    warningInk = AuroraDarkOvertimeInk,
    warningContainer = AuroraDarkOvertimeBg,
    onWarning = AuroraNavy,

    info = AuroraAqua,
    infoInk = AuroraDarkInfoInk,
    infoContainer = AuroraDarkInfoBg,
    onInfo = AuroraNavy,
)

val LocalAuroraSemantics = staticCompositionLocalOf { AuroraSemanticLight }

/**
 * The resolved dark-mode flag.
 *
 * Nullable on purpose: [isAuroraDarkTheme] falls back to a luminance reading
 * when this is absent, because some surfaces — Glance widgets, previews — render
 * outside [ElmTrackrTheme] and would silently turn light if the local were
 * required.
 */
val LocalAuroraDarkTheme = staticCompositionLocalOf<Boolean?> { null }
