package com.wantox86.signpdf.ui.editor

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.wantox86.signpdf.data.PdfRepository
import com.wantox86.signpdf.domain.model.PdfDocument
import com.wantox86.signpdf.domain.model.SignatureOverlay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PdfEditorViewModel(app: Application) : AndroidViewModel(app) {
    private val pdfRepository = PdfRepository(app)
    private val _pages = MutableStateFlow<List<Bitmap>>(emptyList())
    val pages: StateFlow<List<Bitmap>> = _pages
    private val _overlays = MutableStateFlow<List<SignatureOverlay>>(emptyList())
    val overlays: StateFlow<List<SignatureOverlay>> = _overlays

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
}
