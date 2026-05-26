package com.wantox86.signpdf.ui.editor

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.wantox86.signpdf.data.PdfRepository
import com.wantox86.signpdf.domain.model.OverlayType
import com.wantox86.signpdf.domain.model.PdfDocument
import com.wantox86.signpdf.domain.model.SignatureOverlay
import com.wantox86.signpdf.domain.usecase.EmbedSignatureToPdfUseCase
import com.wantox86.signpdf.domain.usecase.ExportPdfUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed class ExportState {
    data object Idle : ExportState()
    data object Loading : ExportState()
    data class Success(val file: File) : ExportState()
    data class Error(val message: String) : ExportState()
}

class PdfEditorViewModel(app: Application) : AndroidViewModel(app) {
    private val pdfRepository = PdfRepository(app)
    private val embedSignatureToPdfUseCase = EmbedSignatureToPdfUseCase(app)
    private val exportPdfUseCase = ExportPdfUseCase(embedSignatureToPdfUseCase)
    private val _pages = MutableStateFlow<List<Bitmap>>(emptyList())
    val pages: StateFlow<List<Bitmap>> = _pages
    private val _overlays = MutableStateFlow<List<SignatureOverlay>>(emptyList())
    val overlays: StateFlow<List<SignatureOverlay>> = _overlays
    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState

    private var pdfDocument: PdfDocument? = null

    fun loadPdf(uriString: String) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val uri = Uri.parse(uriString)
            val pdfDoc = PDDocument.load(context.contentResolver.openInputStream(uri))
            val pageCount = pdfDoc.numberOfPages
            pdfDoc.close()
            val fileName = uri.lastPathSegment ?: "document.pdf"
            pdfDocument = PdfDocument(uri, fileName, pageCount)
            val bitmaps = mutableListOf<Bitmap>()
            for (i in 0 until pageCount) {
                val bitmap = pdfRepository.renderPage(pdfDocument!!, i)
                bitmaps.add(bitmap)
            }
            _pages.value = bitmaps
        }
    }

    fun addOverlay(type: OverlayType, bitmap: Bitmap, pageIndex: Int) {
        val pageBitmap = _pages.value.getOrNull(pageIndex) ?: return
        val pageWidth = pageBitmap.width.toFloat()
        val pageHeight = pageBitmap.height.toFloat()
        val defaultWidth = pageWidth * 0.30f
        val aspectRatio = if (bitmap.width > 0) bitmap.height.toFloat() / bitmap.width.toFloat() else 0.35f
        val defaultHeight = defaultWidth * aspectRatio

        val overlay = SignatureOverlay(
            type = type,
            bitmap = bitmap,
            pageIndex = pageIndex,
            x = (pageWidth - defaultWidth) / 2f,
            y = (pageHeight - defaultHeight) / 2f,
            width = defaultWidth,
            height = defaultHeight
        )

        _overlays.value = _overlays.value + overlay
    }

    fun updateOverlays(newOverlays: List<SignatureOverlay>) {
        _overlays.value = newOverlays
    }

    fun exportAndShare() {
        val document = pdfDocument ?: run {
            _exportState.value = ExportState.Error("Dokumen PDF belum dimuat")
            return
        }

        viewModelScope.launch {
            _exportState.value = ExportState.Loading
            try {
                val (updatedDocument, outputFile) = exportPdfUseCase.execute(document, _overlays.value)
                pdfDocument = updatedDocument
                _exportState.value = ExportState.Success(outputFile)
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(e.message ?: "Gagal menyimpan PDF")
            }
        }
    }

    fun resetExportState() {
        _exportState.value = ExportState.Idle
    }
}
