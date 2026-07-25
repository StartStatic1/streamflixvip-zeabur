package com.streamflixvip.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixvip.app.data.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface AuthStep {
    data object EnterEmail : AuthStep
    data object EnterCode : AuthStep
    data object LoggedIn : AuthStep
}

data class AuthUiState(
    val step: AuthStep = AuthStep.EnterEmail,
    val email: String = "",
    val code: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)

class AuthViewModel(
    private val repository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AuthUiState(step = if (repository.isLoggedIn) AuthStep.LoggedIn else AuthStep.EnterEmail)
    )
    val uiState: StateFlow<AuthUiState> = _uiState

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value, errorMessage = null)
    }

    fun onCodeChange(value: String) {
        _uiState.value = _uiState.value.copy(code = value, errorMessage = null)
    }

    /** Passo 1: envia o código de 6 dígitos pro e-mail informado. */
    fun sendCode() {
        val email = _uiState.value.email.trim()
        if (!email.contains("@")) {
            _uiState.value = _uiState.value.copy(errorMessage = "Digite um e-mail válido.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                repository.sendOtp(email)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    step = AuthStep.EnterCode,
                    infoMessage = "Código enviado para $email. Verifique também o spam.",
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Não foi possível enviar o código. Tente novamente.",
                )
            }
        }
    }

    /** Passo 2: confirma o código de 6 dígitos recebido por e-mail. */
    fun confirmCode() {
        val state = _uiState.value
        if (state.code.trim().length != 6) {
            _uiState.value = state.copy(errorMessage = "Digite o código de 6 dígitos.")
            return
        }
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            try {
                repository.verifyOtp(email = state.email.trim(), code = state.code.trim())
                _uiState.value = _uiState.value.copy(isLoading = false, step = AuthStep.LoggedIn)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Código inválido ou expirado. Confira e tente de novo.",
                )
            }
        }
    }

    fun signOut() {
        repository.signOut()
        _uiState.value = AuthUiState(step = AuthStep.EnterEmail)
    }
}
