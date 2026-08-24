package com.elmtrackr.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The published palette for the "Sign in with Google" button.
 *
 * These are Google's values, not ours, which is the whole reason they sit here
 * as named constants instead of being drawn from the Aurora scheme: the button
 * has to look the same in every app that shows it, and a user who has learnt to
 * recognise it should not have to re-learn it here. Substituting the app's own
 * surface and outline would make it a nicer fit and a worse signal.
 *
 * Source: Google's Sign in with Google branding guidelines
 * (developers.google.com/identity/branding-guidelines), light and dark themes.
 * If that page changes, change these — do not tune them by eye.
 */
@Immutable
data class GoogleButtonColors(
    val container: Color,
    val stroke: Color,
    val text: Color,
)

val GoogleButtonLight = GoogleButtonColors(
    container = Color(0xFFFFFFFF),
    stroke = Color(0xFF747775),
    text = Color(0xFF1F1F1F),
)

val GoogleButtonDark = GoogleButtonColors(
    container = Color(0xFF131314),
    stroke = Color(0xFF8E918F),
    text = Color(0xFFE3E3E3),
)
