package com.wantox86.signpdf.domain.util

/**
 * Konversi koordinat overlay (piksel Android, origin top-left, dari bitmap hasil render
 * PdfEditorFragment lebar tetap [RenderPdfPageUseCase.RENDER_WIDTH_PX]) ke koordinat PDF
 * (points, origin bottom-left) buat di-embed lewat PDFBox.
 */
object PdfCoordinateConverter {

    data class Scale(val scaleX: Float, val scaleY: Float)

    /**
     * @param pageWidthPt lebar halaman PDF asli dalam points (PDPage.mediaBox.width)
     * @param pageHeightPt tinggi halaman PDF asli dalam points (PDPage.mediaBox.height)
     * @param renderedWidthPx lebar bitmap hasil render (piksel)
     */
    fun computeScale(pageWidthPt: Float, pageHeightPt: Float, renderedWidthPx: Float): Scale {
        val scaleX = pageWidthPt / renderedWidthPx
        val renderedHeightPx = renderedWidthPx * (pageHeightPt / pageWidthPt)
        val scaleY = pageHeightPt / renderedHeightPx
        return Scale(scaleX, scaleY)
    }

    fun toPdfX(overlayXPx: Float, scaleX: Float): Float = overlayXPx * scaleX

    /**
     * @param overlayYPx posisi Y overlay dalam piksel, origin top-left (sistem Android)
     * @param overlayHeightPx tinggi overlay dalam piksel
     */
    fun toPdfY(pageHeightPt: Float, overlayYPx: Float, overlayHeightPx: Float, scaleY: Float): Float =
        pageHeightPt - (overlayYPx * scaleY) - (overlayHeightPx * scaleY)
}
