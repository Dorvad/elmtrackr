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
