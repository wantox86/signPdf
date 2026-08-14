package com.wantox86.signpdf.domain.model

import kotlinx.serialization.Serializable

// Sync bookkeeping for one local signature slot (TTD or PARAF), persisted via
// SignatureMetadataStore. Absence of an entry entirely (store returns null) means "never
// synced" -- treated as dirty by SyncRepository.
//
// "Dirty" isn't stored as an explicit flag: the normal signature-drawing flow
// (SignatureViewModel -> SignatureRepository.saveBitmap) is untouched by this feature and
// has no reason to know about sync bookkeeping. Instead, localFileModifiedAtMillis snapshots
// the local file's mtime as of the last successful sync; SyncRepository compares that against
// the file's current mtime to detect an unsynced local edit.
@Serializable
data class SignatureSlotMeta(
    val remoteId: Long? = null,
    // ISO-8601 / RFC3339 updated_at as returned by the backend; null until the first
    // successful upload.
    val updatedAt: String? = null,
    val localFileModifiedAtMillis: Long? = null,
)
