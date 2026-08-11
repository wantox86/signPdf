package com.wantox86.signpdf.domain.usecase

import android.content.Context
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.wantox86.signpdf.domain.model.PdfDocument
import com.wantox86.signpdf.domain.model.SignatureOverlay
import com.wantox86.signpdf.domain.util.PdfCoordinateConverter
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
                val scale = PdfCoordinateConverter.computeScale(
                    pageWidthPt = page.mediaBox.width,
                    pageHeightPt = page.mediaBox.height,
                    renderedWidthPx = renderedWidth
                )

                val contentStream = PDPageContentStream(
                    pdfDoc,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                )

                pageOverlays.forEach { overlay ->
                    val pdImage = LosslessFactory.createFromImage(pdfDoc, overlay.bitmap)
                    val pdfX = PdfCoordinateConverter.toPdfX(overlay.x, scale.scaleX)
                    val pdfY = PdfCoordinateConverter.toPdfY(
                        pageHeightPt = page.mediaBox.height,
                        overlayYPx = overlay.y,
                        overlayHeightPx = overlay.height,
                        scaleY = scale.scaleY
                    )
                    contentStream.drawImage(
                        pdImage,
                        pdfX,
                        pdfY,
                        overlay.width * scale.scaleX,
                        overlay.height * scale.scaleY
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
