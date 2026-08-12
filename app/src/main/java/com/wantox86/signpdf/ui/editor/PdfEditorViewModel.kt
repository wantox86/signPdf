package com.wantox86.signpdf.ui.editor

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
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

sealed class ExportState {
    data object Idle : ExportState()
    data object Loading : ExportState()
    // diagnostics: log mentah dari EmbedSignatureToPdfUseCase (config bitmap, hasil
    // PDImageXObject, koordinat final vs batas halaman) -- buat nelusurin laporan "overlay
    // ke-embed tapi nggak kelihatan di preview" langsung dari device asli, tanpa adb/logcat.
    // firstSignedPage: halaman pertama yang ada overlay-nya, biar Preview auto-scroll ke situ
    // (bukan selalu mulai dari halaman 1) kalau TTD-nya ada di halaman bawah.
    data class Success(val file: File, val diagnostics: String, val firstSignedPage: Int) : ExportState()
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
    private val _documentTitle = MutableStateFlow("")
    val documentTitle: StateFlow<String> = _documentTitle

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

                val fileName = resolveDisplayName(context, uri)
                pdfDocument = PdfDocument(uri, fileName, pageCount)
                _documentTitle.value = fileName
                _pages.value = List(pageCount) { null }
                renderAllPages()
            } catch (e: Exception) {
                _loadError.value = e.message ?: context.getString(R.string.error_pdf_load)
            }
        }
    }

    // uri.lastPathSegment buat content:// URI dari file picker (SAF) itu document ID internal
    // provider (mis. "document:1000115680"), BUKAN nama file asli -- nggak ada ekstensi yang
    // bener, jadi begitu di-share app lain (WhatsApp/Telegram) nebak jadi .bin. Query beneran ke
    // ContentResolver (OpenableColumns.DISPLAY_NAME) itu cara yang benar buat dapetin nama file
    // asli + ekstensinya.
    private fun resolveDisplayName(context: Context, uri: Uri): String {
        val queried = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else null
            }
        val name = queried ?: uri.lastPathSegment ?: "document.pdf"
        return if (name.endsWith(".pdf", ignoreCase = true)) name else "$name.pdf"
    }

    // Render semua halaman upfront pas dokumen dibuka, bukan lazy/windowed pas discroll --
    // pendekatan lazy sebelumnya bikin halaman yang baru kelihatan pas discroll sempet nunjukin
    // skeleton dulu sebelum bitmap-nya kelar dirender (async), yang keliatan kayak "TTD hilang
    // muncul". PDF yang ditandatangan biasanya cuma sekian halaman (bukan ratusan), jadi trade-off
    // pake lebih banyak memori upfront demi scroll yang mulus itu wajar buat use-case ini.
    private fun renderAllPages() {
        val document = pdfDocument ?: return
        viewModelScope.launch {
            _isLoadingPages.value = true
            val mutablePages = _pages.value.toMutableList()
            for (index in mutablePages.indices) {
                mutablePages[index] = pdfRepository.renderPage(document, index)
                // Emit progresif per halaman (bukan nunggu semua kelar) biar halaman yang udah
                // jadi langsung kelihatan, nggak nunggu dokumen 20 halaman kelar semua dulu.
                _pages.value = mutablePages.toList()
            }
            _isLoadingPages.value = false
        }
    }

    fun addOverlay(type: OverlayType, bitmap: Bitmap, pageIndex: Int) {
        // pageBitmap null berarti halaman ini belum kelar dirender -- Fragment nge-disable
        // tombol tambah selama isLoadingPages true jadi ini SEHARUSNYA nggak kejadian, tapi
        // kalau toh kejadian, mending nggak nambahin overlay sama sekali daripada nebak ukuran
        // halaman (fallback lama nebak ukuran potrait, ngaco parah buat dokumen landscape --
        // itu akar bug "overlay ke-taruh jauh di luar halaman" yang kejadian pas nambah sign di
        // halaman bawah yang belum sempet kerender).
        val pageBitmap = _pages.value.getOrNull(pageIndex) ?: return
        val pageWidth = pageBitmap.width.toFloat()
        val pageHeight = pageBitmap.height.toFloat()
        val defaultWidth = pageWidth * 0.18f
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
                val overlaysToEmbed = _overlays.value
                val result = exportPdfUseCase.execute(document, overlaysToEmbed)
                pdfDocument = result.document
                val firstSignedPage = overlaysToEmbed.minOfOrNull { it.pageIndex } ?: 0
                _exportState.value = ExportState.Success(result.file, result.diagnostics, firstSignedPage)
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
