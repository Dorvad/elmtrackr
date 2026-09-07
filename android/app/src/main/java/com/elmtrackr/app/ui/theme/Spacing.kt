package com.elmtrackr.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The numeric spacing scale.
 *
 * The original scale was 4/8/16/24/32/48, which is a clean geometric ladder and
 * also the reason it was routinely bypassed: 6, 10, 12, 14 and 18 dp are all
 * high-traffic values in this codebase with nowhere to live, so screens reached
 * for raw literals instead. The scale now covers what the screens actually use.
 *
 * Prefer [Layout] when a role fits — `Layout.cardPadding` says why a value was
 * chosen, `Spacing.s16` only says what it is. Reach for [Spacing] directly for
 * one-off rhythm that no role describes.
 */
object Spacing {
    val s1 = 1.dp
    val s2 = 2.dp
    val s4 = 4.dp
    val s6 = 6.dp
    val s8 = 8.dp
    val s10 = 10.dp
    val s12 = 12.dp
    val s14 = 14.dp
    val s16 = 16.dp
    val s18 = 18.dp
    val s20 = 20.dp
    val s24 = 24.dp
    val s32 = 32.dp
    val s48 = 48.dp
    val s64 = 64.dp
    val s96 = 96.dp

    // Original names, kept as aliases so existing call sites keep compiling and
    // reading naturally. New code should prefer Layout.* or the sN names.
    val xs = s4
    val sm = s8
    val md = s16
    val lg = s24
    val xl = s32
    val xxl = s48

    /** Web `px-5` horizontal screen gutter */
    val screenH = s20
}

/**
 * Spacing by role. These are the values a screen should reach for first: they
 * survive a visual retune, because changing "how much padding a card has" here
 * changes it everywhere at once instead of across several hundred literals.
 */
object Layout {
    /** Horizontal gutter between screen content and the screen edge. */
    val screenGutter = Spacing.s20

    /** Padding inside a card between its border and its content. */
    val cardPadding = Spacing.s16

    /** Vertical gap between two stacked cards. */
    val cardGap = Spacing.s12

    /** Vertical gap between titled sections of a screen. */
    val sectionGap = Spacing.s24

    /** Vertical rhythm inside a list row or a card's internal stack. */
    val rowGap = Spacing.s8

    /** Gap between an icon and its label, or between chip internals. */
    val inlineGap = Spacing.s6

    /** Vertical padding of a tappable list row. */
    val listRowVertical = Spacing.s14

    /** Trailing spacer that keeps the last item clear of the bottom nav bar. */
    val bottomNavInset = Spacing.s96

    /**
     * The smallest a tappable thing is allowed to be.
     *
     * 48dp is Android's accessibility minimum, and it is about the person, not
     * the design: it is roughly the area a fingertip actually covers, so a
     * smaller target is one that people with less steady hands will keep
     * missing. Apply it with `Modifier.sizeIn`/`heightIn` rather than by growing
     * the icon — the target can extend past the paint.
     */
    val minTouchTarget = 48.dp

    /**
     * A clock face preview on a picker tile.
     *
     * Tall enough that the face reads as a face rather than a swatch — the
     * canvas draws arcs, dials and flip cards, and below about this height they
     * collapse into indistinguishable smudges.
     */
    val facePreviewHeight = 68.dp

    /**
     * The dashboard clock card's face box, and the reference the faces were
     * drawn against: a 312×176 canvas whose x positions stretch with the width
     * while everything else is in dp. The store's previews render the same
     * drawing through a canvas scaled from this height, so a preview is the
     * face at a smaller scale rather than a second drawing of it.
     */
    val clockFaceBoxHeight = 176.dp

    /** The reference width of the face box; see [clockFaceBoxHeight]. */
    val clockFaceBoxWidth = 312.dp

    /**
     * Blur the store's full-screen look opens from: the face arrives as a
     * suggestion and sharpens, the one reveal the store allows itself.
     */
    val packVeilBlur = 3.dp

    /**
     * A colour-plus-emoji identity tile — a work profile above the clock, or the
     * same profile on a shift row. Small enough to sit inside a chip without
     * setting the row's height.
     */
    val identityTile = Spacing.s24

    /**
     * The panel a teaching demo is drawn in, on the Projects guide.
     *
     * Larger than the update wizard's banner, because these demos are the
     * explanation rather than a garnish on it: the clock-in sequence has a mode
     * switch, a chip, a running shift and a filling bank to get through, and
     * below about this height they stop being separable.
     */
    /**
     * An icon sitting inline with a label — a chevron on an expander, the mark
     * beside a text button.
     *
     * Smaller than [minTouchTarget] on purpose: this is the paint, and the row
     * or button around it supplies the target.
     */
    val inlineIcon = 18.dp

    val guideDemoHeight = 180.dp

    /**
     * The footprint the guide reserves for a page, demo and text together.
     *
     * Fixed at the tallest page rather than wrapped to each: the pages are read
     * by tapping Next repeatedly, and a button that moves between taps is a
     * button people mis-hit.
     */
    val guidePageMinHeight = 400.dp
}
