package com.wantox86.signpdf.data

import android.content.Context
import com.wantox86.signpdf.R
import com.wantox86.signpdf.data.local.SignatureMetadataStore
import com.wantox86.signpdf.data.local.TokenStore
import com.wantox86.signpdf.data.remote.SignPdfApiService
import com.wantox86.signpdf.data.remote.dto.CreateSignatureRequest
import com.wantox86.signpdf.data.remote.dto.ErrorEnvelope
import com.wantox86.signpdf.data.remote.dto.SignatureDto
import com.wantox86.signpdf.data.remote.dto.SignatureListResponse
import com.wantox86.signpdf.data.remote.dto.UpdateSignatureRequest
import com.wantox86.signpdf.domain.model.OverlayType
import com.wantox86.signpdf.domain.model.SignatureSlotMeta
import com.wantox86.signpdf.domain.model.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.util.Base64

// Sync is 100% client-driven (no server-side "sync" package, per plan decision #5): this
// class is the entire algorithm. Each OverlayType is synced independently inside its own
// try/catch so one failing type never blocks or rolls back the other.
class SyncRepository(
    private val context: Context,
    private val apiService: SignPdfApiService,
    private val signatureRepository: SignatureRepository,
    private val metadataStore: SignatureMetadataStore,
    private val authRepository: AuthRepository,
    private val tokenStore: TokenStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState

    suspend fun sync() = withContext(Dispatchers.IO) {
        if (tokenStore.token() == null) {
            // Guest mode: never calls the API at all.
            _syncState.value = SyncState.Failed(context.getString(R.string.error_sync_not_logged_in))
            return@withContext
        }

        _syncState.value = SyncState.Syncing

        val remote = try {
            apiService.listSignatures()
        } catch (e: HttpException) {
            if (e.code() == 401) authRepository.handleSessionExpired()
            _syncState.value = SyncState.Failed(extractErrorMessage(e))
            return@withContext
        } catch (e: IOException) {
            _syncState.value = SyncState.Failed(context.getString(R.string.error_login_network))
            return@withContext
        }

        var lastError: String? = null
        for (type in OverlayType.values()) {
            try {
                syncType(type, remote)
            } catch (e: HttpException) {
                if (e.code() == 401) authRepository.handleSessionExpired()
                lastError = extractErrorMessage(e)
            } catch (e: IOException) {
                lastError = context.getString(R.string.error_login_network)
            } catch (e: Exception) {
                lastError = e.message ?: context.getString(R.string.error_sync_generic)
            }
        }

        // Per-type try/catch above means a successful type's changes are already committed
        // to SignatureRepository/SignatureMetadataStore by this point -- a failure on the
        // other type doesn't roll that back, it just keeps the overall result Failed so the
        // user knows something didn't fully go through.
        _syncState.value = if (lastError != null) SyncState.Failed(lastError) else SyncState.Synced(Instant.now())
    }

    // Returns the types that have a local bitmap but were never uploaded (no remoteId yet)
    // -- the guest-to-login migration prompt (phase 7) uses this right after a successful
    // login to ask "upload your local signature?" before the first auto-sync.
    suspend fun detectUnsyncedLocalOnly(): List<OverlayType> = withContext(Dispatchers.IO) {
        OverlayType.values().filter { type ->
            val hasLocal = signatureRepository.bitmapBytesFor(type) != null
            val meta = metadataStore.get(type)
            hasLocal && meta?.remoteId == null
        }
    }

    private suspend fun syncType(type: OverlayType, remote: SignatureListResponse) {
        val cloudList = if (type == OverlayType.TTD) remote.signatures else remote.initials
        val cloudRecord = cloudList.maxByOrNull { Instant.parse(it.updatedAt) }
        val localBytes = signatureRepository.bitmapBytesFor(type)

        if (localBytes == null && cloudRecord == null) return

        if (localBytes == null) {
            downloadToLocal(type, cloudRecord!!)
            return
        }

        if (cloudRecord == null) {
            uploadNew(type, localBytes)
            return
        }

        val meta = metadataStore.get(type)
        if (meta == null) {
            // Never synced before, both sides now have data -> local wins (matches spec:
            // "local newer & dirty -> PUT update").
            uploadUpdate(type, cloudRecord.id, localBytes)
            return
        }

        val localMtime = signatureRepository.lastModifiedAt(type)
        // The file's mtime moved past what we last synced -> local has an unsynced edit.
        val dirty = meta.localFileModifiedAtMillis != localMtime
        if (dirty) {
            uploadUpdate(type, cloudRecord.id, localBytes)
            return
        }

        // Not dirty: cloud is authoritative unless it's already identical to what we have.
        val localData = Base64.getEncoder().encodeToString(localBytes)
        val alreadyInSync = cloudRecord.data == localData && meta.updatedAt == cloudRecord.updatedAt
        if (alreadyInSync) {
            metadataStore.put(type, meta.copy(remoteId = cloudRecord.id, updatedAt = cloudRecord.updatedAt))
            return
        }

        downloadToLocal(type, cloudRecord)
    }

    private suspend fun downloadToLocal(type: OverlayType, cloudRecord: SignatureDto) {
        val bytes = Base64.getDecoder().decode(cloudRecord.data)
        signatureRepository.saveBitmapFromSync(type, bytes)
        val mtime = signatureRepository.lastModifiedAt(type)
        metadataStore.put(type, SignatureSlotMeta(cloudRecord.id, cloudRecord.updatedAt, mtime))
    }

    private suspend fun uploadNew(type: OverlayType, localBytes: ByteArray) {
        val data = Base64.getEncoder().encodeToString(localBytes)
        val dto = apiService.createSignature(CreateSignatureRequest(backendTypeName(type), defaultName(type), data))
        metadataStore.put(type, SignatureSlotMeta(dto.id, dto.updatedAt, signatureRepository.lastModifiedAt(type)))
    }

    private suspend fun uploadUpdate(type: OverlayType, remoteId: Long, localBytes: ByteArray) {
        val data = Base64.getEncoder().encodeToString(localBytes)
        val dto = apiService.updateSignature(remoteId, UpdateSignatureRequest(defaultName(type), data))
        metadataStore.put(type, SignatureSlotMeta(dto.id, dto.updatedAt, signatureRepository.lastModifiedAt(type)))
    }

    private fun backendTypeName(type: OverlayType) = when (type) {
        OverlayType.TTD -> "SIGNATURE"
        OverlayType.PARAF -> "INITIAL"
    }

    private fun defaultName(type: OverlayType) = when (type) {
        OverlayType.TTD -> "Signature"
        OverlayType.PARAF -> "Initial"
    }

    private fun extractErrorMessage(e: HttpException): String {
        val body = e.response()?.errorBody()?.string()
        val parsed = body?.let { runCatching { json.decodeFromString<ErrorEnvelope>(it) }.getOrNull() }
        return parsed?.error?.message ?: context.getString(R.string.error_sync_generic)
    }
}
