package com.elmtrackr.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The brand sweep for surfaces that carry white text: primary buttons, the shift
 * form's pay preview, the shop button and the selected Reports tab.
 *
 * Same indigo → violet → teal identity as the decorative gradient, with the middle
 * and end stops deepened. The decorative stops (AuroraPlum, AuroraAqua) cannot hold
 * a white label: plum itself is 4.23:1 against white and aqua 2.05:1, and the
 * primary buttons measured 3.7–4.3:1 under their labels in a render. With these
 * stops white clears 4.5:1 anywhere a centred or start-aligned label sits (10–90%
 * along the sweep), and 90% white — the pay card's secondary lines — clears it too.
 *
 * Decorative uses with no text on top keep the bright stops.
 */
val AuroraVioletInk = Color(0xFF6D3FDB)
val AuroraTealInk = Color(0xFF0A7F89)

val AuroraLegibleGradientStops: Array<Pair<Float, Color>> = arrayOf(
    0.00f to AuroraIndigo,
    0.42f to AuroraVioletInk,
    1.00f to AuroraTealInk,
)

val AuroraLegibleGradient: Brush = Brush.linearGradient(colorStops = AuroraLegibleGradientStops)
