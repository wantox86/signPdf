package com.wantox86.signpdf.domain.model

import android.graphics.Bitmap
import android.net.Uri

sealed class SignatureSource {
    data class FromCanvas(val bitmap: Bitmap) : SignatureSource()
    data class FromImageFile(val uri: Uri) : SignatureSource()
}
