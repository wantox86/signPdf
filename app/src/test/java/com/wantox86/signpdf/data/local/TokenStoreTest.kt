package com.wantox86.signpdf.data.local

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TokenStoreTest {
    private lateinit var tokenStore: TokenStore

    @Before
    fun setUp() {
        tokenStore = TokenStore(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `token username and expiresAt are null before anything is saved`() {
        assertNull(tokenStore.token())
        assertNull(tokenStore.username())
        assertNull(tokenStore.expiresAt())
    }

    @Test
    fun `save persists token username and expiresAt`() {
        tokenStore.save("abc123", "2027-01-01T00:00:00Z", "alice")

        assertEquals("abc123", tokenStore.token())
        assertEquals("alice", tokenStore.username())
        assertEquals("2027-01-01T00:00:00Z", tokenStore.expiresAt())
    }

    @Test
    fun `save overwrites a previously saved session`() {
        tokenStore.save("first", "2027-01-01T00:00:00Z", "alice")
        tokenStore.save("second", "2027-06-01T00:00:00Z", "bob")

        assertEquals("second", tokenStore.token())
        assertEquals("bob", tokenStore.username())
    }

    @Test
    fun `clear removes the saved session`() {
        tokenStore.save("abc123", "2027-01-01T00:00:00Z", "alice")

        tokenStore.clear()

        assertNull(tokenStore.token())
        assertNull(tokenStore.username())
        assertNull(tokenStore.expiresAt())
    }
}
