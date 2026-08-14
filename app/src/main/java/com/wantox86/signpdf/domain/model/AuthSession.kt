package com.wantox86.signpdf.domain.model

sealed class AuthState {
    data object Guest : AuthState()
    data class Authenticated(val username: String) : AuthState()
}
