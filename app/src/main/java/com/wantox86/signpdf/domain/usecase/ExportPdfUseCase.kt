package com.wantox86.signpdf.domain.usecase

import com.wantox86.signpdf.domain.model.PdfDocument
import com.wantox86.signpdf.domain.model.SignatureOverlay
import java.io.File

class ExportPdfUseCase(
    private val embedSignatureToPdfUseCase: EmbedSignatureToPdfUseCase
) {
    suspend fun execute(document: PdfDocument, overlays: List<SignatureOverlay>): Pair<PdfDocument, File> {
        val output = embedSignatureToPdfUseCase.execute(document, overlays)
        val updated = document.copy(outputPath = output.absolutePath)
        return updated to output
    }
}
