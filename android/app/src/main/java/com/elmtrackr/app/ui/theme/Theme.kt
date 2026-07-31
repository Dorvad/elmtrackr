package com.elmtrackr.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ElmTrackrShapes = Shapes(
    // Text fields and menus take extraSmall; Material's 4dp default reads
    // square next to the Aurora cards, so inputs share the 12dp radius the
    // reference frames use for form fields.
    extraSmall = RoundedCornerShape(CornerRadius.Small),
    // Stock chips take small; 10dp matches ElmChoiceChip.
    small = RoundedCornerShape(10.dp),
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
