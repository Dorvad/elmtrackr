package com.elmtrackr.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * ElmTrackr corner radius tokens.
 *
 *  Small  (12dp) — badges, form fields, icon containers, segmented tracks
 *  Medium (16dp) — list cards, shift rows, dialog panels
 *  Large  (24dp) — feature cards, clock card, bottom sheets
 *
 * The smaller values below are not variations on the scale; each names a
 * specific control that was carrying the number inline.
 */
object CornerRadius {
    val Small  = 12.dp
    val Medium = 16.dp
    val Large  = 24.dp
    /** Web `rounded-2xl` — primary buttons */
    val Button = 18.dp
    /** Bottom-nav active icon pill */
    val NavPill = 13.dp
    /** Choice chips, and Material's `shapes.small` so stock chips match them. */
    val Chip = 10.dp
    /** The lifted segment inside a segmented pill track. */
    val SegmentPill = 9.dp
}
