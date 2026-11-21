package com.wantox86.signpdf.data.local

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.wantox86.signpdf.domain.model.DocumentStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RecentDocumentsStoreTest {

    private lateinit var store: RecentDocumentsStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        store = RecentDocumentsStore(context)
        store.clear()
    }

    @Test
    fun `recordOpened inserts new document as DRAFT and orders most recent first`() {
        store.recordOpened("content://doc/1", "first.pdf", 1024L, nowMillis = 1000L)
        store.recordOpened("content://doc/2", "second.pdf", 2048L, nowMillis = 2000L)

        val list = store.recentDocuments.value
        assertEquals(2, list.size)
        assertEquals("second.pdf", list[0].fileName)
        assertEquals(DocumentStatus.DRAFT, list[0].status)
        assertEquals("first.pdf", list[1].fileName)
    }

    @Test
    fun `markSigned updates status to SIGNED and persists across instances`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        store.recordOpened("content://doc/1", "nda.pdf", 500_000L, nowMillis = 1000L)

        store.markSigned("content://doc/1", nowMillis = 3000L)

        val reloaded = RecentDocumentsStore(context).recentDocuments.value
        assertEquals(1, reloaded.size)
        assertEquals(DocumentStatus.SIGNED, reloaded[0].status)
        assertEquals(3000L, reloaded[0].lastOpenedAtMillis)
    }

    @Test
    fun `reopening a signed document preserves its SIGNED status`() {
        store.recordOpened("content://doc/1", "nda.pdf", 500_000L, nowMillis = 1000L)
        store.markSigned("content://doc/1", nowMillis = 2000L)

        store.recordOpened("content://doc/1", "nda.pdf", 500_000L, nowMillis = 3000L)

        assertEquals(DocumentStatus.SIGNED, store.recentDocuments.value[0].status)
    }

    @Test
    fun `caps stored recent documents at MAX_RECENT_ITEMS`() {
        for (i in 1..15) {
            store.recordOpened("content://doc/$i", "doc_$i.pdf", 100L * i, nowMillis = i * 1000L)
        }

        val list = store.recentDocuments.value
        assertEquals(RecentDocumentsStore.MAX_RECENT_ITEMS, list.size)
        assertEquals("doc_15.pdf", list.first().fileName)
        assertEquals("doc_6.pdf", list.last().fileName)
    }

    @Test
    fun `remove deletes matching uri`() {
        store.recordOpened("content://doc/1", "a.pdf", 100L, nowMillis = 1000L)
        store.remove("content://doc/1")

        assertTrue(store.recentDocuments.value.isEmpty())
    }
}
