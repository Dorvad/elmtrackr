package com.elmtrackr.app.data.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The adaptive threshold, on the lighting a hand-held receipt photo actually has.
 *
 * Every case here is one a global threshold — which is what Tesseract does on its
 * own — gets wrong, because a global threshold has to pick a single brightness for
 * a page that does not have one.
 */
class ReceiptImageBinarizerTest {

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    private fun argb(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    private fun isInk(pixels: IntArray, width: Int, x: Int, y: Int) = pixels[y * width + x] == black

    /**
     * The case the whole thing exists for: a receipt lit brightly at one end and
     * shadowed at the other, with equally dark print along its length.
     *
     * A global threshold puts its cut somewhere in the middle of that gradient,
     * so the print in the bright half survives and the print in the shadowed half
     * is swallowed by a background darker than the ink it is judged against — or
     * the reverse. Judging each pixel against its own neighbourhood finds all of
     * it.
     */
    @Test
    fun `text is found at both ends of a lighting gradient`() {
        val width = 240
        val height = 40
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                // Background falls from near-white to dark grey across the image.
                pixels[y * width + x] = argb(250 - (x * 190) / width)
            }
        }
        // One ink stroke in the bright end, one in the shadowed end, each 40 %
        // darker than the paper beside it.
        val strokes = listOf(20, 220)
        strokes.forEach { sx ->
            for (y in 12 until 28) {
                for (x in sx until sx + 4) {
                    val background = 250 - (x * 190) / width
                    pixels[y * width + x] = argb((background * 6) / 10)
                }
            }
        }

        val out = ReceiptImageBinarizer.binarize(pixels, width, height)

        strokes.forEach { sx ->
            assertTrue("stroke at x=$sx was lost", isInk(out, width, sx + 1, 20))
        }
        // And the paper between them stayed paper.
        assertTrue(!isInk(out, width, 120, 20))
    }

    /** Nothing but paper stays nothing but paper — no speckle to invent glyphs from. */
    @Test
    fun `an evenly lit blank image produces no ink`() {
        val width = 64
        val height = 64
        val pixels = IntArray(width * height) { argb(238) }

        val out = ReceiptImageBinarizer.binarize(pixels, width, height)

        assertEquals(0, out.count { it == black })
    }

    /** Output is strictly two opaque tones; Tesseract is handed no half-shades. */
    @Test
    fun `output is only opaque black and white`() {
        val width = 32
        val height = 16
        val pixels = IntArray(width * height) { index -> argb((index * 7) % 256) }

        val out = ReceiptImageBinarizer.binarize(pixels, width, height)

        assertTrue(out.all { it == white || it == black })
    }

    /**
     * Luminance, not a channel average: red print on white paper is ink.
     *
     * A plain average puts pure red at 85, which reads as a mid grey; its
     * luminance is 76 against paper at 255, which reads as the ink it is.
     */
    @Test
    fun `coloured print is read by luminance`() {
        val width = 64
        val height = 32
        val pixels = IntArray(width * height) { white }
        for (y in 10 until 22) {
            for (x in 28 until 36) {
                pixels[y * width + x] = (0xFF shl 24) or (0xFF shl 16) // opaque red
            }
        }

        val out = ReceiptImageBinarizer.binarize(pixels, width, height)

        assertTrue(isInk(out, width, 32, 16))
    }

    @Test
    fun `a mismatched pixel count is rejected rather than read past the end`() {
        val error = runCatching { ReceiptImageBinarizer.binarize(IntArray(10), 4, 4) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }
}
