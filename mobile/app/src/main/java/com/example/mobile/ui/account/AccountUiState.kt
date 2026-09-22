package com.example.mobile.ui.account

import com.example.mobile.model.AccountResponse

sealed interface AccountUiState {
    data object Loading : AccountUiState
    data class Success(
        val account: AccountResponse,
        val isBalanceVisible: Boolean = true
    ) : AccountUiState
    data class Error(val message: String) : AccountUiState
}
