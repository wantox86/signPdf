package com.wantox86.signpdf.domain.usecase

import com.wantox86.signpdf.domain.model.PdfDocument
import com.wantox86.signpdf.domain.model.SignatureOverlay
import java.io.File

class ExportPdfUseCase(
    private val embedSignatureToPdfUseCase: EmbedSignatureToPdfUseCase
) {
    data class Result(val document: PdfDocument, val file: File, val diagnostics: String)

    suspend fun execute(document: PdfDocument, overlays: List<SignatureOverlay>): Result {
        val embedResult = embedSignatureToPdfUseCase.execute(document, overlays)
        val updated = document.copy(outputPath = embedResult.file.absolutePath)
        return Result(updated, embedResult.file, embedResult.diagnostics)
    }
}
