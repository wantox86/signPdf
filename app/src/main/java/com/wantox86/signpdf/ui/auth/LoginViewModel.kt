package com.wantox86.signpdf.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wantox86.signpdf.R
import com.wantox86.signpdf.SignPdfApplication
import com.wantox86.signpdf.data.AuthRepository
import com.wantox86.signpdf.data.LoginResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class LoginUiState {
    data object Idle : LoginUiState()
    data object Loading : LoginUiState()
    data object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

class LoginViewModel @JvmOverloads constructor(
    app: Application,
    // Defaults to the app-wide singleton in production; @JvmOverloads keeps the plain
    // by viewModels() reflection-based factory working (it looks for a single-Application-arg
    // constructor), while tests can pass a fake/mock AuthRepository directly.
    private val authRepository: AuthRepository = (app as SignPdfApplication).authRepository,
) : AndroidViewModel(app) {
    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _uiState.value = LoginUiState.Error(getApplication<Application>().getString(R.string.login_error_empty_fields))
            return
        }
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            _uiState.value = when (val result = authRepository.login(username, password)) {
                is LoginResult.Success -> LoginUiState.Success
                is LoginResult.Failure -> LoginUiState.Error(result.message)
            }
        }
    }
}
