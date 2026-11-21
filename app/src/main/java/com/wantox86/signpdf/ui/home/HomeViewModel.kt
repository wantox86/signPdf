package com.wantox86.signpdf.ui.home

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wantox86.signpdf.SignPdfApplication
import com.wantox86.signpdf.data.AuthRepository
import com.wantox86.signpdf.data.SignatureRepository
import com.wantox86.signpdf.data.SyncRepository
import com.wantox86.signpdf.data.local.RecentDocumentsStore
import com.wantox86.signpdf.domain.model.AuthState
import com.wantox86.signpdf.domain.model.OverlayType
import com.wantox86.signpdf.domain.model.RecentDocument
import com.wantox86.signpdf.domain.model.SyncState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel @JvmOverloads constructor(
    app: Application,
    // Same @JvmOverloads seam as LoginViewModel: defaults to the app-wide singletons in
    // production (by viewModels() still finds the single-Application-arg constructor), tests
    // pass fakes/mocks directly.
    private val authRepository: AuthRepository = (app as SignPdfApplication).authRepository,
    private val syncRepository: SyncRepository = (app as SignPdfApplication).syncRepository,
    private val signatureRepository: SignatureRepository =
        (app as? SignPdfApplication)?.signatureRepository ?: SignatureRepository(app),
    private val recentDocumentsStore: RecentDocumentsStore =
        (app as? SignPdfApplication)?.recentDocumentsStore ?: RecentDocumentsStore(app),
) : AndroidViewModel(app) {
    val authState: StateFlow<AuthState> = authRepository.authState
    val syncState: StateFlow<SyncState> = syncRepository.syncState
    val ttdBitmap: StateFlow<Bitmap?> = signatureRepository.ttdBitmap
    val parafBitmap: StateFlow<Bitmap?> = signatureRepository.parafBitmap
    val recentDocuments: StateFlow<List<RecentDocument>> = recentDocumentsStore.recentDocuments

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
            signatureRepository.restoreSavedBitmaps()
        }
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
        val context = getApplication<Application>()
        val (fileName, sizeBytes) = resolveFileMeta(context, uri)
        recentDocumentsStore.recordOpened(
            uri = uri.toString(),
            fileName = fileName,
            sizeBytes = sizeBytes
        )
        viewModelScope.launch {
            _openPdfEvent.emit(uri)
        }
    }

    fun openRecentDocument(doc: RecentDocument) {
        val uri = Uri.parse(doc.uri)
        recentDocumentsStore.recordOpened(
            uri = doc.uri,
            fileName = doc.fileName,
            sizeBytes = doc.sizeBytes
        )
        viewModelScope.launch {
            _openPdfEvent.emit(uri)
        }
    }

    fun removeRecentDocument(uri: String) {
        recentDocumentsStore.remove(uri)
    }

    fun refreshSavedSignatures() {
        viewModelScope.launch {
            signatureRepository.restoreSavedBitmaps()
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

    private fun resolveFileMeta(context: Context, uri: Uri): Pair<String, Long> {
        var name: String? = null
        var size = 0L
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                        name = cursor.getString(nameIdx)
                    }
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        size = cursor.getLong(sizeIdx)
                    }
                }
            }
        }
        val resolvedName = (name ?: uri.lastPathSegment ?: "document.pdf").let {
            if (it.endsWith(".pdf", ignoreCase = true)) it else "$it.pdf"
        }
        return resolvedName to size
    }
}
