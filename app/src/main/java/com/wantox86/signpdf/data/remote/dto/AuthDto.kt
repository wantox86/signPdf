package com.wantox86.signpdf.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class LoginResponse(
    val token: String,
    // ISO-8601 / RFC3339, kept as raw String on the wire; parsed to java.time.Instant
    // only where actually compared (see SyncRepository in a later phase), to avoid
    // coupling every DTO consumer to a date library.
    @SerialName("expires_at")
    val expiresAt: String,
)
