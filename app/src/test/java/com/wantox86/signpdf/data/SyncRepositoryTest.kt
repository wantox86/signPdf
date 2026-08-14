package com.wantox86.signpdf.data

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.wantox86.signpdf.data.local.FakeTokenStorage
import com.wantox86.signpdf.data.local.SignatureMetadataStore
import com.wantox86.signpdf.data.local.TokenStorage
import com.wantox86.signpdf.data.remote.FakeSignPdfApiService
import com.wantox86.signpdf.data.remote.dto.SignatureDto
import com.wantox86.signpdf.domain.model.OverlayType
import com.wantox86.signpdf.domain.model.SyncState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SyncRepositoryTest {
    private lateinit var fakeApi: FakeSignPdfApiService
    private lateinit var signatureRepository: SignatureRepository
    private lateinit var metadataStore: SignatureMetadataStore
    private lateinit var tokenStore: TokenStorage
    private lateinit var authRepository: AuthRepository
    private lateinit var syncRepository: SyncRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        fakeApi = FakeSignPdfApiService()
        signatureRepository = SignatureRepository(context)
        metadataStore = SignatureMetadataStore(context)
        tokenStore = FakeTokenStorage()
        // A far-future expiry so AuthRepository's own "is my stored session expired"
        // check (evaluated once at construction time) never interferes with these tests.
        tokenStore.save("fake-token", "2099-01-01T00:00:00Z", "alice")
        authRepository = AuthRepository(context, fakeApi, tokenStore)
        syncRepository = SyncRepository(context, fakeApi, signatureRepository, metadataStore, authRepository, tokenStore)
    }

    // color lets tests produce two deliberately-different-content "PNGs" (e.g. simulating a
    // real edit made from another device) instead of relying on two blank bitmaps happening
    // to compress to distinguishable bytes.
    private fun tinyPngBase64(color: Int = Color.BLACK): String {
        val stream = ByteArrayOutputStream()
        tinyBitmap(color).compress(Bitmap.CompressFormat.PNG, 100, stream)
        return Base64.getEncoder().encodeToString(stream.toByteArray())
    }

    private fun tinyBitmap(color: Int = Color.BLACK): Bitmap =
        Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    @Test
    fun `sync without a stored token fails without calling the API`() = runTest {
        tokenStore.clear()

        syncRepository.sync()

        assertTrue(syncRepository.syncState.value is SyncState.Failed)
        assertEquals(0, fakeApi.listCallCount)
    }

    @Test
    fun `sync downloads a cloud-only signature to local storage`() = runTest {
        fakeApi.signatures.add(
            SignatureDto(id = 1, type = "SIGNATURE", name = "Signature", data = tinyPngBase64(), createdAt = "t0", updatedAt = "t0")
        )

        syncRepository.sync()

        assertNotNull(signatureRepository.bitmapBytesFor(OverlayType.TTD))
        assertEquals(1L, metadataStore.get(OverlayType.TTD)?.remoteId)
        assertTrue(syncRepository.syncState.value is SyncState.Synced)
    }

    @Test
    fun `sync uploads a local-only signature as a new cloud record`() = runTest {
        signatureRepository.saveBitmap(OverlayType.TTD, tinyBitmap())

        syncRepository.sync()

        assertEquals(1, fakeApi.signatures.count { it.type == "SIGNATURE" })
        assertEquals(fakeApi.signatures.first().id, metadataStore.get(OverlayType.TTD)?.remoteId)
    }

    @Test
    fun `sync uploads local changes when a slot was never synced before even if cloud already has a record`() = runTest {
        fakeApi.signatures.add(
            SignatureDto(id = 5, type = "INITIAL", name = "Initial", data = tinyPngBase64(), createdAt = "t0", updatedAt = "t0")
        )
        signatureRepository.saveBitmap(OverlayType.PARAF, tinyBitmap())
        val localBytes = signatureRepository.bitmapBytesFor(OverlayType.PARAF)!!
        val localBase64 = Base64.getEncoder().encodeToString(localBytes)

        syncRepository.sync()

        val updated = fakeApi.signatures.first { it.id == 5L }
        assertEquals(localBase64, updated.data)
        assertEquals(5L, metadataStore.get(OverlayType.PARAF)?.remoteId)
    }

    @Test
    fun `a second sync with nothing changed is a no-op that doesn't re-upload`() = runTest {
        fakeApi.signatures.add(
            SignatureDto(id = 1, type = "SIGNATURE", name = "Signature", data = tinyPngBase64(), createdAt = "t0", updatedAt = "t0")
        )
        syncRepository.sync() // first sync downloads it
        val updatedAtAfterFirstSync = fakeApi.signatures.first().updatedAt

        syncRepository.sync() // nothing changed locally or remotely since

        assertEquals(1, fakeApi.signatures.size)
        assertEquals(updatedAtAfterFirstSync, fakeApi.signatures.first().updatedAt)
        assertTrue(syncRepository.syncState.value is SyncState.Synced)
    }

    @Test
    fun `sync downloads a newer cloud edit made from another device`() = runTest {
        fakeApi.signatures.add(
            SignatureDto(id = 1, type = "SIGNATURE", name = "Signature", data = tinyPngBase64(Color.BLACK), createdAt = "t0", updatedAt = "t0")
        )
        syncRepository.sync() // establishes local copy + metadata, not dirty

        // A different color guarantees genuinely different bytes, not just a different
        // in-memory Bitmap instance with coincidentally identical (blank) pixel content.
        val newData = tinyPngBase64(Color.WHITE)
        fakeApi.simulateRemoteEdit(id = 1, newData = newData)

        syncRepository.sync()

        val localBytes = signatureRepository.bitmapBytesFor(OverlayType.TTD)!!
        assertEquals(newData, Base64.getEncoder().encodeToString(localBytes))
        assertEquals(fakeApi.signatures.first().updatedAt, metadataStore.get(OverlayType.TTD)?.updatedAt)
    }

    @Test
    fun `a 401 from the server clears the local session and fails the sync`() = runTest {
        fakeApi.throwOnList = FakeSignPdfApiService.unauthorized()

        syncRepository.sync()

        assertNull(tokenStore.token())
        assertTrue(syncRepository.syncState.value is SyncState.Failed)
    }

    @Test
    fun `detectUnsyncedLocalOnly reports a slot with a local bitmap that was never uploaded`() = runTest {
        signatureRepository.saveBitmap(OverlayType.TTD, tinyBitmap())

        val unsynced = syncRepository.detectUnsyncedLocalOnly()

        assertEquals(listOf(OverlayType.TTD), unsynced)
    }

    @Test
    fun `detectUnsyncedLocalOnly is empty once a slot has a remoteId`() = runTest {
        signatureRepository.saveBitmap(OverlayType.TTD, tinyBitmap())
        syncRepository.sync() // uploads it, assigning a remoteId

        val unsynced = syncRepository.detectUnsyncedLocalOnly()

        assertTrue(unsynced.isEmpty())
    }
}
