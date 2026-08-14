package com.wantox86.signpdf.ui.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wantox86.signpdf.SignPdfApplication
import com.wantox86.signpdf.domain.model.AuthState
import com.wantox86.signpdf.domain.model.OverlayType
import com.wantox86.signpdf.domain.model.SyncState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val signPdfApplication = app as SignPdfApplication
    private val authRepository = signPdfApplication.authRepository
    private val syncRepository = signPdfApplication.syncRepository

    val authState: StateFlow<AuthState> = authRepository.authState
    val syncState: StateFlow<SyncState> = syncRepository.syncState

    private val _openPdfEvent = MutableSharedFlow<Uri>()
    val openPdfEvent: SharedFlow<Uri> = _openPdfEvent

    // StateFlow, not a one-shot SharedFlow: Login runs on a separate screen that pops back to
    // Home immediately on success, tearing down and recreating Home's view (and whatever
    // collector was attached to it) across that navigation. A StateFlow always replays its
    // latest value to a freshly (re)attached collector, so this can't be silently dropped by
    // that race the way an unbuffered SharedFlow event could be.
    private val _migrationPrompt = MutableStateFlow<List<OverlayType>?>(null)
    val migrationPrompt: StateFlow<List<OverlayType>?> = _migrationPrompt

    // Same reasoning: true means "show a session-expired Snackbar", consumed (reset to
    // false) by the Fragment once it's actually shown one.
    private val _sessionExpiredEvent = MutableStateFlow(false)
    val sessionExpiredEvent: StateFlow<Boolean> = _sessionExpiredEvent

    private var previousAuthState: AuthState = authState.value
    private var loggingOut = false

    init {
        viewModelScope.launch {
            authState.collect { state ->
                val wasGuest = previousAuthState is AuthState.Guest
                val wasAuthenticated = previousAuthState is AuthState.Authenticated
                when {
                    wasGuest && state is AuthState.Authenticated -> onLoggedIn()
                    // Authenticated -> Guest without us having called logout() ourselves
                    // means AuthRepository.handleSessionExpired() fired (401 from a sync
                    // call) -- surface it, but never block the user from continuing to sign
                    // documents offline.
                    wasAuthenticated && state is AuthState.Guest && !loggingOut -> {
                        _sessionExpiredEvent.value = true
                    }
                }
                loggingOut = false
                previousAuthState = state
            }
        }
    }

    fun openPdf(uri: Uri) {
        viewModelScope.launch {
            _openPdfEvent.emit(uri)
        }
    }

    fun sync() {
        viewModelScope.launch {
            syncRepository.sync()
        }
    }

    fun logout() {
        loggingOut = true
        viewModelScope.launch {
            authRepository.logout()
        }
    }

    fun confirmMigrationUpload() {
        _migrationPrompt.value = null
        sync()
    }

    fun dismissMigrationPrompt() {
        _migrationPrompt.value = null
    }

    fun consumeSessionExpiredEvent() {
        _sessionExpiredEvent.value = false
    }

    private suspend fun onLoggedIn() {
        val unsynced = syncRepository.detectUnsyncedLocalOnly()
        if (unsynced.isNotEmpty()) {
            _migrationPrompt.value = unsynced
        } else {
            sync()
        }
    }
}
