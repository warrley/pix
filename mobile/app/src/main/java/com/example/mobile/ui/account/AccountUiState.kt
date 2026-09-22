package com.example.mobile.ui.account

import com.example.mobile.model.AccountResponse
import com.example.mobile.model.UserResponse

sealed interface AccountUiState {
    data object Loading : AccountUiState

    data class Success(
        val user: UserResponse? = null,
        val account: AccountResponse,
        val userAccounts: List<AccountResponse> = emptyList(),
        val isBalanceVisible: Boolean = true,
        val feedbackMessage: String? = null
    ) : AccountUiState

    data class Error(
        val message: String
    ) : AccountUiState
}
