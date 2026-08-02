package com.elmtrackr.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val ElmTrackrPrimary = AuroraIndigo
val ElmTrackrPrimaryContainer = Color(0xFFE5E2FF)
val ElmTrackrSecondary = AuroraPlum
val ElmTrackrTertiary = AuroraAqua
val ElmTrackrError = Color(0xFFBA1A1A)
val ElmTrackrSurface = AuroraSurface
val ElmTrackrBackground = AuroraLavender
val ElmTrackrInk = AuroraNavy
val ElmTrackrMutedInk = AuroraInk2
/** Alias of [AuroraHair] — the same value was declared twice. */
val ElmTrackrHairline = AuroraHair

val ElmTrackrLightColorScheme: ColorScheme = lightColorScheme(
    primary = ElmTrackrPrimary,
    onPrimary = Color.White,
    primaryContainer = ElmTrackrPrimaryContainer,
    onPrimaryContainer = AuroraNavy,
    secondary = ElmTrackrSecondary,
    onSecondary = Color.White,
    secondaryContainer = AuroraWeekendBg,
    onSecondaryContainer = AuroraPlum,
    tertiary = ElmTrackrTertiary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD8FBFF),
    onTertiaryContainer = Color(0xFF07393D),
    error = ElmTrackrError,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = ElmTrackrBackground,
    onBackground = ElmTrackrInk,
    surface = ElmTrackrSurface,
    onSurface = ElmTrackrInk,
    surfaceVariant = AuroraSurfaceSub,
    onSurfaceVariant = ElmTrackrMutedInk,
    outline = AuroraFaint,
    outlineVariant = ElmTrackrHairline,
)

val ElmTrackrDarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFAFA8FF),
    onPrimary = Color(0xFF21145F),
    primaryContainer = Color(0xFF372A93),
    onPrimaryContainer = Color(0xFFE5E2FF),
    secondary = Color(0xFFC9B6FF),
    onSecondary = Color(0xFF31146B),
    secondaryContainer = Color(0xFF4E2A91),
    onSecondaryContainer = Color(0xFFF2EDFE),
    tertiary = Color(0xFF73EAF2),
    onTertiary = Color(0xFF00363B),
    tertiaryContainer = Color(0xFF00545C),
    onTertiaryContainer = Color(0xFFD8FBFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = AuroraDarkBg,
    onBackground = AuroraDarkInk,
    surface = AuroraDarkSurface,
    onSurface = AuroraDarkInk,
    surfaceVariant = AuroraDarkSurfaceSub,
    onSurfaceVariant = AuroraDarkInk2,
    outline = AuroraDarkOutline,
    outlineVariant = AuroraDarkHair,
)

val LightColorScheme: ColorScheme = ElmTrackrLightColorScheme
val DarkColorScheme: ColorScheme = ElmTrackrDarkColorScheme
