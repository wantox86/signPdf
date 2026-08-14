package com.wantox86.signpdf.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.wantox86.signpdf.domain.model.OverlayType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

class SignatureRepository(private val context: Context) {
    private val _ttdBitmap = MutableStateFlow<Bitmap?>(null)
    val ttdBitmap: StateFlow<Bitmap?> = _ttdBitmap

    private val _parafBitmap = MutableStateFlow<Bitmap?>(null)
    val parafBitmap: StateFlow<Bitmap?> = _parafBitmap

    suspend fun restoreSavedBitmaps() = withContext(Dispatchers.IO) {
        _ttdBitmap.value = loadBitmap(OverlayType.TTD)
        _parafBitmap.value = loadBitmap(OverlayType.PARAF)
    }

    suspend fun saveBitmap(type: OverlayType, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        val normalizedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        saveBitmapToFile(type, normalizedBitmap)
        when (type) {
            OverlayType.TTD -> _ttdBitmap.value = normalizedBitmap
            OverlayType.PARAF -> _parafBitmap.value = normalizedBitmap
        }
    }

    private fun signatureFile(type: OverlayType): File {
        val signaturesDir = File(context.filesDir, "signatures").apply { mkdirs() }
        val fileName = when (type) {
            OverlayType.TTD -> "ttd_signature.png"
            OverlayType.PARAF -> "paraf_signature.png"
        }
        return File(signaturesDir, fileName)
    }

    private fun saveBitmapToFile(type: OverlayType, bitmap: Bitmap) {
        val file = signatureFile(type)
        file.outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    private fun loadBitmap(type: OverlayType): Bitmap? {
        val file = signatureFile(type)
        if (!file.exists()) return null
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val loaded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        return loaded.copy(Bitmap.Config.ARGB_8888, true)
    }

    // --- Cloud sync additions below (SyncRepository only) ---------------------------------
    // All three are thin: they reuse the existing save/file logic above rather than
    // duplicating it, so Guest mode (which never calls any of them) stays byte-for-byte
    // unchanged.

    // Decodes a PNG downloaded from the backend and saves it exactly like a locally-drawn
    // signature would be.
    suspend fun saveBitmapFromSync(type: OverlayType, pngBytes: ByteArray) = withContext(Dispatchers.IO) {
        val bitmap = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)
            ?: throw IllegalArgumentException("Invalid PNG data for $type")
        saveBitmap(type, bitmap)
    }

    // Raw PNG bytes straight from disk (not re-encoded via Bitmap.compress) so what gets
    // uploaded is byte-identical to what's stored locally.
    suspend fun bitmapBytesFor(type: OverlayType): ByteArray? = withContext(Dispatchers.IO) {
        val file = signatureFile(type)
        if (!file.exists()) null else file.readBytes()
    }

    // Local file mtime, used by SyncRepository as a cheap "changed since last sync" signal
    // without needing saveBitmap() to know anything about sync state.
    suspend fun lastModifiedAt(type: OverlayType): Long? = withContext(Dispatchers.IO) {
        val file = signatureFile(type)
        if (!file.exists()) null else file.lastModified()
    }
}
