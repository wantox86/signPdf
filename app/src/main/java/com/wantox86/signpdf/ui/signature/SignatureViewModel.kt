package com.wantox86.signpdf.ui.signature

import android.app.Application
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.request.ImageRequest
import com.wantox86.signpdf.data.SignatureRepository
import com.wantox86.signpdf.domain.model.OverlayType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SignatureViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SignatureRepository()

    val ttdBitmap: StateFlow<Bitmap?> = repository.ttdBitmap
    val parafBitmap: StateFlow<Bitmap?> = repository.parafBitmap

    fun saveTtd(bitmap: Bitmap) {
        repository.saveBitmap(OverlayType.TTD, bitmap)
    }

    fun saveParaf(bitmap: Bitmap) {
        repository.saveBitmap(OverlayType.PARAF, bitmap)
    }

    fun savePendingBitmap(bitmap: Bitmap, type: OverlayType) {
        repository.saveBitmap(type, bitmap)
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
            loaded?.let { repository.saveBitmap(type, it.copy(Bitmap.Config.ARGB_8888, true)) }
        }
    }
}
