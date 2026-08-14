package com.wantox86.signpdf.data.local

import android.content.Context
import com.wantox86.signpdf.domain.model.OverlayType
import com.wantox86.signpdf.domain.model.SignatureSlotMeta
import kotlinx.serialization.json.Json

// Non-secret sync bookkeeping (remote id / updated_at / dirty flag per slot) -- plain
// SharedPreferences + JSON, deliberately not Room: only two rows ever exist (TTD, PARAF),
// so a database is more machinery than the problem needs.
class SignatureMetadataStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun get(type: OverlayType): SignatureSlotMeta? {
        val raw = prefs.getString(key(type), null) ?: return null
        return runCatching { json.decodeFromString<SignatureSlotMeta>(raw) }.getOrNull()
    }

    fun put(type: OverlayType, meta: SignatureSlotMeta) {
        prefs.edit().putString(key(type), json.encodeToString(meta)).apply()
    }

    fun clear(type: OverlayType) {
        prefs.edit().remove(key(type)).apply()
    }

    private fun key(type: OverlayType) = "meta_${type.name}"

    companion object {
        private const val PREFS_NAME = "signpdf_signature_metadata"
    }
}
