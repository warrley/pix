package com.example.mobile.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mobile.container
import com.example.mobile.model.AccountResponse
import com.example.mobile.model.StatementEntry
import com.example.mobile.model.UserResponse
import com.example.mobile.repository.AccountRepository
import com.example.mobile.repository.AuthRepository
import com.example.mobile.repository.TransferRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val user: UserResponse? = null,
    val accounts: List<AccountResponse> = emptyList(),
    val selectedAccountId: Long? = null,
    val statement: List<StatementEntry> = emptyList(),
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val isBalanceVisible: Boolean = true,
    val error: String? = null,
    val message: String? = null
) {
    val selectedAccount: AccountResponse?
        get() = accounts.firstOrNull { it.id == selectedAccountId } ?: accounts.firstOrNull()
}

class HomeViewModel(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository,
    private val transferRepository: TransferRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(user = authRepository.session.value?.user))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun refresh() {
        val user = authRepository.session.value?.user ?: return
        _uiState.update { it.copy(user = user, isLoading = true, error = null) }
        viewModelScope.launch {
            accountRepository.getAccounts(user.id)
                .onSuccess { accounts ->
                    _uiState.update { it.copy(accounts = accounts, isLoading = false) }
                    loadStatement()
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun selectAccount(id: Long) {
        _uiState.update { it.copy(selectedAccountId = id, statement = emptyList()) }
        loadStatement()
    }

    fun toggleBalanceVisibility() = _uiState.update { it.copy(isBalanceVisible = !it.isBalanceVisible) }

    fun createAccount() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true, message = null, error = null) }
        viewModelScope.launch {
            accountRepository.createAccount()
                .onSuccess { account ->
                    _uiState.update {
                        it.copy(
                            accounts = it.accounts + account,
                            selectedAccountId = account.id,
                            statement = emptyList(),
                            isBusy = false,
                            message = "Conta ${account.accountNumber} criada com sucesso!"
                        )
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isBusy = false, error = e.message) } }
        }
    }

    fun closeSelectedAccount() {
        val account = _uiState.value.selectedAccount ?: return
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true, message = null, error = null) }
        viewModelScope.launch {
            accountRepository.closeAccount(account.id)
                .onSuccess {
                    _uiState.update { it.copy(isBusy = false, message = "Conta ${account.accountNumber} encerrada.") }
                    refresh()
                }
                .onFailure { e -> _uiState.update { it.copy(isBusy = false, error = e.message) } }
        }
    }

    fun consumeFeedback() = _uiState.update { it.copy(message = null, error = null) }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }

    private fun loadStatement() {
        val account = _uiState.value.selectedAccount ?: return
        viewModelScope.launch {
            transferRepository.statement(account.id)
                .onSuccess { entries ->
                    val visibleEntries = entries.filter { it.status != "failed" }
                    // Ignore the answer if the user switched accounts meanwhile.
                    _uiState.update { if (it.selectedAccount?.id == account.id) it.copy(statement = visibleEntries) else it }
                }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = container()
                HomeViewModel(c.authRepository, c.accountRepository, c.transferRepository)
            }
        }
    }
}
