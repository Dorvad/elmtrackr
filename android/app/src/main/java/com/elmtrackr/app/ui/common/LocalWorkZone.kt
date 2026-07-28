package com.elmtrackr.app.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import java.time.ZoneId

/**
 * The timezone the user's work is recorded in (their compensation profile's zone, falling
 * back to the app setting).
 *
 * Every surface that renders a shift date or time must use this, not
 * [ZoneId.systemDefault]. Pay, the Hours breakdown, and the CSV all bucket shifts by the
 * work zone; screens that reached for the device zone instead showed a different day for
 * the same shift whenever the two differed — a traveller's dashboard disagreed with their
 * shift list. Provide it once per screen from `WorkTimezone.zoneFor(settings)`.
 *
 * The default is the device zone so previews and tests render without a provider.
 */
val LocalWorkZone = staticCompositionLocalOf { ZoneId.systemDefault() }
