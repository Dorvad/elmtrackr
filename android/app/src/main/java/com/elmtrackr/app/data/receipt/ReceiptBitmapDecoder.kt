package com.elmtrackr.app.data.receipt

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Bounded receipt-image decoding shared by both OCR engines. A 10 MB JPEG can
 * be 100 MP and inflate to hundreds of MB in ARGB_8888, so decoding without
 * an inSampleSize bound is an OutOfMemoryError waiting for a high-res camera.
 */
internal object ReceiptBitmapDecoder {

    const val MAX_DIMENSION_PX = 2048

    /**
     * Thumbnail-sized previews: a receipt shown 150–160 dp tall in a form needs a
     * fraction of the pixels OCR does. 1024 px covers a full-width image on the
     * densest phones in circulation without holding a 2048 px bitmap for it.
     */
    const val PREVIEW_MAX_DIMENSION_PX = 1024

    fun decodeDownscaled(imagePath: String, maxDimensionPx: Int = MAX_DIMENSION_PX): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imagePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDimensionPx)
        }
        return BitmapFactory.decodeFile(imagePath, options)
    }

    /**
     * The same bound for an image that is already in memory — a receipt fetched
     * from cloud storage. The encoded bytes are small; it is the decoded bitmap
     * that is not, so the stream is read first and decoded in two passes.
     */
    fun decodeDownscaled(bytes: ByteArray, maxDimensionPx: Int = MAX_DIMENSION_PX): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDimensionPx)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    /**
     * The largest power-of-two subsampling that keeps the longer side at or above
     * [maxDimensionPx] — so the result is never smaller than asked for, and at most
     * just under twice as large.
     */
    fun sampleSizeFor(width: Int, height: Int, maxDimensionPx: Int): Int {
        var sampleSize = 1
        while (maxOf(width, height) / (sampleSize * 2) >= maxDimensionPx) {
            sampleSize *= 2
        }
        return sampleSize
    }
}
