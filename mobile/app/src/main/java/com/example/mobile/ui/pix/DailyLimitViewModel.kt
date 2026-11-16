package com.example.mobile.ui.pix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mobile.container
import com.example.mobile.model.AccountLimitResponse
import com.example.mobile.repository.AccountRepository
import com.example.mobile.util.parseNonNegativeAmountInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DailyLimitUiState(
    val limit: AccountLimitResponse? = null,
    val limitInput: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

class DailyLimitViewModel(
    private val accountId: Long,
    private val repository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DailyLimitUiState())
    val uiState: StateFlow<DailyLimitUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repository.getLimit(accountId)
                .onSuccess { info ->
                    _uiState.update {
                        it.copy(
                            limit = info,
                            limitInput = info.dailyLimit.replace('.', ','),
                            isLoading = false
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun onLimitInputChange(value: String) {
        _uiState.update { it.copy(limitInput = value, error = null, message = null) }
    }

    fun applyPreset(value: String) {
        _uiState.update { it.copy(limitInput = value, error = null, message = null) }
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return
        val normalized = parseNonNegativeAmountInput(state.limitInput)
        if (normalized == null) {
            _uiState.update { it.copy(error = "Informe um valor válido maior ou igual a zero (ex.: 2000,00).") }
            return
        }

        _uiState.update { it.copy(isSaving = true, error = null, message = null) }
        viewModelScope.launch {
            repository.updateLimit(accountId, normalized)
                .onSuccess { updated ->
                    _uiState.update {
                        it.copy(
                            limit = updated,
                            limitInput = updated.dailyLimit.replace('.', ','),
                            isSaving = false,
                            message = "Limite diário atualizado com sucesso!"
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSaving = false, error = e.message) }
                }
        }
    }

    companion object {
        fun factory(accountId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { DailyLimitViewModel(accountId, container().accountRepository) }
        }
    }
}
