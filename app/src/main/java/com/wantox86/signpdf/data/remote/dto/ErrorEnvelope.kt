package com.wantox86.signpdf.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ErrorEnvelope(
    val error: ErrorBody,
)

@Serializable
data class ErrorBody(
    val code: String,
    val message: String,
)
