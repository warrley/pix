package com.example.mobile.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mobile.accountRepository
import com.example.mobile.repository.AccountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AccountViewModel(
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AccountUiState>(AccountUiState.Loading)
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    private var currentAccountId: Long = 1L

    init {
        loadAccount(currentAccountId)
    }

    fun loadAccount(id: Long = currentAccountId) {
        currentAccountId = id
        _uiState.update { AccountUiState.Loading }

        viewModelScope.launch {
            val result = accountRepository.getAccount(id)
            if (result.isSuccess) {
                val account = result.getOrThrow()
                _uiState.update { currentState ->
                    val isVisible = if (currentState is AccountUiState.Success) {
                        currentState.isBalanceVisible
                    } else {
                        true
                    }
                    AccountUiState.Success(account = account, isBalanceVisible = isVisible)
                }
            } else {
                val exception = result.exceptionOrNull()
                val errorMessage = exception?.message ?: "Erro desconhecido ao carregar a conta"
                _uiState.update { AccountUiState.Error(errorMessage) }
            }
        }
    }

    fun toggleBalanceVisibility() {
        _uiState.update { currentState ->
            if (currentState is AccountUiState.Success) {
                currentState.copy(isBalanceVisible = !currentState.isBalanceVisible)
            } else {
                currentState
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val repository = accountRepository()
                AccountViewModel(accountRepository = repository)
            }
        }
    }
}
