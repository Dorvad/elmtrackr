package com.elmtrackr.app.domain.text

/**
 * Keeps left-to-right tokens intact when they sit inside right-to-left text.
 *
 * A Hebrew sentence with an English or numeric run in it — a billing reference,
 * a currency code, an email address — is laid out by the Unicode Bidirectional
 * Algorithm, which resolves the direction of each run from its own characters
 * and from its surroundings. The failure that follows is not a rendering bug,
 * it is the algorithm working as specified on text that did not say what it
 * meant: a reference such as "2026-014" ends up displayed as "014-2026",
 * because the hyphen is neutral and takes its direction from the Hebrew
 * paragraph around it. The same reordering hits "+972-50-1234567", any
 * "USD 1,200.00" written with the code first, and file or invoice numbers that
 * mix digits with punctuation.
 *
 * The fix is to mark the boundaries of the token so the algorithm treats it as
 * one unit and resolves its direction from its own content rather than from the
 * paragraph. [FIRST_STRONG_ISOLATE] and [POP_DIRECTIONAL_ISOLATE] do exactly
 * that, and are preferred over the older embedding controls (LRE/RLE/PDF)
 * because the isolate does not leak direction into the surrounding text — a
 * neutral character immediately after the token keeps the paragraph's
 * direction instead of picking up the token's.
 *
 * "First strong" rather than an explicit left-to-right isolate is deliberate:
 * the token decides its own direction. A Hebrew client name passed through
 * this stays right-to-left; an English one becomes left-to-right; and a
 * reference that begins with digits inherits the paragraph direction for its
 * neutral characters only, which is what keeps "2026-014" in order without
 * forcing anything.
 *
 * These are formatting characters with no width and no glyph. They belong in
 * text on its way to the screen, never in text on its way to storage, to a
 * CSV export, or to a comparison — see [strip].
 */
object BidiText {

    /** U+2068. Opens a run whose direction comes from its first strong character. */
    const val FIRST_STRONG_ISOLATE: Char = '⁨'

    /** U+2069. Closes the innermost open isolate. */
    const val POP_DIRECTIONAL_ISOLATE: Char = '⁩'

    private val ISOLATE_CHARS = charArrayOf(
        FIRST_STRONG_ISOLATE,
        POP_DIRECTIONAL_ISOLATE,
        '⁦', // LRI
        '⁧', // RLI
    )

    /**
     * Wraps [token] so it cannot be reordered by the text around it.
     *
     * Null and blank tokens come back unchanged: isolating nothing would add
     * invisible characters to an empty field for no benefit, and would make an
     * "is this blank" check downstream answer no.
     *
     * Already-isolated text also comes back unchanged, so passing a value
     * through twice — a formatted amount that is then interpolated into a
     * sentence, say — does not nest isolates.
     */
    fun isolate(token: String?): String {
        if (token.isNullOrBlank()) return token.orEmpty()
        if (token.first() == FIRST_STRONG_ISOLATE && token.last() == POP_DIRECTIONAL_ISOLATE) {
            return token
        }
        return "$FIRST_STRONG_ISOLATE$token$POP_DIRECTIONAL_ISOLATE"
    }

    /**
     * [isolate] for each argument, for the common case of a localised format
     * string whose placeholders are all user or machine data.
     */
    fun isolateAll(vararg tokens: String?): Array<String> =
        Array(tokens.size) { isolate(tokens[it]) }

    /**
     * Removes every isolate character.
     *
     * Anything that leaves the screen goes through this: CSV and backup
     * exports, values written to the database, and text compared against
     * stored data. An invisible U+2068 in an exported billing reference would
     * be a real defect in someone's spreadsheet, and two strings that differ
     * only by isolates are the same string as far as the user is concerned.
     */
    fun strip(text: String?): String {
        val raw = text ?: return ""
        if (raw.none { it in ISOLATE_CHARS }) return raw
        return raw.filterNot { it in ISOLATE_CHARS }
    }

    /** True if [text] carries any isolate character. Used by tests and exports. */
    fun containsIsolates(text: String?): Boolean =
        text != null && text.any { it in ISOLATE_CHARS }
}
