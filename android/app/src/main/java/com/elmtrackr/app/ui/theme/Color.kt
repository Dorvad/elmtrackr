package com.elmtrackr.app.ui.theme

import androidx.compose.ui.graphics.Color

// Aurora palette - exact tokens from the ElmTrackr Aurora design system
val AuroraLavender    = Color(0xFFECEEFA)  // bg - app background
val AuroraNavy        = Color(0xFF181530)  // ink - primary text
val AuroraInk2        = Color(0xFF615C8A)  // ink2 - secondary text
/**
 * The light scheme's `outline`.
 *
 * Was 0xFFA7A2C8, which measures 2.43:1 on white — below the 3:1 WCAG floor for
 * non-text UI, the same floor `DarkThemeContrastTest` had been asserting for the dark
 * scheme only. Darkened to 3.48:1. The comment used to call this "placeholder / hint
 * text" as well, but the outline role is its only remaining use, so nothing else
 * moves with it.
 */
val AuroraFaint       = Color(0xFF8A84B4)
val AuroraHair        = Color(0x1A5B4DF2)  // hair - dividers (~10 % indigo)
val AuroraSurface     = Color(0xFFFFFFFF)  // surface - card background
val AuroraSurfaceSub  = Color(0xFFF6F6FD)  // surfaceSub - tinted inner surfaces

// Brand colours
val AuroraIndigo      = Color(0xFF5B4DF2)  // primary
val AuroraIndigoDeep  = Color(0xFF4133C8)
val AuroraPlum        = Color(0xFF8B5CF6)  // secondary
val AuroraAqua        = Color(0xFF16C8D6)  // tertiary / gradient end
val AuroraAquaDeep    = Color(0xFF0E9CA8)

// Semantic - overtime / special
val AuroraPeach       = Color(0xFFFF9E7D)  // overtime indicator
val AuroraPeachDeep   = Color(0xFFEF6F45)
val AuroraOvertimeBg  = Color(0xFFFFF0E9)
/**
 * Overtime text on [AuroraOvertimeBg].
 *
 * Was 0xFFC75A30, which measures 3.83:1 on that container — below AA, and it was
 * already in use for the overtime stat *label*, so the fill/ink split was only
 * half working here. Darkened to the same value as [AuroraWarningInk], which the
 * semantic layer had already picked for this exact job: 5.23:1 on the container,
 * 5.81:1 on white. Overtime and warning are the same peach family; two different
 * inks for them was the drift the token layer exists to stop.
 *
 * The dark arm ([AuroraDarkOvertimeInk]) was already fine at 8.26:1 and is
 * unchanged. [AuroraPeachDeep] stays the graphic accent — 2.69:1 here — and must
 * not be used for text.
 */
val AuroraOvertimeInk = Color(0xFFA8481F)
val AuroraWeekendBg   = Color(0xFFF2EDFE)

// Fixed
val AuroraWhite       = Color(0xFFFFFFFF)

// Dark-mode base colours
val AuroraDarkBg         = Color(0xFF0B1020)
val AuroraDarkSurface    = Color(0xFF151D2E)
/**
 * The top stop of the clock face store's pack card, fading into
 * [AuroraDarkSurface]. A shade above the surface rather than [AuroraDarkSurfaceSub]
 * because the card needs a *lift* against the store background, not a second
 * surface tier — Sub is the tile fill that sits inside these cards.
 */
val AuroraDarkSurfaceRaised = Color(0xFF1A2338)
val AuroraDarkSurfaceSub = Color(0xFF202A3D)
val AuroraDarkInk        = Color(0xFFF5F3FF)
val AuroraDarkInk2       = Color(0xFFC8D1E0)
val AuroraDarkOutline    = Color(0xFFA4B0C3)
val AuroraDarkHair       = Color(0xFF414D64)
val AuroraDarkOvertimeBg  = Color(0xFF3A2618)
val AuroraDarkOvertimeInk = Color(0xFFFFB48A)
val AuroraDarkWeekendBg   = Color(0xFF2A2240)
val AuroraDarkWeekendInk  = Color(0xFFD4BCFF)
val AuroraDarkSyncedBg    = Color(0xFF252040)
