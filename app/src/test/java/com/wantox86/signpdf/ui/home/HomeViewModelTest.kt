package com.wantox86.signpdf.ui.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.wantox86.signpdf.MainDispatcherRule
import com.wantox86.signpdf.data.AuthRepository
import com.wantox86.signpdf.data.SyncRepository
import com.wantox86.signpdf.domain.model.AuthState
import com.wantox86.signpdf.domain.model.OverlayType
import com.wantox86.signpdf.domain.model.SyncState
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var authRepository: AuthRepository
    private lateinit var syncRepository: SyncRepository
    private lateinit var authState: MutableStateFlow<AuthState>
    private lateinit var syncState: MutableStateFlow<SyncState>

    @Before
    fun setUp() {
        authState = MutableStateFlow(AuthState.Guest)
        syncState = MutableStateFlow(SyncState.Idle)
        authRepository = mockk()
        syncRepository = mockk()
        every { authRepository.authState } returns authState
        every { syncRepository.syncState } returns syncState
        coEvery { syncRepository.detectUnsyncedLocalOnly() } returns emptyList()
        coEvery { syncRepository.sync() } just Runs
        coEvery { authRepository.logout() } just Runs
    }

    // Uses the @JvmOverloads testing seam (see HomeViewModel) -- mocks, never touching
    // SignPdfApplication's real singletons or the network.
    private fun createViewModel(): HomeViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        return HomeViewModel(app, authRepository, syncRepository)
    }

    @Test
    fun `logging in with nothing unsynced triggers an automatic sync`() = runTest {
        createViewModel()

        authState.value = AuthState.Authenticated("alice")

        coVerify { syncRepository.sync() }
    }

    @Test
    fun `logging in with an unsynced local signature shows the migration prompt instead of syncing immediately`() = runTest {
        coEvery { syncRepository.detectUnsyncedLocalOnly() } returns listOf(OverlayType.TTD)
        val viewModel = createViewModel()

        authState.value = AuthState.Authenticated("alice")

        assertEquals(listOf(OverlayType.TTD), viewModel.migrationPrompt.value)
        coVerify(exactly = 0) { syncRepository.sync() }
    }

    @Test
    fun `confirming the migration prompt clears it and triggers a sync`() = runTest {
        coEvery { syncRepository.detectUnsyncedLocalOnly() } returns listOf(OverlayType.TTD)
        val viewModel = createViewModel()
        authState.value = AuthState.Authenticated("alice")

        viewModel.confirmMigrationUpload()

        assertNull(viewModel.migrationPrompt.value)
        coVerify { syncRepository.sync() }
    }

    @Test
    fun `dismissing the migration prompt clears it without syncing`() = runTest {
        coEvery { syncRepository.detectUnsyncedLocalOnly() } returns listOf(OverlayType.TTD)
        val viewModel = createViewModel()
        authState.value = AuthState.Authenticated("alice")

        viewModel.dismissMigrationPrompt()

        assertNull(viewModel.migrationPrompt.value)
        coVerify(exactly = 0) { syncRepository.sync() }
    }

    @Test
    fun `session expiring on its own surfaces a session-expired event`() = runTest {
        val viewModel = createViewModel()
        authState.value = AuthState.Authenticated("alice")

        // Simulates AuthRepository.handleSessionExpired() flipping the real StateFlow after
        // a 401 -- authRepository is a mock here, so the test drives its StateFlow directly.
        authState.value = AuthState.Guest

        assertTrue(viewModel.sessionExpiredEvent.value)
    }

    @Test
    fun `calling logout does not surface a session-expired event`() = runTest {
        val viewModel = createViewModel()
        authState.value = AuthState.Authenticated("alice")

        viewModel.logout()
        authState.value = AuthState.Guest // simulates what the real AuthRepository.logout() would do

        assertFalse(viewModel.sessionExpiredEvent.value)
        coVerify { authRepository.logout() }
    }

    @Test
    fun `consumeSessionExpiredEvent resets the flag`() = runTest {
        val viewModel = createViewModel()
        authState.value = AuthState.Authenticated("alice")
        authState.value = AuthState.Guest
        assertTrue(viewModel.sessionExpiredEvent.value)

        viewModel.consumeSessionExpiredEvent()

        assertFalse(viewModel.sessionExpiredEvent.value)
    }
}
