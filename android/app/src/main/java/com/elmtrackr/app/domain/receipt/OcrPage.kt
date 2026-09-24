package com.elmtrackr.app.domain.receipt

/**
 * One OCR engine's reading of a receipt, with each line's vertical place on the
 * image when the engine reported one.
 *
 * [top] and [bottom] are fractions of the image height (0 at the top). They are
 * how a Hebrew label from Tesseract gets paired with the digits ML Kit read on
 * the same row — the two engines never share a string.
 */
data class OcrLine(
    val text: String,
    val top: Float? = null,
    val bottom: Float? = null,
)

data class OcrPage(
    val text: String,
    val lines: List<OcrLine> = emptyList(),
) {
    companion object {
        fun fromText(text: String): OcrPage {
            val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { OcrLine(it) }
            return OcrPage(text = text, lines = lines)
        }
    }
}
