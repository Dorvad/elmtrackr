package com.elmtrackr.app.domain.receipt

import kotlin.math.abs

/**
 * Puts the Latin engine's digits onto the Hebrew engine's total line.
 *
 * Tesseract reads סה"כ and garbles the number next to it. ML Kit reads the
 * number and cannot read the label, so it does not know which number is the
 * total. Each page still has the line's place on the image. A total label and
 * the amount on the same row are the same charge; the label keeps the row, and
 * the Latin digits replace whatever Tesseract made of the number.
 */
object ReceiptOcrFusion {

    fun fuse(hebrew: OcrPage?, latin: OcrPage?): String? {
        if (hebrew == null) return latin?.text
        val hebrewLines = hebrew.lines.ifEmpty { OcrPage.fromText(hebrew.text).lines }
        val latinLines = latin?.lines?.ifEmpty { null }
            ?: latin?.let { OcrPage.fromText(it.text).lines }
            ?: return hebrew.text
        if (hebrewLines.isEmpty() || latinLines.isEmpty()) return hebrew.text

        return hebrewLines.mapIndexed { index, line ->
            rewriteTotalLine(line.text, latinAmountsFor(line, index, hebrewLines, latinLines))
        }.joinToString("\n")
    }

    private fun latinAmountsFor(
        hebrew: OcrLine,
        index: Int,
        hebrewLines: List<OcrLine>,
        latinLines: List<OcrLine>,
    ): List<String> {
        if (!ReceiptParser.hasStrongTotalLabel(hebrew.text)) return emptyList()
        val ranked = rankedLatinLines(hebrew, index, hebrewLines, latinLines)
        val withAmount = ranked.filter { amountTokens(it.first.text).isNotEmpty() }
        return withAmount.firstOrNull()?.let { amountTokens(it.first.text) }.orEmpty()
    }

    private fun rankedLatinLines(
        hebrew: OcrLine,
        index: Int,
        hebrewLines: List<OcrLine>,
        latinLines: List<OcrLine>,
    ): List<Pair<OcrLine, Float>> {
        val hebrewCenter = center(hebrew)
        val latinHasGeometry = latinLines.any { center(it) != null }
        if (hebrewCenter != null && latinHasGeometry) {
            val height = ((hebrew.bottom ?: hebrewCenter) - (hebrew.top ?: hebrewCenter))
                .takeIf { it > 0.001f } ?: 0.03f
            val threshold = maxOf(height * 1.5f, 0.025f)
            return latinLines.mapNotNull { line ->
                center(line)?.let { lineCenter ->
                    val distance = abs(lineCenter - hebrewCenter)
                    if (distance <= threshold) line to distance else null
                }
            }.sortedBy { it.second }
        }

        val hebrewSpan = (hebrewLines.size - 1).coerceAtLeast(1)
        val target = index.toFloat() / hebrewSpan
        val latinSpan = (latinLines.size - 1).coerceAtLeast(1)
        return latinLines.withIndex().mapNotNull { (latinIndex, line) ->
            val distance = abs(latinIndex.toFloat() / latinSpan - target)
            if (distance <= INDEX_TOLERANCE) line to distance else null
        }.sortedBy { it.second }
    }

    private fun rewriteTotalLine(hebrewLine: String, latinAmounts: List<String>): String {
        if (latinAmounts.isEmpty()) return hebrewLine
        val stripped = AMOUNT_TOKEN.replace(hebrewLine, " ").replace(Regex("\\s+"), " ").trim()
        return "$stripped ${latinAmounts.joinToString(" ")}".trim()
    }

    private fun amountTokens(line: String): List<String> =
        AMOUNT_TOKEN.findAll(line).map { it.value.trim() }.filter { it.isNotEmpty() }.toList()

    private fun center(line: OcrLine): Float? {
        val top = line.top ?: return null
        val bottom = line.bottom ?: return top
        return (top + bottom) / 2f
    }

    private const val INDEX_TOLERANCE = 0.18f

    private val AMOUNT_TOKEN = Regex(
        """(?<!\d)(?:₪|ILS|NIS|\$|€|£)?\s*(?:\d{1,3}(?:,\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?)(?!\d)\s*(?:₪|ILS|NIS|\$|€|£|שח|שקל)?""",
        RegexOption.IGNORE_CASE,
    )
}
