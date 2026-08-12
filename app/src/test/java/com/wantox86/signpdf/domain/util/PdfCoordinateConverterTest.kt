package com.wantox86.signpdf.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfCoordinateConverterTest {

    @Test
    fun `computeScale returns 1 to 1 when rendered width matches page width in points`() {
        // Halaman A4 potret (595 x 842 pt), di-render persis selebar page width dalam px
        val scale = PdfCoordinateConverter.computeScale(
            pageWidthPt = 595f,
            pageHeightPt = 842f,
            renderedWidthPx = 595f
        )

        assertEquals(1f, scale.scaleX, 0.0001f)
        assertEquals(1f, scale.scaleY, 0.0001f)
    }

    @Test
    fun `computeScale shrinks proportionally when rendered width larger than page width`() {
        // Render lebar 1080px buat halaman 595pt lebar -> scaleX harus < 1 (piksel lebih "rapat")
        val scale = PdfCoordinateConverter.computeScale(
            pageWidthPt = 595f,
            pageHeightPt = 842f,
            renderedWidthPx = 1080f
        )

        assertEquals(595f / 1080f, scale.scaleX, 0.0001f)
        // Aspect ratio halaman dipertahankan -> scaleY harus sama dengan scaleX
        assertEquals(scale.scaleX, scale.scaleY, 0.0001f)
    }

    @Test
    fun `toPdfX scales overlay x by scaleX`() {
        val pdfX = PdfCoordinateConverter.toPdfX(overlayXPx = 200f, scaleX = 0.5f)
        assertEquals(100f, pdfX, 0.0001f)
    }

    @Test
    fun `toPdfY flips origin from top-left android to bottom-left pdf`() {
        // Halaman tinggi 842pt, overlay di y=0 (paling atas layar) tinggi 100px, scaleY=1
        // -> harus nempel di bagian ATAS pdf, artinya pdfY = pageHeight - overlayHeight
        val pdfY = PdfCoordinateConverter.toPdfY(
            pageHeightPt = 842f,
            overlayYPx = 0f,
            overlayHeightPx = 100f,
            scaleY = 1f
        )
        assertEquals(742f, pdfY, 0.0001f)
    }

    @Test
    fun `toPdfY places overlay at bottom of page when y plus height fills the page`() {
        // overlay nempel di paling bawah layar (y + height == page height dalam px)
        val pdfY = PdfCoordinateConverter.toPdfY(
            pageHeightPt = 842f,
            overlayYPx = 742f,
            overlayHeightPx = 100f,
            scaleY = 1f
        )
        assertEquals(0f, pdfY, 0.0001f)
    }
}
