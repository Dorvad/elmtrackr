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

    fun decodeDownscaled(imagePath: String, maxDimensionPx: Int = MAX_DIMENSION_PX): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imagePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxDimensionPx) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(imagePath, options)
    }
}
