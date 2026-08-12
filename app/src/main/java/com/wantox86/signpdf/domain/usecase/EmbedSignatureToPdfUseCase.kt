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

    // File + log diagnostic mentah (config bitmap, hasil PDImageXObject, koordinat final vs
    // batas halaman) -- dipake buat nelusurin laporan "overlay ke-embed (count > 0) tapi nggak
    // kelihatan" tanpa perlu adb/logcat, langsung dari device asli.
    data class EmbedResult(val file: File, val diagnostics: String)

    suspend fun execute(document: PdfDocument, overlays: List<SignatureOverlay>): EmbedResult =
        withContext(Dispatchers.IO) {
            val log = StringBuilder()
            log.appendLine("Overlays to embed: ${overlays.size}")

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

                log.appendLine("Page $pageIndex: mediaBox=${mediaBoxWidth}x${mediaBoxHeight}pt rotation=$rotation displayWH=${displayWidth}x${displayHeight}pt")
                log.appendLine("  actualRenderedWidthPx=$actualRenderedWidthPx scaleX=${scale.scaleX} scaleY=${scale.scaleY}")

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
                    log.appendLine(
                        "  overlay ${overlay.id.take(8)}: bitmap=${overlay.bitmap.width}x${overlay.bitmap.height} " +
                            "config=${overlay.bitmap.config} hasAlpha=${overlay.bitmap.hasAlpha()} " +
                            "isRecycled=${overlay.bitmap.isRecycled}"
                    )
                    val pdImage = LosslessFactory.createFromImage(pdfDoc, overlay.bitmap)
                    log.appendLine("    PDImageXObject created: ${pdImage.width}x${pdImage.height} isStencil=${pdImage.isStencil}")

                    val pdfX = PdfCoordinateConverter.toPdfX(overlay.x, scale.scaleX)
                    val pdfY = PdfCoordinateConverter.toPdfY(
                        pageHeightPt = displayHeight,
                        overlayYPx = overlay.y,
                        overlayHeightPx = overlay.height,
                        scaleY = scale.scaleY
                    )
                    val drawWidth = overlay.width * scale.scaleX
                    val drawHeight = overlay.height * scale.scaleY
                    val inBounds = pdfX >= 0 && pdfY >= 0 &&
                        (pdfX + drawWidth) <= displayWidth && (pdfY + drawHeight) <= displayHeight
                    log.appendLine(
                        "    placed at pdfX=$pdfX pdfY=$pdfY w=$drawWidth h=$drawHeight " +
                            "(page bounds 0..$displayWidth x 0..$displayHeight) fully in-bounds: $inBounds"
                    )

                    // Safety net terakhir: SignatureOverlayView udah clamp overlay ke batas
                    // halaman pas drag/resize (lihat fixing-signing.md), tapi kalau toh ada
                    // celah lain yang lolos, mending gambar tetep kepaksa di dalam kertas
                    // (clamped) daripada diem-diem ngegambar di luar halaman -- itu yang bikin
                    // overlay "ke-embed" (count > 0) tapi invisible di preview.
                    val clampedWidth = drawWidth.coerceAtMost(displayWidth)
                    val clampedHeight = drawHeight.coerceAtMost(displayHeight)
                    val clampedX = pdfX.coerceIn(0f, (displayWidth - clampedWidth).coerceAtLeast(0f))
                    val clampedY = pdfY.coerceIn(0f, (displayHeight - clampedHeight).coerceAtLeast(0f))
                    if (!inBounds) {
                        log.appendLine("    CLAMPED to pdfX=$clampedX pdfY=$clampedY w=$clampedWidth h=$clampedHeight")
                    }

                    contentStream.drawImage(pdImage, clampedX, clampedY, clampedWidth, clampedHeight)
                }

                contentStream.close()
            }

            val outputFile = File(context.filesDir, "signed_${document.fileName}")
            pdfDoc.save(outputFile)
            pdfDoc.close()
            log.appendLine("Saved to: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
            EmbedResult(outputFile, log.toString())
        }
}
