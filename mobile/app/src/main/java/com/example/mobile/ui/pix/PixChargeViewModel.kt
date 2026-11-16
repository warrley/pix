package com.example.mobile.ui.pix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mobile.container
import com.example.mobile.model.PixKeyResponse
import com.example.mobile.repository.PixKeyRepository
import com.example.mobile.util.PixQrCodeCodec
import com.example.mobile.util.PixQrPayload
import com.example.mobile.util.parseAmountInput
import com.example.mobile.util.toBrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PixChargeUiState(
    val userName: String = "",
    val keys: List<PixKeyResponse> = emptyList(),
    val selectedKey: PixKeyResponse? = null,
    val amountInput: String = "0",
    val description: String = "",
    val isLoading: Boolean = true,
    val isCreatingKey: Boolean = false,
    val error: String? = null
) {
    val normalizedAmount: String?
        get() = parseAmountInput(amountInput)

    val formattedAmount: String
        get() = normalizedAmount?.toBrl() ?: "R$ 0,00"

    val hasFixedAmount: Boolean
        get() = normalizedAmount != null

    val qrCodeText: String?
        get() {
            val key = selectedKey ?: return null
            return PixQrCodeCodec.encode(
                PixQrPayload(
                    pixKey = key.keyValue,
                    recipientName = userName,
                    amount = normalizedAmount,
                    description = description.trim().ifEmpty { null }
                )
            )
        }
}

class PixChargeViewModel(
    private val accountId: Long,
    private val userName: String,
    private val repository: PixKeyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PixChargeUiState(userName = userName))
    val uiState: StateFlow<PixChargeUiState> = _uiState.asStateFlow()

    init {
        loadKeys()
    }

    fun loadKeys() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repository.getKeys(accountId)
                .onSuccess { keys ->
                    val active = keys.filter { it.status == "active" }
                    _uiState.update { current ->
                        val kept = current.selectedKey?.let { sel -> active.firstOrNull { it.id == sel.id } }
                        current.copy(
                            keys = active,
                            selectedKey = kept ?: active.firstOrNull(),
                            isLoading = false
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun selectKey(key: PixKeyResponse) {
        _uiState.update { it.copy(selectedKey = key, error = null) }
    }

    fun onAmountChange(raw: String) {
        val sanitized = sanitizeAmountInput(previous = _uiState.value.amountInput, raw = raw) ?: return
        _uiState.update { it.copy(amountInput = sanitized, error = null) }
    }

    fun onDescriptionChange(value: String) {
        _uiState.update { it.copy(description = value.take(40), error = null) }
    }

    fun createRandomKey() {
        if (_uiState.value.isCreatingKey) return
        _uiState.update { it.copy(isCreatingKey = true, error = null) }
        viewModelScope.launch {
            repository.createKey(accountId, "random", null)
                .onSuccess { created ->
                    _uiState.update { current ->
                        current.copy(
                            keys = current.keys + created,
                            selectedKey = created,
                            isCreatingKey = false
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isCreatingKey = false, error = e.message) }
                }
        }
    }

    private fun sanitizeAmountInput(previous: String, raw: String): String? {
        if (raw.isEmpty()) return ""
        val filtered = raw.filter { it.isDigit() || it == ',' || it == '.' }
        if (filtered.length != raw.length) return null

        val separatorCount = filtered.count { it == ',' || it == '.' }
        if (separatorCount > 1) return null

        var normalized = when {
            filtered.startsWith(",") || filtered.startsWith(".") -> "0$filtered"
            previous == "0" && filtered.length == 2 && filtered.all { it.isDigit() } ->
                filtered.trimStart('0').ifEmpty { "0" }
            else -> filtered
        }

        val sepIdx = normalized.indexOfFirst { it == ',' || it == '.' }
        if (sepIdx >= 0) {
            val intPart = normalized.substring(0, sepIdx).trimStart('0').ifEmpty { "0" }
            val decPart = normalized.substring(sepIdx + 1)
            if (decPart.length > 2) return null
            normalized = intPart + normalized[sepIdx] + decPart
        } else if (normalized.length > 1) {
            normalized = normalized.trimStart('0').ifEmpty { "0" }
        }

        return normalized
    }

    companion object {
        fun factory(accountId: Long, userName: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PixChargeViewModel(accountId, userName, container().pixKeyRepository)
            }
        }
    }
}
