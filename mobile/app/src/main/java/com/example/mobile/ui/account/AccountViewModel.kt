package com.example.mobile.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mobile.accountRepository
import com.example.mobile.model.AccountResponse
import com.example.mobile.model.UserCreateRequest
import com.example.mobile.model.UserResponse
import com.example.mobile.model.UserUpdateRequest
import com.example.mobile.repository.AccountRepository
import com.example.mobile.repository.UserRepository
import com.example.mobile.userRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AccountViewModel(
    private val accountRepository: AccountRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AccountUiState>(AccountUiState.Loading)
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    private var currentAccountId: Long = 1L
    private var currentUserId: Long? = 1L

    init {
        loadAccount(currentAccountId)
    }

    fun loadAccount(id: Long = currentAccountId) {
        currentAccountId = id
        _uiState.update { AccountUiState.Loading }

        viewModelScope.launch {
            val accountResult = accountRepository.getAccount(id)
            if (accountResult.isSuccess) {
                val account = accountResult.getOrThrow()
                val targetUserId = account.user_id ?: currentUserId ?: 1L
                currentUserId = targetUserId

                // Fetch user data and user's accounts in parallel/sequence
                val userResult = userRepository.getUser(targetUserId)
                val user = userResult.getOrNull()

                val accountsResult = userRepository.getUserAccounts(targetUserId)
                val userAccounts = accountsResult.getOrDefault(listOf(account))

                _uiState.update { currentState ->
                    val isVisible = if (currentState is AccountUiState.Success) {
                        currentState.isBalanceVisible
                    } else {
                        true
                    }
                    AccountUiState.Success(
                        user = user,
                        account = account,
                        userAccounts = if (userAccounts.isEmpty()) listOf(account) else userAccounts,
                        isBalanceVisible = isVisible
                    )
                }
            } else {
                val exception = accountResult.exceptionOrNull()
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

    fun createUser(
        name: String,
        email: String,
        docId: String,
        phone: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val request = UserCreateRequest(
                name = name.trim(),
                email = email.trim(),
                doc_id = docId.trim(),
                phone = phone.trim()
            )
            val result = userRepository.createUser(request)
            if (result.isSuccess) {
                val newUser = result.getOrThrow()
                currentUserId = newUser.id

                // Automatically create initial account for the new user
                val accResult = accountRepository.createAccount(newUser.id)
                if (accResult.isSuccess) {
                    val newAccount = accResult.getOrThrow()
                    currentAccountId = newAccount.id
                    loadAccount(newAccount.id)
                    onResult(true, "Usuário ${newUser.name} e conta cadastrados com sucesso!")
                } else {
                    onResult(true, "Usuário cadastrado com sucesso! (ID: ${newUser.id})")
                }
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Falha ao cadastrar usuário"
                onResult(false, errorMsg)
            }
        }
    }

    fun updateUser(
        name: String,
        email: String,
        phone: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val userId = currentUserId ?: (uiState.value as? AccountUiState.Success)?.user?.id
        if (userId == null) {
            onResult(false, "Nenhum usuário ativo selecionado")
            return
        }

        viewModelScope.launch {
            val request = UserUpdateRequest(
                name = name.trim(),
                email = email.trim(),
                phone = phone.trim()
            )
            val result = userRepository.updateUser(userId, request)
            if (result.isSuccess) {
                val updatedUser = result.getOrThrow()
                _uiState.update { state ->
                    if (state is AccountUiState.Success) {
                        state.copy(user = updatedUser, feedbackMessage = "Dados do usuário atualizados!")
                    } else state
                }
                onResult(true, "Dados do usuário atualizados com sucesso!")
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Falha ao atualizar dados"
                onResult(false, errorMsg)
            }
        }
    }

    fun deleteUser(onResult: (Boolean, String) -> Unit) {
        val userId = currentUserId ?: (uiState.value as? AccountUiState.Success)?.user?.id
        if (userId == null) {
            onResult(false, "Nenhum usuário ativo para remover")
            return
        }

        viewModelScope.launch {
            val result = userRepository.deleteUser(userId)
            if (result.isSuccess) {
                onResult(true, "Usuário removido com sucesso!")
                // Reset to default account or show error
                loadAccount(1L)
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Não foi possível remover o usuário"
                onResult(false, errorMsg)
            }
        }
    }

    fun createAccount(onResult: (Boolean, String) -> Unit) {
        val userId = currentUserId ?: (uiState.value as? AccountUiState.Success)?.user?.id ?: 1L

        viewModelScope.launch {
            val result = accountRepository.createAccount(userId)
            if (result.isSuccess) {
                val newAccount = result.getOrThrow()
                currentAccountId = newAccount.id
                loadAccount(newAccount.id)
                onResult(true, "Nova conta bancária nº ${newAccount.account_number} criada com sucesso!")
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Erro ao criar conta bancária"
                onResult(false, errorMsg)
            }
        }
    }

    fun deleteAccount(onResult: (Boolean, String) -> Unit) {
        val currentState = uiState.value as? AccountUiState.Success
        if (currentState == null) {
            onResult(false, "Nenhuma conta ativa selecionada")
            return
        }

        if (currentState.account.balance > 0.0) {
            onResult(false, "Não é possível encerrar conta com saldo positivo (R$ %.2f). Transfira ou zere o saldo primeiro.".format(currentState.account.balance))
            return
        }

        viewModelScope.launch {
            val result = accountRepository.deleteAccount(currentState.account.id)
            if (result.isSuccess) {
                onResult(true, "Conta nº ${currentState.account.account_number} encerrada com sucesso!")
                // Reload current user's accounts
                val targetUserId = currentUserId ?: 1L
                val accountsResult = userRepository.getUserAccounts(targetUserId)
                val remainingAccounts = accountsResult.getOrNull()?.filter { it.id != currentState.account.id } ?: emptyList()
                if (remainingAccounts.isNotEmpty()) {
                    loadAccount(remainingAccounts.first().id)
                } else {
                    loadAccount(currentState.account.id) // Will show closed or not found
                }
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Erro ao encerrar conta"
                onResult(false, errorMsg)
            }
        }
    }

    fun switchAccount(accountId: Long) {
        loadAccount(accountId)
    }

    fun clearFeedbackMessage() {
        _uiState.update { state ->
            if (state is AccountUiState.Success) state.copy(feedbackMessage = null) else state
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val accRepo = accountRepository()
                val userRepo = userRepository()
                AccountViewModel(
                    accountRepository = accRepo,
                    userRepository = userRepo
                )
            }
        }
    }
}
