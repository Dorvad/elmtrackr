package com.elmtrackr.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * An Aurora shadow recipe.
 *
 * A plain elevation `Dp` would not have removed the drift this token exists to
 * remove: every shadow in the app is a *triple* — blur plus an ambient alpha
 * plus a spot alpha, all tinted indigo rather than black. Those three numbers
 * were hand-tuned at each component and had started to disagree.
 */
@Immutable
data class AuroraShadow(
    val blur: Dp,
    val ambientAlpha: Float,
    val spotAlpha: Float,
)

/**
 * The shadow recipes in use. Values record what each component already drew, so
 * adopting the token is a no-op visually.
 *
 * [CardQuiet] and [CardAccent] exist only to preserve today's pixels. Folding
 * them into [Card] is a deliberate visual decision that belongs in its own
 * commit with re-recorded goldens, not smuggled into a token refactor.
 */
object Elevation {
    val None = AuroraShadow(0.dp, 0.00f, 0.00f)

    /** Segmented-control and chip selection surfaces. */
    val Selector = AuroraShadow(2.dp, 0.10f, 0.35f)

    /** The standard card lift. */
    val Card = AuroraShadow(8.dp, 0.05f, 0.34f)

    /** A card that should recede — used by the report cards. */
    val CardQuiet = AuroraShadow(8.dp, 0.04f, 0.28f)

    /** A card carrying the brand gradient, which needs a stronger spot. */
    val CardAccent = AuroraShadow(8.dp, 0.05f, 0.55f)

    /** Primary buttons. */
    val Button = AuroraShadow(10.dp, 0.22f, 0.70f)
}

/**
 * Applies an [AuroraShadow].
 *
 * `clip = false` throughout: these shadows are drawn outside the shape and the
 * components fill their own background, which is how the original hand-written
 * `shadow()` calls behaved.
 */
fun Modifier.auroraShadow(
    shadow: AuroraShadow,
    shape: Shape,
    tint: Color = AuroraIndigo,
): Modifier = if (shadow.blur.value == 0f) this else this.shadow(
    elevation = shadow.blur,
    shape = shape,
    clip = false,
    ambientColor = tint.copy(alpha = shadow.ambientAlpha),
    spotColor = tint.copy(alpha = shadow.spotAlpha),
)
