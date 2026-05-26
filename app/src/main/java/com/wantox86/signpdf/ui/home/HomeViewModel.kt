package com.wantox86.signpdf.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    private val _openPdfEvent = MutableSharedFlow<Uri>()
    val openPdfEvent: SharedFlow<Uri> = _openPdfEvent

    fun openPdf(uri: Uri) {
        viewModelScope.launch {
            _openPdfEvent.emit(uri)
        }
    }
}
