package com.wantox86.signpdf.ui.editor

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.wantox86.signpdf.R
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
import kotlin.math.abs

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
    private val _pages = MutableStateFlow<List<Bitmap?>>(emptyList())
    val pages: StateFlow<List<Bitmap?>> = _pages
    private val _overlays = MutableStateFlow<List<SignatureOverlay>>(emptyList())
    val overlays: StateFlow<List<SignatureOverlay>> = _overlays
    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState
    private val _isLoadingPages = MutableStateFlow(false)
    val isLoadingPages: StateFlow<Boolean> = _isLoadingPages
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError

    private var pdfDocument: PdfDocument? = null
    private val undoStack = ArrayDeque<List<SignatureOverlay>>()
    private val redoStack = ArrayDeque<List<SignatureOverlay>>()

    fun loadPdf(uriString: String) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            try {
                val uri = Uri.parse(uriString)
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException(context.getString(R.string.error_pdf_load))
                val pdfDoc = PDDocument.load(inputStream)
                val pageCount = pdfDoc.numberOfPages
                pdfDoc.close()

                if (pageCount <= 0) {
                    _loadError.value = context.getString(R.string.error_pdf_invalid)
                    return@launch
                }

                val fileName = uri.lastPathSegment ?: "document.pdf"
                pdfDocument = PdfDocument(uri, fileName, pageCount)
                _pages.value = List(pageCount) { null }
                _isLoadingPages.value = true
                updateVisiblePage(0)
            } catch (e: Exception) {
                _loadError.value = e.message ?: context.getString(R.string.error_pdf_load)
            }
        }
    }

    fun updateVisiblePage(currentIndex: Int) {
        val document = pdfDocument ?: return
        viewModelScope.launch {
            val mutablePages = _pages.value.toMutableList()
            if (mutablePages.isEmpty()) return@launch

            val start = (currentIndex - 1).coerceAtLeast(0)
            val end = (currentIndex + 1).coerceAtMost(mutablePages.lastIndex)
            var changed = false

            for (index in start..end) {
                if (mutablePages[index] == null) {
                    mutablePages[index] = pdfRepository.renderPage(document, index)
                    changed = true
                }
            }

            for (index in mutablePages.indices) {
                if (abs(index - currentIndex) > 2 && mutablePages[index] != null) {
                    // Jangan recycle() manual: nggak ada jaminan ImageView yang lagi nampilin
                    // bitmap ini udah selesai di-unbind/redraw duluan (adapter update dari
                    // _pages.value baru diproses RecyclerView belakangan, async), jadi bisa race
                    // -> "Canvas: trying to use a recycled bitmap" kalau sempat digambar ulang
                    // pas bitmap-nya udah kepanggil recycle(). Cukup drop referensinya, biarin GC
                    // yang bebasin memorinya begitu beneran nggak ada View yang megang lagi.
                    mutablePages[index] = null
                    changed = true
                }
            }

            // Cuma emit kalau beneran ada yang berubah -- tiap emit di sini nyampe ke adapter
            // dan bisa mancing rebind, jadi emit yang nggak perlu (mis. dipanggil ulang buat
            // index yang udah fully-loaded) bikin kerja dua kali sia-sia.
            if (changed) {
                _pages.value = mutablePages
            }
            // Loading indicator cuma refleksiin window yang lagi dibutuhin (start..end), bukan
            // seluruh dokumen -- halaman jauh yang sengaja di-unload (null) itu normal, bukan
            // "masih loading", jadi jangan ikut dihitung di sini.
            _isLoadingPages.value = (start..end).any { mutablePages[it] == null }
        }
    }

    fun addOverlay(type: OverlayType, bitmap: Bitmap, pageIndex: Int) {
        val pageBitmap = _pages.value.getOrNull(pageIndex)
        val pageWidth = pageBitmap?.width?.toFloat() ?: 1080f
        val pageHeight = pageBitmap?.height?.toFloat() ?: 1528f
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

        pushHistory()
        _overlays.value = _overlays.value + overlay
    }

    fun updateOverlays(newOverlays: List<SignatureOverlay>) {
        if (_overlays.value == newOverlays) return
        pushHistory()
        _overlays.value = newOverlays
    }

    fun undoOverlay() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(_overlays.value)
        _overlays.value = undoStack.removeLast()
    }

    fun redoOverlay() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(_overlays.value)
        _overlays.value = redoStack.removeLast()
    }

    fun export() {
        val context = getApplication<Application>()
        val document = pdfDocument ?: run {
            _exportState.value = ExportState.Error(context.getString(R.string.error_document_not_loaded))
            return
        }

        viewModelScope.launch {
            _exportState.value = ExportState.Loading
            try {
                val (updatedDocument, outputFile) = exportPdfUseCase.execute(document, _overlays.value)
                pdfDocument = updatedDocument
                _exportState.value = ExportState.Success(outputFile)
            } catch (e: Exception) {
                val reason = e.message ?: e.javaClass.simpleName
                _exportState.value = ExportState.Error("${context.getString(R.string.error_export)}: $reason")
            }
        }
    }

    fun clearLoadError() {
        _loadError.value = null
    }

    private fun pushHistory() {
        undoStack.addLast(_overlays.value)
        redoStack.clear()
    }

    fun resetExportState() {
        _exportState.value = ExportState.Idle
    }
}
