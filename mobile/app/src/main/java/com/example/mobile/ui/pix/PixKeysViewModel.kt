package com.example.mobile.ui.pix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mobile.container
import com.example.mobile.model.PixKeyResponse
import com.example.mobile.repository.PixKeyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Key types accepted by the backend, with their Portuguese labels. */
enum class PixKeyType(val apiValue: String, val label: String, val needsValue: Boolean) {
    CPF("cpf", "CPF", true),
    CNPJ("cnpj", "CNPJ", true),
    EMAIL("email", "E-mail", true),
    PHONE("phone", "Telefone", true),
    RANDOM("random", "Chave aleatória", false)
}

data class PixKeysUiState(
    val keys: List<PixKeyResponse> = emptyList(),
    val type: PixKeyType = PixKeyType.EMAIL,
    val value: String = "",
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

class PixKeysViewModel(
    private val accountId: Long,
    private val repository: PixKeyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PixKeysUiState())
    val uiState: StateFlow<PixKeysUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onTypeChange(type: PixKeyType) = _uiState.update { it.copy(type = type, value = "", error = null, message = null) }
    fun onValueChange(value: String) = _uiState.update { it.copy(value = value, error = null, message = null) }

    fun load() {
        viewModelScope.launch {
            repository.getKeys(accountId)
                .onSuccess { keys -> _uiState.update { it.copy(keys = keys, isLoading = false) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun create() {
        val state = _uiState.value
        if (state.isBusy) return
        if (state.type.needsValue && state.value.isBlank()) {
            _uiState.update { it.copy(error = "Informe o valor da chave.") }
            return
        }
        _uiState.update { it.copy(isBusy = true, error = null, message = null) }
        viewModelScope.launch {
            repository.createKey(accountId, state.type.apiValue, state.value.trim())
                .onSuccess { key ->
                    _uiState.update {
                        it.copy(
                            keys = it.keys + key,
                            value = "",
                            isBusy = false,
                            message = "Chave cadastrada com sucesso!"
                        )
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isBusy = false, error = e.message) } }
        }
    }

    fun suspend(key: PixKeyResponse) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true, error = null, message = null) }
        viewModelScope.launch {
            repository.suspendKey(key.id)
                .onSuccess { updated ->
                    _uiState.update { state ->
                        state.copy(
                            keys = state.keys.map { if (it.id == updated.id) updated else it },
                            isBusy = false,
                            message = "Chave suspensa."
                        )
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isBusy = false, error = e.message) } }
        }
    }

    fun unblock(key: PixKeyResponse) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true, error = null, message = null) }
        viewModelScope.launch {
            repository.unblockKey(key.id)
                .onSuccess { updated ->
                    _uiState.update { state ->
                        state.copy(
                            keys = state.keys.map { if (it.id == updated.id) updated else it },
                            isBusy = false,
                            message = "Chave reativada!"
                        )
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isBusy = false, error = e.message) } }
        }
    }

    fun reassociate(key: PixKeyResponse, targetAccountId: Long, targetAccountLabel: String) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true, error = null, message = null) }
        viewModelScope.launch {
            repository.reassociateKey(key.id, targetAccountId)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            keys = state.keys.filterNot { it.id == key.id },
                            isBusy = false,
                            message = "Chave transferida para $targetAccountLabel."
                        )
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isBusy = false, error = e.message) } }
        }
    }

    fun delete(key: PixKeyResponse) {
        _uiState.update { it.copy(error = null, message = null) }
        viewModelScope.launch {
            repository.deleteKey(key.id)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            keys = it.keys.filterNot { k -> k.id == key.id },
                            message = "Chave removida."
                        )
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    companion object {
        fun factory(accountId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { PixKeysViewModel(accountId, container().pixKeyRepository) }
        }
    }
}
