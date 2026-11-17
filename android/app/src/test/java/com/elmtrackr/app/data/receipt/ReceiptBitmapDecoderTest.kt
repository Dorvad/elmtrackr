package com.elmtrackr.app.data.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The subsampling rule behind every bounded receipt decode — OCR, the PDF export,
 * and now the inline and full-screen previews, which used to decode at the file's
 * native resolution.
 */
class ReceiptBitmapDecoderTest {

    @Test
    fun `an image already within the bound is not subsampled`() {
        assertEquals(1, ReceiptBitmapDecoder.sampleSizeFor(800, 600, maxDimensionPx = 1024))
        assertEquals(1, ReceiptBitmapDecoder.sampleSizeFor(1024, 768, maxDimensionPx = 1024))
    }

    @Test
    fun `a camera photo is subsampled for a preview`() {
        // 12 MP, as a phone camera writes it.
        assertEquals(2, ReceiptBitmapDecoder.sampleSizeFor(4000, 3000, maxDimensionPx = 1024))
        // 50 MP.
        assertEquals(4, ReceiptBitmapDecoder.sampleSizeFor(8160, 6120, maxDimensionPx = 1024))
    }

    @Test
    fun `the longer side decides, whichever it is`() {
        assertEquals(
            ReceiptBitmapDecoder.sampleSizeFor(4000, 3000, maxDimensionPx = 1024),
            ReceiptBitmapDecoder.sampleSizeFor(3000, 4000, maxDimensionPx = 1024),
        )
    }

    /**
     * Never below the bound, and less than twice above it: enough pixels for the
     * surface it was sized for, and a hard ceiling on what one receipt can cost.
     */
    @Test
    fun `the result stays between the bound and twice the bound`() {
        for (longSide in listOf(1024, 1500, 2047, 2048, 3000, 4096, 9000, 16_000)) {
            for (bound in listOf(1024, 2048)) {
                val sample = ReceiptBitmapDecoder.sampleSizeFor(longSide, longSide / 2, bound)
                val decoded = longSide / sample
                assertTrue("$longSide px at /$sample fell below $bound", decoded >= minOf(bound, longSide))
                assertTrue("$longSide px at /$sample exceeded twice $bound", decoded < bound * 2)
            }
        }
    }

    @Test
    fun `the preview bound is tighter than the OCR bound`() {
        assertTrue(
            ReceiptBitmapDecoder.PREVIEW_MAX_DIMENSION_PX < ReceiptBitmapDecoder.MAX_DIMENSION_PX,
        )
    }
}
