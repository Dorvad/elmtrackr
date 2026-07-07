package com.elmtrackr.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/**
 * The locale the UI is currently rendered in. Prefer this over
 * Locale.getDefault() for month/day names and other locale-sensitive
 * formatting in composables — it follows the in-app language choice on
 * every Android version.
 */
@Composable
fun appLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
