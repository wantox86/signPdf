package com.wantox86.signpdf.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import com.wantox86.signpdf.domain.model.PdfDocument
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.ImageType
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RenderPdfPageUseCase(private val context: Context) {
    companion object {
        // Must match the width used in EmbedSignatureToPdfUseCase for correct coordinate mapping
        const val RENDER_WIDTH_PX = 1080
    }

    suspend fun execute(document: PdfDocument, pageIndex: Int): Bitmap =
        withContext(Dispatchers.IO) {
            val pdfDoc = PDDocument.load(
                context.contentResolver.openInputStream(document.uri)
            )
            val page = pdfDoc.getPage(pageIndex)
            val pageWidthInches = page.mediaBox.width.toFloat() / 72f // PDF points → inches (1 pt = 1/72 in)
            val dpi = RENDER_WIDTH_PX.toFloat() / pageWidthInches
            val renderer = PDFRenderer(pdfDoc)
            val bitmap = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.ARGB)
            pdfDoc.close()
            bitmap
        }
}
