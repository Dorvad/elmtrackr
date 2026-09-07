package com.elmtrackr.app.data.receipt

/**
 * Turns a photographed receipt into the black-on-white image Tesseract expects.
 *
 * Tesseract binarizes internally with a global Otsu threshold, which assumes the
 * page is lit evenly. A receipt photographed by hand almost never is: it curls,
 * it catches a shadow from the phone itself, and thermal print fades unevenly
 * along the roll. One global threshold over that either dissolves the pale half
 * of the receipt into white or floods the shadowed half into black, and either
 * way the total is as likely to be lost as any other line.
 *
 * So the image is binarized here instead, adaptively — each pixel judged against
 * the average of its own neighbourhood rather than against the page. That is the
 * Bradley–Roth method, and it costs two passes over the pixels: one to build an
 * integral image, one to threshold against it.
 *
 * Pure integer arithmetic on ARGB values, deliberately: no `android.graphics`,
 * so the whole thing runs and is tested on the JVM. The bitmap plumbing lives at
 * the call site.
 */
internal object ReceiptImageBinarizer {

    /**
     * Side of the averaging window, as a fraction of image width.
     *
     * An eighth is the value the Bradley–Roth paper uses and it holds up here:
     * the window has to be comfortably wider than a stroke of text — otherwise
     * the inside of a thick glyph averages to its own darkness and dissolves —
     * and narrower than the lighting gradient it is meant to track.
     */
    private const val WINDOW_DIVISOR = 8

    /** Never let the window collapse on a narrow image. */
    private const val MIN_WINDOW_PX = 16

    /**
     * How far below its neighbourhood a pixel must sit to count as ink, in
     * percent.
     *
     * Fifteen is the paper's figure. Lower prints noise as speckle; higher eats
     * the thin strokes that distinguish ד from ר and ה from ח — exactly the
     * confusions that turn a Hebrew total label into a word the parser cannot
     * match.
     */
    private const val THRESHOLD_PERCENT = 15

    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF000000.toInt()

    /**
     * @param pixels ARGB pixels, row-major, length [width] × [height].
     * @return a new array of the same shape holding only opaque black and white.
     */
    fun binarize(pixels: IntArray, width: Int, height: Int): IntArray {
        require(width > 0 && height > 0) { "Image must have a positive size" }
        require(pixels.size == width * height) { "Pixel array does not match ${width}x$height" }

        val gray = toGrayscale(pixels)
        // Integral image with a zero row and column, so a window sum is four
        // lookups with no bounds juggling at the edges. Long, because a 2048²
        // image of 255s overflows Int by two orders of magnitude.
        val integral = LongArray((width + 1) * (height + 1))
        for (y in 0 until height) {
            var rowSum = 0L
            for (x in 0 until width) {
                rowSum += gray[y * width + x]
                integral[(y + 1) * (width + 1) + (x + 1)] =
                    integral[y * (width + 1) + (x + 1)] + rowSum
            }
        }

        val half = maxOf(width / WINDOW_DIVISOR, MIN_WINDOW_PX) / 2
        val out = IntArray(pixels.size)
        for (y in 0 until height) {
            val y0 = maxOf(0, y - half)
            val y1 = minOf(height - 1, y + half)
            for (x in 0 until width) {
                val x0 = maxOf(0, x - half)
                val x1 = minOf(width - 1, x + half)
                val count = ((x1 - x0 + 1) * (y1 - y0 + 1)).toLong()
                val sum = integral[(y1 + 1) * (width + 1) + (x1 + 1)] -
                    integral[y0 * (width + 1) + (x1 + 1)] -
                    integral[(y1 + 1) * (width + 1) + x0] +
                    integral[y0 * (width + 1) + x0]
                // count * gray * 100 <= sum * (100 - t) — the comparison is kept
                // in integers so it cannot drift with floating point.
                val value = gray[y * width + x].toLong()
                out[y * width + x] =
                    if (value * count * 100L <= sum * (100L - THRESHOLD_PERCENT)) BLACK else WHITE
            }
        }
        return out
    }

    /**
     * Luminance, at the usual Rec. 601 weights, as integers.
     *
     * Weighted rather than a plain channel average because receipts are printed
     * and stamped in colour often enough to matter: a red "מבצע" overlay
     * averages to a mid grey that thresholds unpredictably, while its luminance
     * places it correctly.
     */
    private fun toGrayscale(pixels: IntArray): IntArray = IntArray(pixels.size) { index ->
        val pixel = pixels[index]
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        (299 * r + 587 * g + 114 * b) / 1000
    }
}
