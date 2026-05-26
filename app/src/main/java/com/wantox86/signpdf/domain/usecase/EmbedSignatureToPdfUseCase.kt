package com.wantox86.signpdf.domain.usecase

import android.content.Context
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.wantox86.signpdf.domain.model.PdfDocument
import com.wantox86.signpdf.domain.model.SignatureOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class EmbedSignatureToPdfUseCase(private val context: Context) {

    suspend fun execute(document: PdfDocument, overlays: List<SignatureOverlay>): File =
        withContext(Dispatchers.IO) {
            val inputStream = context.contentResolver.openInputStream(document.uri)
                ?: throw IllegalArgumentException("Cannot open input stream for PDF URI")

            val pdfDoc = PDDocument.load(inputStream)
            val renderedWidth = RenderPdfPageUseCase.RENDER_WIDTH_PX.toFloat()

            overlays.groupBy { it.pageIndex }.forEach { (pageIndex, pageOverlays) ->
                val page = pdfDoc.getPage(pageIndex)
                val scaleX = page.mediaBox.width / renderedWidth
                val renderedHeight = renderedWidth * (page.mediaBox.height / page.mediaBox.width)
                val scaleY = page.mediaBox.height / renderedHeight

                val contentStream = PDPageContentStream(
                    pdfDoc,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                )

                pageOverlays.forEach { overlay ->
                    val pdImage = LosslessFactory.createFromImage(pdfDoc, overlay.bitmap)
                    val pdfX = overlay.x * scaleX
                    val pdfY = page.mediaBox.height - (overlay.y * scaleY) - (overlay.height * scaleY)
                    contentStream.drawImage(
                        pdImage,
                        pdfX,
                        pdfY,
                        overlay.width * scaleX,
                        overlay.height * scaleY
                    )
                }

                contentStream.close()
            }

            val outputFile = File(context.filesDir, "signed_${document.fileName}")
            pdfDoc.save(outputFile)
            pdfDoc.close()
            outputFile
        }
}
