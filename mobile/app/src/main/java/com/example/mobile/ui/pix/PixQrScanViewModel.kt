package com.example.mobile.ui.pix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mobile.container
import com.example.mobile.model.PixKeyLookupResponse
import com.example.mobile.repository.PixKeyRepository
import com.example.mobile.util.PixQrCodeCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VerifiedQrTransfer(
    val pixKey: String,
    val amount: String,
    val recipient: PixKeyLookupResponse,
    val description: String
)

data class PixQrScanUiState(
    val isPasteMode: Boolean = false,
    val pastedCode: String = "",
    val isLookingUp: Boolean = false,
    val error: String? = null,
    val verifiedTransfer: VerifiedQrTransfer? = null
)

class PixQrScanViewModel(
    private val sourceAccountId: Long,
    startInPasteMode: Boolean,
    private val repository: PixKeyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PixQrScanUiState(isPasteMode = startInPasteMode))
    val uiState: StateFlow<PixQrScanUiState> = _uiState.asStateFlow()

    fun togglePasteMode(pasteMode: Boolean) {
        _uiState.update { it.copy(isPasteMode = pasteMode, error = null) }
    }

    fun onPastedCodeChange(code: String) {
        _uiState.update { it.copy(pastedCode = code, error = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun onCameraQrDetected(rawCode: String) {
        val current = _uiState.value
        if (current.isPasteMode || current.isLookingUp || current.verifiedTransfer != null || current.error != null) {
            return
        }
        resolveCode(rawCode)
    }

    fun submitPastedCode() {
        if (_uiState.value.isLookingUp) return
        resolveCode(_uiState.value.pastedCode)
    }

    fun consumeNavigation() {
        _uiState.update { it.copy(verifiedTransfer = null) }
    }

    private fun resolveCode(rawCode: String) {
        val trimmed = rawCode.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(error = "Informe ou escaneie um código Pix válido.") }
            return
        }

        val parsed = PixQrCodeCodec.decode(trimmed)
        if (parsed == null) {
            _uiState.update {
                it.copy(error = "QR Code inválido. Escaneie ou cole um código Pix válido.")
            }
            return
        }

        _uiState.update { it.copy(isLookingUp = true, error = null) }
        viewModelScope.launch {
            repository.lookupKey(parsed.pixKey, sourceAccountId)
                .onSuccess { found ->
                    _uiState.update {
                        it.copy(
                            isLookingUp = false,
                            error = null,
                            verifiedTransfer = VerifiedQrTransfer(
                                pixKey = found.keyValue,
                                amount = parsed.amount.orEmpty(),
                                recipient = found,
                                description = parsed.description.orEmpty()
                            )
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLookingUp = false,
                            error = e.message ?: "Não foi possível validar o destinatário deste QR Code."
                        )
                    }
                }
        }
    }

    companion object {
        fun factory(sourceAccountId: Long, startInPasteMode: Boolean): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    PixQrScanViewModel(
                        sourceAccountId = sourceAccountId,
                        startInPasteMode = startInPasteMode,
                        repository = container().pixKeyRepository
                    )
                }
            }
    }
}
