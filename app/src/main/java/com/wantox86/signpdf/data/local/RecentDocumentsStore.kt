package com.wantox86.signpdf.data.local

import android.content.Context
import com.wantox86.signpdf.domain.model.DocumentStatus
import com.wantox86.signpdf.domain.model.RecentDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Lightweight local history of recently opened/signed PDFs -- plain SharedPreferences + JSON
// (same pattern as SignatureMetadataStore), capped at MAX_RECENT_ITEMS so Room isn't needed.
class RecentDocumentsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _recentDocuments = MutableStateFlow(load())
    val recentDocuments: StateFlow<List<RecentDocument>> = _recentDocuments

    fun recordOpened(
        uri: String,
        fileName: String,
        sizeBytes: Long,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val current = _recentDocuments.value
        val existing = current.find { it.uri == uri }
        val updatedEntry = RecentDocument(
            uri = uri,
            fileName = fileName,
            sizeBytes = if (sizeBytes > 0L) sizeBytes else (existing?.sizeBytes ?: 0L),
            lastOpenedAtMillis = nowMillis,
            status = existing?.status ?: DocumentStatus.DRAFT
        )
        val updated = (listOf(updatedEntry) + current.filterNot { it.uri == uri })
            .sortedByDescending { it.lastOpenedAtMillis }
            .take(MAX_RECENT_ITEMS)
        persist(updated)
    }

    fun markSigned(uri: String, nowMillis: Long = System.currentTimeMillis()) {
        val current = _recentDocuments.value
        val existing = current.find { it.uri == uri } ?: return
        val updatedEntry = existing.copy(
            status = DocumentStatus.SIGNED,
            lastOpenedAtMillis = nowMillis
        )
        val updated = (listOf(updatedEntry) + current.filterNot { it.uri == uri })
            .sortedByDescending { it.lastOpenedAtMillis }
            .take(MAX_RECENT_ITEMS)
        persist(updated)
    }

    fun remove(uri: String) {
        val updated = _recentDocuments.value.filterNot { it.uri == uri }
        persist(updated)
    }

    fun clear() {
        persist(emptyList())
    }

    private fun load(): List<RecentDocument> {
        val raw = prefs.getString(KEY_RECENT_LIST, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<RecentDocument>>(raw) }
            .getOrDefault(emptyList())
            .sortedByDescending { it.lastOpenedAtMillis }
            .take(MAX_RECENT_ITEMS)
    }

    private fun persist(list: List<RecentDocument>) {
        prefs.edit().putString(KEY_RECENT_LIST, json.encodeToString(list)).apply()
        _recentDocuments.value = list
    }

    companion object {
        private const val PREFS_NAME = "signpdf_recent_documents"
        private const val KEY_RECENT_LIST = "recent_list"
        const val MAX_RECENT_ITEMS = 10
    }
}
