package com.wantox86.signpdf.data

import android.content.Context
import android.graphics.Bitmap
import com.wantox86.signpdf.domain.model.PdfDocument
import com.wantox86.signpdf.domain.usecase.RenderPdfPageUseCase

class PdfRepository(private val context: Context) {
    private val renderPdfPageUseCase = RenderPdfPageUseCase(context)

    suspend fun renderPage(document: PdfDocument, pageIndex: Int): Bitmap {
        return renderPdfPageUseCase.execute(document, pageIndex)
    }
}
