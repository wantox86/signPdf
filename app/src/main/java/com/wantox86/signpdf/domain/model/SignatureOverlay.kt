package com.wantox86.signpdf.domain.model

import android.graphics.Bitmap
import java.util.UUID

data class SignatureOverlay(
    val id: String = UUID.randomUUID().toString(),
    val type: OverlayType,
    val bitmap: Bitmap,
    val pageIndex: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val createdAt: Long = System.currentTimeMillis()
)

enum class OverlayType { TTD, PARAF }
