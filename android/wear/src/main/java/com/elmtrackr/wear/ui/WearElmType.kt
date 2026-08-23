package com.elmtrackr.wear.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.elmtrackr.wear.R

/**
 * Bricolage Grotesque — the display face, as on the phone.
 *
 * The phone puts it on display and headline roles: the wordmark, the big
 * numbers, the screen titles. On the watch that is the shift count-up and the
 * countdown digits, which is where a face with character actually reads at a
 * glance.
 */
private val ElmDisplayFont = FontFamily(
    Font(R.font.bricolage_grotesque, FontWeight.Normal),
    Font(R.font.bricolage_grotesque, FontWeight.Medium),
    Font(R.font.bricolage_grotesque, FontWeight.SemiBold),
    Font(R.font.bricolage_grotesque, FontWeight.Bold),
)

/** Hanken Grotesk — the body face, as on the phone: titles, labels, body copy. */
private val ElmBodyFont = FontFamily(
    Font(R.font.hanken_grotesk, FontWeight.Normal),
    Font(R.font.hanken_grotesk, FontWeight.Medium),
    Font(R.font.hanken_grotesk, FontWeight.SemiBold),
    Font(R.font.hanken_grotesk, FontWeight.Bold),
)

/**
 * The watch type scale.
 *
 * Named by the job each style does rather than by a Material role, because the
 * watch has six screens and every one of them is a fixed composition — there is
 * no long-form content here for a general scale to serve. Each style says which
 * phone role it answers to, so the two apps can be compared without reading both
 * codebases.
 *
 * The sizes are smaller than the phone's for the obvious reason, but the
 * *relationships* are the phone's: the same face on the same roles, the same
 * weight ladder, the same wide tracking on small capitalised labels.
 *
 * Sizes stay in `sp`, so they still grow with the wearer's font setting. Two
 * styles cap that growth with [withCappedFontScale] at their call sites — the
 * numerals, which have a round bezel to stay inside.
 */
object WearElmType {

    /** The ELMTRACKR wordmark at the top of every face. Phone: `labelSmall`. */
    val wordmark = TextStyle(
        fontFamily = ElmBodyFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 3.sp,
    )

    /** PUNCH IN / PUNCH OUT, the primary call to action. Phone: `titleMedium`. */
    val action = TextStyle(
        fontFamily = ElmBodyFont,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 1.sp,
    )

    /** ON SHIFT / CLOCKED OUT next to the status dot. Phone: `labelSmall` bold. */
    val status = TextStyle(
        fontFamily = ElmBodyFont,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.5.sp,
    )

    /** The last-punch / today line under the status. Phone: `bodySmall`. */
    val detail = TextStyle(
        fontFamily = ElmBodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.2.sp,
    )

    /**
     * The live shift count-up. Phone: `displayMedium`, scaled for the bezel.
     *
     * Tabular figures are not requested here because the count-up re-renders
     * every second and Bricolage's digits are already even-width; if a future
     * face shows it jittering, that is the knob to reach for.
     */
    val countUp = TextStyle(
        fontFamily = ElmDisplayFont,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp,
    )

    /** The 3-2-1 numeral. Phone: `displayLarge`. */
    val countdownDigit = TextStyle(
        fontFamily = ElmDisplayFont,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.5).sp,
    )

    /** Setup screen heading, and the punch confirmation. Phone: `titleLarge`. */
    val title = TextStyle(
        fontFamily = ElmBodyFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
    )

    /** Explanatory copy on the setup screen. Phone: `bodySmall`. */
    val body = TextStyle(
        fontFamily = ElmBodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    )

    /** Button labels. Phone: `labelLarge`. */
    val button = TextStyle(
        fontFamily = ElmBodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    )

    /** Small captions on the countdown overlay. Phone: `labelMedium`. */
    val caption = TextStyle(
        fontFamily = ElmBodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
    )
}
