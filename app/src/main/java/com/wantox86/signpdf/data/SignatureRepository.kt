package com.wantox86.signpdf.data

import android.graphics.Bitmap
import com.wantox86.signpdf.domain.model.OverlayType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SignatureRepository {
    private val _ttdBitmap = MutableStateFlow<Bitmap?>(null)
    val ttdBitmap: StateFlow<Bitmap?> = _ttdBitmap

    private val _parafBitmap = MutableStateFlow<Bitmap?>(null)
    val parafBitmap: StateFlow<Bitmap?> = _parafBitmap

    fun saveBitmap(type: OverlayType, bitmap: Bitmap) {
        when (type) {
            OverlayType.TTD -> _ttdBitmap.value = bitmap
            OverlayType.PARAF -> _parafBitmap.value = bitmap
        }
    }
}
