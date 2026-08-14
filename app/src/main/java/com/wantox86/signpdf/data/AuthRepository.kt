package com.wantox86.signpdf.data

import android.content.Context
import com.wantox86.signpdf.R
import com.wantox86.signpdf.data.local.TokenStorage
import com.wantox86.signpdf.data.remote.SignPdfApiService
import com.wantox86.signpdf.data.remote.dto.ErrorEnvelope
import com.wantox86.signpdf.data.remote.dto.LoginRequest
import com.wantox86.signpdf.domain.model.AuthState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant

sealed class LoginResult {
    data object Success : LoginResult()
    data class Failure(val message: String) : LoginResult()
}

class AuthRepository(
    private val context: Context,
    private val apiService: SignPdfApiService,
    private val tokenStore: TokenStorage,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _authState = MutableStateFlow(initialAuthState())
    val authState: StateFlow<AuthState> = _authState

    private fun initialAuthState(): AuthState {
        val token = tokenStore.token()
        val username = tokenStore.username()
        val expiresAt = tokenStore.expiresAt()
        if (token == null || username == null || expiresAt == null) return AuthState.Guest
        val expiry = runCatching { Instant.parse(expiresAt) }.getOrNull()
        if (expiry == null || expiry.isBefore(Instant.now())) {
            tokenStore.clear()
            return AuthState.Guest
        }
        return AuthState.Authenticated(username)
    }

    suspend fun login(username: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        try {
            val response = apiService.login(LoginRequest(username, password))
            tokenStore.save(response.token, response.expiresAt, username)
            _authState.value = AuthState.Authenticated(username)
            LoginResult.Success
        } catch (e: HttpException) {
            LoginResult.Failure(extractErrorMessage(e))
        } catch (e: IOException) {
            LoginResult.Failure(context.getString(R.string.error_login_network))
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        // Best-effort: even if the network call fails, the local session still gets torn
        // down below, so Logout always works from the user's point of view.
        runCatching { apiService.logout() }
        tokenStore.clear()
        _authState.value = AuthState.Guest
    }

    // Called when a request comes back 401 SESSION_EXPIRED outside of an explicit user
    // action (e.g. from SyncRepository in phase 6) -- clears local session state only,
    // never touches SignatureRepository/SignatureMetadataStore.
    fun handleSessionExpired() {
        tokenStore.clear()
        _authState.value = AuthState.Guest
    }

    private fun extractErrorMessage(e: HttpException): String {
        val body = e.response()?.errorBody()?.string()
        val parsed = body?.let { runCatching { json.decodeFromString<ErrorEnvelope>(it) }.getOrNull() }
        return parsed?.error?.message ?: context.getString(R.string.error_login_generic)
    }
}
