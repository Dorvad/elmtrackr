package com.elmtrackr.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLocale
import java.util.Locale

/**
 * The locale the UI is currently rendered in. Prefer this over [Locale.getDefault] for
 * month and day names, money, hours and any other locale-sensitive formatting in a
 * composable — it follows the in-app language choice on every Android version.
 *
 * Reads `LocalLocale`, which is observable state: a locale change recomposes everything
 * that read it. The previous implementation ended `?: Locale.getDefault()`, and Compose's
 * own `NonObservableLocale` lint check — new in the 1.11 UI artifact — flags exactly that
 * fallback, because `Locale.getDefault()` is process state that composition cannot
 * observe. A locale change would leave whatever had fallen through to it showing the old
 * language until something else happened to recompose it.
 *
 * No fallback is needed now: `LocalLocale` always has a value.
 */
@Composable
fun appLocale(): Locale = LocalLocale.current.platformLocale
