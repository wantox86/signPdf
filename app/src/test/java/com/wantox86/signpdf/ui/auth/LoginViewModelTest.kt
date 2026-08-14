package com.wantox86.signpdf.ui.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.wantox86.signpdf.MainDispatcherRule
import com.wantox86.signpdf.data.AuthRepository
import com.wantox86.signpdf.data.LoginResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LoginViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var authRepository: AuthRepository
    private lateinit var viewModel: LoginViewModel

    @Before
    fun setUp() {
        authRepository = mockk()
        val app = ApplicationProvider.getApplicationContext<Application>()
        // Uses the @JvmOverloads testing seam (see LoginViewModel) -- a mock repository,
        // never touching SignPdfApplication's real singletons or the network.
        viewModel = LoginViewModel(app, authRepository)
    }

    @Test
    fun `blank username or password shows an error without calling the repository`() = runTest {
        viewModel.uiState.test {
            assertEquals(LoginUiState.Idle, awaitItem())

            viewModel.login("", "secret")

            assertTrue(awaitItem() is LoginUiState.Error)
        }
        coVerify(exactly = 0) { authRepository.login(any(), any()) }
    }

    @Test
    fun `successful login goes through Loading then Success`() = runTest {
        coEvery { authRepository.login("alice", "secret") } returns LoginResult.Success

        viewModel.uiState.test {
            assertEquals(LoginUiState.Idle, awaitItem())

            viewModel.login("alice", "secret")

            assertEquals(LoginUiState.Loading, awaitItem())
            assertEquals(LoginUiState.Success, awaitItem())
        }
    }

    @Test
    fun `failed login surfaces the repository's error message`() = runTest {
        coEvery { authRepository.login("alice", "wrong") } returns LoginResult.Failure("Invalid credentials")

        viewModel.uiState.test {
            awaitItem() // Idle
            viewModel.login("alice", "wrong")
            awaitItem() // Loading

            val state = awaitItem()
            assertTrue(state is LoginUiState.Error)
            assertEquals("Invalid credentials", (state as LoginUiState.Error).message)
        }
    }
}
