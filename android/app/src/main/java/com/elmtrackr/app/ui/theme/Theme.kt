package com.elmtrackr.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable

private val ElmTrackrShapes = Shapes(
    medium = RoundedCornerShape(CornerRadius.Small),
    large = RoundedCornerShape(CornerRadius.Medium),
    extraLarge = RoundedCornerShape(CornerRadius.Large),
)

@Composable
fun ElmTrackrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) ElmTrackrDarkColorScheme else ElmTrackrLightColorScheme,
        typography = ElmTrackrTypography,
        shapes = ElmTrackrShapes,
        content = content,
    )
}
