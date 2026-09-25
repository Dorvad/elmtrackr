package com.elmtrackr.app.data.receipt

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Draws the first pages of a receipt PDF into one JPEG.
 *
 * The refund bucket accepts photos, not PDF bytes, and both OCR engines decode
 * a bitmap. A picked PDF is therefore rendered here and stored as the same
 * kind of file a scan already produces. Password-protected and corrupt files
 * fail closed so the caller can ask for a different one.
 */
internal object ReceiptPdfRasterizer {

    fun renderToJpeg(source: File, target: File): Boolean {
        if (!source.isFile || source.length() <= 0L) return false
        val descriptor = runCatching {
            ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
        }.getOrNull() ?: return false
        return try {
            descriptor.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val count = ReceiptPdfLayout.pagesToRender(renderer.pageCount)
                    if (count == 0) return false
                    val pages = ArrayList<Bitmap>(count)
                    try {
                        for (index in 0 until count) {
                            renderer.openPage(index).use { page ->
                                val (width, height) = ReceiptPdfLayout.renderSize(
                                    page.width,
                                    page.height,
                                    renderer.pageCount,
                                )
                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                pages.add(bitmap)
                            }
                        }
                        writeStackedJpeg(pages, target)
                    } finally {
                        pages.forEach { it.recycle() }
                    }
                }
            }
        } catch (_: RuntimeException) {
            target.delete()
            false
        }
    }

    private fun writeStackedJpeg(pages: List<Bitmap>, target: File): Boolean {
        val gap = if (pages.size > 1) ReceiptPdfLayout.PAGE_GAP_PX else 0
        val width = pages.maxOf { it.width }
        val height = pages.sumOf { it.height } + gap * (pages.size - 1)
        val combined = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            val canvas = Canvas(combined)
            canvas.drawColor(Color.WHITE)
            var top = 0f
            for (page in pages) {
                canvas.drawBitmap(page, 0f, top, null)
                top += page.height + gap
            }
            target.outputStream().use { output ->
                combined.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            }
            target.length() > 0L
        } finally {
            combined.recycle()
        }
    }

    private const val JPEG_QUALITY = 90
}
