package com.example.mobile.ui.transfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mobile.container
import com.example.mobile.model.PixKeyLookupResponse
import com.example.mobile.model.TransferReceipt
import com.example.mobile.model.TransferRequest
import com.example.mobile.network.NetworkException
import com.example.mobile.repository.PixKeyRepository
import com.example.mobile.repository.TransferRepository
import com.example.mobile.util.parseAmountInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class TransferUiState(
    val pixKey: String = "",
    val amount: String = "",
    val description: String = "",
    val recipient: PixKeyLookupResponse? = null,
    val isLookingUp: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val receipt: TransferReceipt? = null
)

class TransferViewModel(
    private val sourceAccountId: Long,
    private val repository: TransferRepository,
    private val pixKeyRepository: PixKeyRepository? = null,
    private val newIdempotencyKey: () -> String = { UUID.randomUUID().toString().replace("-", "") }
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransferUiState())
    val uiState: StateFlow<TransferUiState> = _uiState.asStateFlow()

    // One key per user intent: a retry after a network failure reuses it, so the backend
    // returns the original transfer instead of debiting twice.
    private var idempotencyKey = newIdempotencyKey()

    fun onPixKeyChange(value: String) = edit { copy(pixKey = value, recipient = null) }
    fun onAmountChange(value: String) = edit { copy(amount = value) }
    fun onDescriptionChange(value: String) = edit { copy(description = value) }

    fun applyPrefill(
        pixKey: String,
        amount: String,
        recipient: PixKeyLookupResponse,
        description: String = ""
    ) {
        idempotencyKey = newIdempotencyKey()
        _uiState.value = TransferUiState(
            pixKey = pixKey,
            amount = amount,
            description = description,
            recipient = recipient
        )
    }

    /**
     * Validates that the destination Pix key exists, is active, and does not belong
     * to the current user before allowing the transfer flow to advance to Step 2.
     */
    fun verifyRecipientKey(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.isLookingUp || state.isLoading) return
        val key = state.pixKey.trim()
        if (key.isEmpty()) {
            _uiState.update { it.copy(error = "Informe a chave Pix do destinatário.") }
            return
        }
        val lookupRepo = pixKeyRepository
        if (lookupRepo == null) {
            onSuccess()
            return
        }

        _uiState.update { it.copy(isLookingUp = true, error = null) }
        viewModelScope.launch {
            lookupRepo.lookupKey(key, sourceAccountId)
                .onSuccess { found ->
                    _uiState.update {
                        it.copy(
                            isLookingUp = false,
                            recipient = found,
                            pixKey = found.keyValue,
                            error = null
                        )
                    }
                    onSuccess()
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLookingUp = false,
                            recipient = null,
                            error = e.message ?: "Chave Pix não encontrada."
                        )
                    }
                }
        }
    }

    fun submit() {
        val state = _uiState.value
        if (state.isLoading) return
        val amount = parseAmountInput(state.amount)
        val error = when {
            state.pixKey.isBlank() -> "Informe a chave Pix do destinatário."
            amount == null -> "Informe um valor válido maior que zero (ex.: 10,50)."
            else -> null
        }
        if (error != null || amount == null) {
            _uiState.update { it.copy(error = error) }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val request = TransferRequest(
                sourceAccountId = sourceAccountId,
                pixKey = state.pixKey.trim(),
                amount = amount,
                description = state.description.trim().ifEmpty { null }
            )
            repository.transfer(request, idempotencyKey)
                .onSuccess { receipt ->
                    idempotencyKey = newIdempotencyKey()
                    _uiState.update { it.copy(isLoading = false, receipt = receipt) }
                }
                .onFailure { e ->
                    // The backend stores rejected transfers under their key, so only a transport
                    // failure (outcome unknown) may be retried with the same key.
                    if (e !is NetworkException) idempotencyKey = newIdempotencyKey()
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun startNewTransfer() {
        idempotencyKey = newIdempotencyKey()
        _uiState.value = TransferUiState()
    }

    private fun edit(block: TransferUiState.() -> TransferUiState) {
        // Changing the data means a different transfer, so the old key must not be reused.
        idempotencyKey = newIdempotencyKey()
        _uiState.update { it.block().copy(error = null) }
    }

    companion object {
        fun factory(sourceAccountId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = container()
                TransferViewModel(sourceAccountId, c.transferRepository, c.pixKeyRepository)
            }
        }
    }
}
