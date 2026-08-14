package com.wantox86.signpdf.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SignatureDto(
    val id: Long,
    val type: String,
    val name: String,
    val data: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
)

@Serializable
data class SignatureListResponse(
    val signatures: List<SignatureDto>,
    val initials: List<SignatureDto>,
)

@Serializable
data class CreateSignatureRequest(
    val type: String,
    val name: String,
    val data: String,
)

@Serializable
data class UpdateSignatureRequest(
    val name: String,
    val data: String,
)
