package com.wantox86.signpdf.domain.usecase

import android.content.Context
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.util.Matrix
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
                val mediaBoxWidth = page.mediaBox.width
                val mediaBoxHeight = page.mediaBox.height

                // page.rotation itu properti tampilan (/Rotate di PDF) yang otomatis diikutin
                // PDFRenderer pas render buat editor/preview -- overlay.x/y user taruh relatif ke
                // hasil render itu (as-displayed, udah keputer), bukan ke raw content stream
                // (belum keputer). Tanpa kompensasi ini, overlay ke-gambar di ruang koordinat
                // yang salah -- bisa jauh di luar halaman kalau rotasinya 90/270 (lebar & tinggi
                // ketuker), makanya "ke-embed" (overlayCount > 0) tapi nggak pernah kelihatan.
                val rotation = ((page.rotation % 360) + 360) % 360
                val (displayWidth, displayHeight) = if (rotation == 90 || rotation == 270) {
                    mediaBoxHeight to mediaBoxWidth
                } else {
                    mediaBoxWidth to mediaBoxHeight
                }

                // RenderPdfPageUseCase selalu ngitung dpi dari mediaBoxWidth RAW (belum
                // dirotasi) -- buat halaman rotasi 90/270, lebar bitmap HASIL render yang
                // beneran dipake user nempatin overlay ikut ke-swap proporsional, bukan lagi
                // persis RENDER_WIDTH_PX konstan.
                val actualRenderedWidthPx = displayWidth * renderedWidth / mediaBoxWidth

                val scale = PdfCoordinateConverter.computeScale(
                    pageWidthPt = displayWidth,
                    pageHeightPt = displayHeight,
                    renderedWidthPx = actualRenderedWidthPx
                )

                val contentStream = PDPageContentStream(
                    pdfDoc,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                )

                // Transform standar buat "nulis pake koordinat as-displayed di atas page yang
                // punya /Rotate" -- setelah ini, drawImage() di bawah bisa pura-pura halamannya
                // nggak diputer sama sekali.
                when (rotation) {
                    90 -> contentStream.transform(Matrix(0f, 1f, -1f, 0f, mediaBoxWidth, 0f))
                    180 -> contentStream.transform(Matrix(-1f, 0f, 0f, -1f, mediaBoxWidth, mediaBoxHeight))
                    270 -> contentStream.transform(Matrix(0f, -1f, 1f, 0f, 0f, mediaBoxHeight))
                }

                pageOverlays.forEach { overlay ->
                    val pdImage = LosslessFactory.createFromImage(pdfDoc, overlay.bitmap)
                    val pdfX = PdfCoordinateConverter.toPdfX(overlay.x, scale.scaleX)
                    val pdfY = PdfCoordinateConverter.toPdfY(
                        pageHeightPt = displayHeight,
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
