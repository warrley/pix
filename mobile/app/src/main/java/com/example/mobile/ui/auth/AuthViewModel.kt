package com.example.mobile.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mobile.container
import com.example.mobile.model.SignUpRequest
import com.example.mobile.repository.AuthRepository
import com.example.mobile.util.normalizePhone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode { LOGIN, SIGN_UP }

data class AuthUiState(
    val mode: AuthMode = AuthMode.LOGIN,
    val name: String = "",
    val email: String = "",
    val docId: String = "",
    val phone: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /** Shown on the login screen after the server invalidated the session. */
    val sessionExpired: StateFlow<Boolean> = authRepository.sessionExpired

    fun onNameChange(value: String) = edit { copy(name = value) }
    fun onEmailChange(value: String) = edit { copy(email = value) }
    fun onDocIdChange(value: String) = edit { copy(docId = value) }
    fun onPhoneChange(value: String) = edit { copy(phone = value) }
    fun onPasswordChange(value: String) = edit { copy(password = value) }

    fun switchMode() = _uiState.update {
        it.copy(mode = if (it.mode == AuthMode.LOGIN) AuthMode.SIGN_UP else AuthMode.LOGIN, error = null)
    }

    fun submit() {
        val state = _uiState.value
        if (state.isLoading) return
        validate(state)?.let { message ->
            _uiState.update { it.copy(error = message) }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val failure = when (state.mode) {
                AuthMode.LOGIN -> authRepository.login(state.email, state.password).exceptionOrNull()
                AuthMode.SIGN_UP -> signUpThenLogin(state)
            }
            if (failure == null) {
                authRepository.acknowledgeExpiration()
                _uiState.value = AuthUiState(email = state.email)
            } else {
                _uiState.update { it.copy(isLoading = false, error = failure.message ?: "Erro inesperado") }
            }
        }
    }

    private suspend fun signUpThenLogin(state: AuthUiState): Throwable? {
        val request = SignUpRequest(
            name = state.name.trim(),
            email = state.email.trim(),
            docId = state.docId.filter { it.isDigit() },
            phone = normalizePhone(state.phone),
            password = state.password
        )
        authRepository.signUp(request).onFailure { return it }
        return authRepository.login(request.email, request.password).exceptionOrNull()
    }

    private fun validate(state: AuthUiState): String? {
        if (state.email.isBlank() || state.password.isEmpty()) return "Informe e-mail e senha."
        if (state.mode == AuthMode.SIGN_UP) {
            if (state.name.isBlank()) return "Informe o nome."
            if (state.docId.filter { it.isDigit() }.length !in listOf(11, 14)) return "Informe um CPF (11 dígitos) ou CNPJ (14 dígitos)."
            if (state.password.length < 12) return "A senha deve ter pelo menos 12 caracteres."
        }
        return null
    }

    private fun edit(block: AuthUiState.() -> AuthUiState) = _uiState.update { it.block().copy(error = null) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { AuthViewModel(container().authRepository) }
        }
    }
}
