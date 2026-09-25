package com.elmtrackr.app.data.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptPdfLayoutTest {

    @Test
    fun `a single page is rendered large enough for receipt type`() {
        val (width, height) = ReceiptPdfLayout.renderSize(pageWidth = 612, pageHeight = 792, pageCount = 1)

        assertEquals(ReceiptPdfLayout.SINGLE_PAGE_LONG_SIDE, maxOf(width, height))
        assertTrue(width > 612)
        assertTrue(height > 792)
    }

    @Test
    fun `extra pages use the shorter long side`() {
        val (_, height) = ReceiptPdfLayout.renderSize(pageWidth = 612, pageHeight = 792, pageCount = 3)

        assertEquals(ReceiptPdfLayout.MULTI_PAGE_LONG_SIDE, height)
    }

    @Test
    fun `only the first few pages are rendered`() {
        assertEquals(0, ReceiptPdfLayout.pagesToRender(0))
        assertEquals(1, ReceiptPdfLayout.pagesToRender(1))
        assertEquals(ReceiptPdfLayout.MAX_PAGES, ReceiptPdfLayout.pagesToRender(12))
    }

    @Test
    fun `a landscape page keeps its long side on the width`() {
        val (width, height) = ReceiptPdfLayout.renderSize(pageWidth = 792, pageHeight = 612, pageCount = 1)

        assertTrue(width > height)
        assertEquals(ReceiptPdfLayout.SINGLE_PAGE_LONG_SIDE, width)
    }
}
