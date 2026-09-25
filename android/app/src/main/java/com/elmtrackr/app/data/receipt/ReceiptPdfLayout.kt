package com.elmtrackr.app.data.receipt

/**
 * How a PDF receipt is sized before it is drawn into the JPEG the OCR engines
 * already know how to read.
 *
 * PdfRenderer reports page size in points, about 600 by 800 for a page. Drawn
 * at that size the Hebrew type is too small. Drawn at full photo size, three
 * pages blow the memory budget. One page is rendered large; extra pages share
 * a shorter long side so the stack still fits.
 */
internal object ReceiptPdfLayout {

    const val MAX_PAGES = 3
    const val SINGLE_PAGE_LONG_SIDE = 2048
    const val MULTI_PAGE_LONG_SIDE = 1600
    const val PAGE_GAP_PX = 24

    fun pagesToRender(pageCount: Int): Int = pageCount.coerceAtLeast(0).coerceAtMost(MAX_PAGES)

    fun renderSize(pageWidth: Int, pageHeight: Int, pageCount: Int): Pair<Int, Int> {
        require(pageWidth > 0 && pageHeight > 0) { "Page must have a positive size" }
        val target = if (pagesToRender(pageCount) <= 1) SINGLE_PAGE_LONG_SIDE else MULTI_PAGE_LONG_SIDE
        return if (pageWidth >= pageHeight) {
            target to maxOf(1, (pageHeight.toLong() * target / pageWidth).toInt())
        } else {
            maxOf(1, (pageWidth.toLong() * target / pageHeight).toInt()) to target
        }
    }
}
