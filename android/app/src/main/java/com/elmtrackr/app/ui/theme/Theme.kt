package com.elmtrackr.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary            = AuroraIndigo,
    onPrimary          = AuroraWhite,
    primaryContainer   = AuroraWeekendBg,
    onPrimaryContainer = AuroraNavy,

    secondary            = AuroraPlum,
    onSecondary          = AuroraWhite,
    secondaryContainer   = AuroraWeekendBg,
    onSecondaryContainer = AuroraNavy,

    tertiary            = AuroraAqua,
    onTertiary          = AuroraWhite,
    tertiaryContainer   = AuroraOvertimeBg,
    onTertiaryContainer = AuroraOvertimeInk,

    background   = AuroraLavender,
    onBackground = AuroraNavy,

    surface            = AuroraSurface,
    onSurface          = AuroraNavy,
    surfaceVariant     = AuroraSurfaceSub,
    onSurfaceVariant   = AuroraInk2,
    outline            = AuroraFaint,
    outlineVariant     = AuroraHair,

    error   = AuroraPeachDeep,
    onError = AuroraWhite,
    errorContainer   = AuroraOvertimeBg,
    onErrorContainer = AuroraOvertimeInk,
)

private val DarkColorScheme = darkColorScheme(
    primary            = AuroraPlum,
    onPrimary          = AuroraWhite,
    primaryContainer   = AuroraIndigoDeep,
    onPrimaryContainer = AuroraLavender,

    secondary            = AuroraAqua,
    onSecondary          = AuroraDarkBg,
    secondaryContainer   = AuroraAquaDeep,
    onSecondaryContainer = AuroraWhite,

    tertiary            = AuroraPeach,
    onTertiary          = AuroraDarkBg,
    tertiaryContainer   = AuroraOvertimeInk,
    onTertiaryContainer = AuroraOvertimeBg,

    background   = AuroraDarkBg,
    onBackground = AuroraLavender,

    surface            = AuroraDarkSurface,
    onSurface          = AuroraLavender,
    surfaceVariant     = Color(0xFF231F40),
    onSurfaceVariant   = AuroraDarkInk2,
    outline            = AuroraInk2,
    outlineVariant     = Color(0xFF312D55),

    error            = AuroraPeach,
    onError          = AuroraDarkBg,
    errorContainer   = AuroraOvertimeInk,
    onErrorContainer = AuroraOvertimeBg,
)

@Composable
fun ElmTrackrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography  = ElmTrackrTypography,
        content     = content,
    )
}
