package com.elmtrackr.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Theme-aware Aurora tokens. Use these instead of hardcoded light-palette constants
 * so dark mode keeps readable contrast.
 */
/**
 * Whether the Aurora palette should use its dark arm.
 *
 * Reads the flag [ElmTrackrTheme] provides. The luminance reading is kept as a
 * fallback rather than removed: Glance widgets and `@Preview`s compose outside
 * the app theme, and requiring the local would silently force them light.
 */
@Composable
fun isAuroraDarkTheme(): Boolean =
    LocalAuroraDarkTheme.current ?: (MaterialTheme.colorScheme.background.luminance() < 0.5f)

@Composable
fun auroraSecondaryText(): Color = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
fun auroraFaintText(): Color = MaterialTheme.colorScheme.outline

@Composable
fun auroraNavBarBackground(): Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)

@Composable
fun auroraNavUnselectedIcon(): Color = MaterialTheme.colorScheme.outline

@Composable
fun auroraNavUnselectedLabel(): Color = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
fun auroraNavSelectedLabel(): Color = MaterialTheme.colorScheme.primary

@Composable
fun auroraSurfaceSub(): Color = MaterialTheme.colorScheme.surfaceVariant

@Composable
fun auroraOvertimeBackground(): Color =
    if (isAuroraDarkTheme()) AuroraDarkOvertimeBg else AuroraOvertimeBg

@Composable
fun auroraOvertimeInk(): Color =
    if (isAuroraDarkTheme()) AuroraDarkOvertimeInk else AuroraOvertimeInk

@Composable
fun auroraWeekendBackground(): Color =
    if (isAuroraDarkTheme()) AuroraDarkWeekendBg else AuroraWeekendBg

@Composable
fun auroraWeekendInk(): Color =
    if (isAuroraDarkTheme()) AuroraDarkWeekendInk else AuroraPlum

@Composable
fun auroraSyncedPillBackground(): Color =
    if (isAuroraDarkTheme()) AuroraDarkSyncedBg else AuroraSyncedBg

/**
 * The readable text colour for a status pill drawn on a 14% tint of [accent].
 *
 * A status pill is one colour used twice — as its own container and as its label —
 * which puts the text against a wash of itself rather than against the surface.
 * That is a far narrower gap: Aqua measured 1.83:1 on its own light tint, Peach
 * 2.59:1, Plum 3.56:1. Four of the five work statuses were below AA on the word
 * that carries the meaning.
 *
 * Resolved by accent rather than by status so every caller is covered at once —
 * work status, billing status and the project health flags all pass their own
 * colours in, and none of them should have to know about inks.
 *
 * An accent this does not recognise falls back to `onSurface`, which is readable on
 * any 14% tint over the surface. A new status colour therefore degrades to plain
 * high-contrast text rather than to a quiet failure; `StatusPillContrastTest`
 * asserts every mapped pair, and the fallback is deliberate, not a gap.
 */
@Composable
fun auroraStatusPillInk(accent: Color): Color {
    val dark = isAuroraDarkTheme()
    val scheme = MaterialTheme.colorScheme
    return when (accent) {
        AuroraAqua -> if (dark) AuroraDarkInfoInk else AuroraInfoInk
        AuroraPeachDeep -> if (dark) AuroraDarkOvertimeInk else AuroraWarningInk
        AuroraPlum -> if (dark) AuroraDarkPlumInk else AuroraPlumDeep
        AuroraIndigo -> if (dark) AuroraDarkIndigoInk else AuroraIndigoDeep
        AuroraSuccessInk -> AuroraSuccessInkStrong
        AuroraDarkSuccessInk -> AuroraDarkSuccessInk
        // error, tertiary and onSurfaceVariant already clear 4.5:1 against their own
        // tint in the theme that uses them, so they pass straight through. Listed
        // rather than left to the fallback so the mapping is total over what ships.
        //
        // Order matters above: in the light scheme `tertiary` *is* AuroraAqua, which
        // measures 1.83:1 against itself. The AuroraAqua branch has to be reached
        // first, and the test asserts the resolved pair rather than the branch.
        scheme.error, scheme.tertiary, scheme.onSurfaceVariant -> accent
        else -> scheme.onSurface
    }
}
