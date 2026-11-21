package com.wantox86.signpdf.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class DocumentStatus {
    DRAFT,
    SIGNED
}

@Serializable
data class RecentDocument(
    val uri: String,
    val fileName: String,
    val sizeBytes: Long,
    val lastOpenedAtMillis: Long,
    val status: DocumentStatus = DocumentStatus.DRAFT
)
