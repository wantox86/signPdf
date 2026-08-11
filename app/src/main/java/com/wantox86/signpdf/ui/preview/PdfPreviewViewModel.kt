package com.wantox86.signpdf.ui.preview

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.wantox86.signpdf.R
import com.wantox86.signpdf.data.PdfRepository
import com.wantox86.signpdf.domain.model.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class PreviewLoadState {
    data object Loading : PreviewLoadState()
    data class Ready(val pages: List<Bitmap>) : PreviewLoadState()
    data class Error(val message: String) : PreviewLoadState()
}

class PdfPreviewViewModel(app: Application) : AndroidViewModel(app) {
    private val pdfRepository = PdfRepository(app)
    private val _loadState = MutableStateFlow<PreviewLoadState>(PreviewLoadState.Loading)
    val loadState: StateFlow<PreviewLoadState> = _loadState

    private var loadedForPath: String? = null

    fun loadPreview(filePath: String) {
        if (loadedForPath == filePath && _loadState.value is PreviewLoadState.Ready) return
        loadedForPath = filePath

        val context = getApplication<Application>()
        viewModelScope.launch {
            _loadState.value = PreviewLoadState.Loading
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    throw IllegalStateException(context.getString(R.string.error_preview_file_not_found))
                }

                val pageCount = withContext(Dispatchers.IO) {
                    val doc = PDDocument.load(file)
                    val count = doc.numberOfPages
                    doc.close()
                    count
                }

                val document = PdfDocument(Uri.fromFile(file), file.name, pageCount)
                val pages = (0 until pageCount).map { index ->
                    pdfRepository.renderPage(document, index)
                }
                _loadState.value = PreviewLoadState.Ready(pages)
            } catch (e: Exception) {
                _loadState.value = PreviewLoadState.Error(e.message ?: context.getString(R.string.error_preview_load))
            }
        }
    }
}
