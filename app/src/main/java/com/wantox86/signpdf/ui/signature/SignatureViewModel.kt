package com.wantox86.signpdf.ui.signature

import android.app.Application
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.request.ImageRequest
import com.wantox86.signpdf.SignPdfApplication
import com.wantox86.signpdf.domain.model.OverlayType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SignatureViewModel(application: Application) : AndroidViewModel(application) {
    // Shared app-wide instance (see SignPdfApplication) -- not `new`'d here, so a Sync
    // triggered from Home (a different ViewModel) reuses the same in-memory StateFlows this
    // screen observes, instead of drifting out of sync until the next process restart.
    private val repository = (application as SignPdfApplication).signatureRepository

    val ttdBitmap: StateFlow<Bitmap?> = repository.ttdBitmap
    val parafBitmap: StateFlow<Bitmap?> = repository.parafBitmap

    init {
        viewModelScope.launch {
            repository.restoreSavedBitmaps()
        }
    }

    fun saveTtd(bitmap: Bitmap) {
        viewModelScope.launch {
            repository.saveBitmap(OverlayType.TTD, bitmap)
        }
    }

    fun saveParaf(bitmap: Bitmap) {
        viewModelScope.launch {
            repository.saveBitmap(OverlayType.PARAF, bitmap)
        }
    }

    fun savePendingBitmap(bitmap: Bitmap, type: OverlayType) {
        viewModelScope.launch {
            repository.saveBitmap(type, bitmap)
        }
    }

    fun importFromUri(uri: Uri, type: OverlayType) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request).drawable
                (result as? BitmapDrawable)?.bitmap
            }
            loaded?.let {
                repository.saveBitmap(type, it.copy(Bitmap.Config.ARGB_8888, true))
            }
        }
    }
}
