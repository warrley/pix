package com.example.mobile.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mobile.container
import com.example.mobile.model.SignUpRequest
import com.example.mobile.network.BiometricCredentialStore
import com.example.mobile.network.UnauthorizedException
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
    val enableBiometricOnLogin: Boolean = true,
    val hasSavedBiometrics: Boolean = false,
    val savedBiometricEmail: String? = null,
    val savedBiometricName: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val biometricStore: BiometricCredentialStore? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AuthUiState(
            email = biometricStore?.savedEmail().orEmpty(),
            hasSavedBiometrics = biometricStore?.isEnabled() == true,
            savedBiometricEmail = biometricStore?.savedEmail(),
            savedBiometricName = biometricStore?.savedDisplayName()
        )
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /** Shown on the login screen after the server invalidated the session. */
    val sessionExpired: StateFlow<Boolean> = authRepository.sessionExpired

    fun onNameChange(value: String) = edit { copy(name = value) }
    fun onEmailChange(value: String) = edit { copy(email = value) }
    fun onDocIdChange(value: String) = edit { copy(docId = value) }
    fun onPhoneChange(value: String) = edit { copy(phone = value) }
    fun onPasswordChange(value: String) = edit { copy(password = value) }
    fun onEnableBiometricChange(enabled: Boolean) = _uiState.update { it.copy(enableBiometricOnLogin = enabled) }
    fun setBiometricError(message: String) = _uiState.update { it.copy(error = message) }

    fun refreshBiometricState() {
        _uiState.update {
            it.copy(
                hasSavedBiometrics = biometricStore?.isEnabled() == true,
                savedBiometricEmail = biometricStore?.savedEmail(),
                savedBiometricName = biometricStore?.savedDisplayName()
            )
        }
    }

    fun switchMode() = _uiState.update {
        it.copy(mode = if (it.mode == AuthMode.LOGIN) AuthMode.SIGN_UP else AuthMode.LOGIN, error = null)
    }

    fun submit(biometricHardwareAvailable: Boolean = true) {
        val state = _uiState.value
        if (state.isLoading) return
        validate(state)?.let { message ->
            _uiState.update { it.copy(error = message) }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = when (state.mode) {
                AuthMode.LOGIN -> authRepository.login(state.email, state.password)
                AuthMode.SIGN_UP -> signUpThenLogin(state)
            }
            val user = result.getOrNull()
            if (user != null) {
                if (biometricHardwareAvailable && state.enableBiometricOnLogin) {
                    biometricStore?.save(state.email.trim(), state.password, user.name)
                }
                authRepository.acknowledgeExpiration()
                _uiState.value = AuthUiState(
                    email = state.email.trim(),
                    enableBiometricOnLogin = state.enableBiometricOnLogin,
                    hasSavedBiometrics = biometricStore?.isEnabled() == true,
                    savedBiometricEmail = biometricStore?.savedEmail(),
                    savedBiometricName = biometricStore?.savedDisplayName()
                )
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.exceptionOrNull()?.message ?: "Erro inesperado"
                    )
                }
            }
        }
    }

    fun loginWithBiometrics() {
        val state = _uiState.value
        if (state.isLoading) return
        val creds = biometricStore?.read()
        if (creds == null) {
            refreshBiometricState()
            _uiState.update { it.copy(error = "Credenciais biométricas indisponíveis. Entre com e-mail e senha.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            authRepository.login(creds.email, creds.password)
                .onSuccess {
                    authRepository.acknowledgeExpiration()
                    _uiState.value = AuthUiState(
                        email = creds.email,
                        hasSavedBiometrics = biometricStore?.isEnabled() == true,
                        savedBiometricEmail = biometricStore?.savedEmail(),
                        savedBiometricName = biometricStore?.savedDisplayName()
                    )
                }
                .onFailure { e ->
                    if (e is UnauthorizedException) {
                        biometricStore?.clear()
                        refreshBiometricState()
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "Sua senha mudou. Entre com e-mail e senha para reativar a biometria."
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(isLoading = false, error = e.message ?: "Erro ao autenticar com biometria.")
                        }
                    }
                }
        }
    }

    private suspend fun signUpThenLogin(state: AuthUiState) = run {
        val request = SignUpRequest(
            name = state.name.trim(),
            email = state.email.trim(),
            docId = state.docId.filter { it.isDigit() },
            phone = normalizePhone(state.phone),
            password = state.password
        )
        val signUpResult = authRepository.signUp(request)
        if (signUpResult.isFailure) {
            Result.failure(signUpResult.exceptionOrNull()!!)
        } else {
            authRepository.login(request.email, request.password)
        }
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
            initializer {
                val c = container()
                AuthViewModel(c.authRepository, c.biometricCredentialStore)
            }
        }
    }
}
