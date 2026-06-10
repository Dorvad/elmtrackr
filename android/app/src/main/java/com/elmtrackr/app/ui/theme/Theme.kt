package com.elmtrackr.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = AuroraIndigo,
    onPrimary = AuroraWhite,
    primaryContainer = AuroraLavender,
    onPrimaryContainer = AuroraNavy,
    secondary = AuroraViolet,
    onSecondary = AuroraWhite,
    background = AuroraLavender,
    onBackground = AuroraNavy,
    surface = AuroraWhite,
    onSurface = AuroraNavy,
    surfaceVariant = AuroraSurface,
    onSurfaceVariant = AuroraNavy,
)

private val DarkColorScheme = darkColorScheme(
    primary = AuroraViolet,
    onPrimary = AuroraNavy,
    primaryContainer = AuroraNavy,
    onPrimaryContainer = AuroraLavender,
    secondary = AuroraAqua,
    onSecondary = AuroraNavy,
    background = AuroraDarkBg,
    onBackground = AuroraLavender,
    surface = AuroraDarkSurface,
    onSurface = AuroraLavender,
)

@Composable
fun ElmTrackrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color uses the Android 12+ wallpaper palette.
    // Falls back to the Aurora scheme on older devices.
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ElmTrackrTypography,
        content = content,
    )
}
