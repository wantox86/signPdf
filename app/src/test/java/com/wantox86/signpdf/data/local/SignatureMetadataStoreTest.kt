package com.wantox86.signpdf.data.local

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.wantox86.signpdf.domain.model.OverlayType
import com.wantox86.signpdf.domain.model.SignatureSlotMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SignatureMetadataStoreTest {
    private lateinit var store: SignatureMetadataStore

    @Before
    fun setUp() {
        store = SignatureMetadataStore(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `get returns null when nothing was ever put for a slot`() {
        assertNull(store.get(OverlayType.TTD))
    }

    @Test
    fun `put then get round-trips the same slot`() {
        val meta = SignatureSlotMeta(remoteId = 42L, updatedAt = "2027-01-01T00:00:00Z", localFileModifiedAtMillis = 1000L)

        store.put(OverlayType.TTD, meta)

        assertEquals(meta, store.get(OverlayType.TTD))
    }

    @Test
    fun `TTD and PARAF slots are stored independently`() {
        val ttdMeta = SignatureSlotMeta(remoteId = 1L, updatedAt = "2027-01-01T00:00:00Z", localFileModifiedAtMillis = 100L)
        val parafMeta = SignatureSlotMeta(remoteId = 2L, updatedAt = "2027-02-01T00:00:00Z", localFileModifiedAtMillis = 200L)

        store.put(OverlayType.TTD, ttdMeta)
        store.put(OverlayType.PARAF, parafMeta)

        assertEquals(ttdMeta, store.get(OverlayType.TTD))
        assertEquals(parafMeta, store.get(OverlayType.PARAF))
    }

    @Test
    fun `clear removes only the given slot`() {
        store.put(OverlayType.TTD, SignatureSlotMeta(remoteId = 1L, updatedAt = "2027-01-01T00:00:00Z", localFileModifiedAtMillis = 100L))
        store.put(OverlayType.PARAF, SignatureSlotMeta(remoteId = 2L, updatedAt = "2027-02-01T00:00:00Z", localFileModifiedAtMillis = 200L))

        store.clear(OverlayType.TTD)

        assertNull(store.get(OverlayType.TTD))
        assertEquals(2L, store.get(OverlayType.PARAF)?.remoteId)
    }
}
