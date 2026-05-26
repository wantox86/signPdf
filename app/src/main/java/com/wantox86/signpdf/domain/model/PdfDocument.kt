package com.wantox86.signpdf.domain.model

import android.net.Uri

data class PdfDocument(
    val uri: Uri,
    val fileName: String,
    val pageCount: Int,
    val outputPath: String? = null
)
