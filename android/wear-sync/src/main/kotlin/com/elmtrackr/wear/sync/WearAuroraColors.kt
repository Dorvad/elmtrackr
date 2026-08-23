package com.elmtrackr.wear.sync

/**
 * The Aurora palette, as the watch needs it.
 *
 * Every value here is copied from the phone module's
 * `com.elmtrackr.app.ui.theme.Color` / `SemanticColors`, and the phone name is
 * given against each one. The two modules cannot share a `Color` type — the
 * phone maps these into Compose Material 3 and the watch into Wear Compose
 * Material 3 — so this is a mirror, and a mirror only stays true if it is
 * checked. When a brand colour moves on the phone, move it here in the same
 * change.
 *
 * The watch is always on a dark surface, so the reference is the phone's **dark
 * scheme**, not its light one. That distinction was previously lost: `INK2`
 * held `0xFF615C8A`, which is the phone's *light* secondary ink and measures
 * 3.4:1 on black — under the 4.5:1 AA floor for body text, on a screen
 * read at arm's length in daylight. It is now the dark scheme's `#C8D1E0`.
 */
object WearAuroraColors {

    // ── Brand ───────────────────────────────────────────────────────────────
    /** Phone `AuroraIndigo` — primary. */
    const val INDIGO = 0xFF5B4DF2.toInt()

    /** Phone `AuroraPlum` — secondary, and the middle stop of the gradient. */
    const val PLUM = 0xFF8B5CF6.toInt()

    /** Phone `AuroraAqua` — tertiary, and the end stop of the gradient. */
    const val AQUA = 0xFF16C8D6.toInt()

    // ── Text on a dark surface ──────────────────────────────────────────────
    /** Phone `AuroraDarkInk` — primary text. 19.1:1 on black. */
    const val INK = 0xFFF5F3FF.toInt()

    /** Phone `AuroraDarkInk2` — secondary text. 13.7:1 on black. */
    const val INK2 = 0xFFC8D1E0.toInt()

    /**
     * Phone `AuroraDarkOutline` — the dimmest text the watch is allowed to
     * draw, and hairlines. 9.6:1 on black.
     *
     * Anything quieter than this belongs to a decorative element, not a label.
     * The watch used to reach for `INK` at 0.6-0.7 alpha for the same job,
     * which lands between 6.8:1 and 9.1:1 — close to this by accident rather
     * than by decision, and not a number anyone could check against the phone.
     */
    const val OUTLINE = 0xFFA4B0C3.toInt()

    // ── Semantic ────────────────────────────────────────────────────────────
    /**
     * Phone `AuroraDarkSuccess` — the running-shift indicator.
     *
     * The dark arm specifically: the phone's light `AuroraSuccess` (`#10B981`)
     * is documented there as fill-only and fails AA as text.
     */
    const val SUCCESS = 0xFF34D399.toInt()

    // ── Surfaces ────────────────────────────────────────────────────────────
    /**
     * Phone `AuroraDarkSurface` / `AuroraDarkSurfaceSub` — raised panels.
     *
     * The watch draws on pure black rather than on these (the Wear colour
     * guidance the July review cited, and the reason a watch face blends into
     * the bezel), so nothing on the watch paints with them today. They are kept
     * because the phone's dark surface ladder is part of the same system and a
     * future watch panel should sit on these rather than invent a value.
     */
    const val SURFACE = 0xFF151D2E.toInt()
    const val SURFACE_SUB = 0xFF202A3D.toInt()

    /** Phone `AuroraNavy` — the ink the brand gradient is legible against. */
    const val NAVY = 0xFF181530.toInt()

    // ── Compatibility ───────────────────────────────────────────────────────
    /** Older name for [INK]; kept so existing call sites keep reading naturally. */
    const val ON_SURFACE = INK
}
