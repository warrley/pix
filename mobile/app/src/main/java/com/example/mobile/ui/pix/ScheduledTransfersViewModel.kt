package com.example.mobile.ui.pix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mobile.container
import com.example.mobile.model.PixKeyLookupResponse
import com.example.mobile.model.ScheduledTransferCreateRequest
import com.example.mobile.model.ScheduledTransferResponse
import com.example.mobile.repository.PixKeyRepository
import com.example.mobile.repository.ScheduledTransferRepository
import com.example.mobile.util.parseAmountInput
import com.example.mobile.util.todayIsoDate
import com.example.mobile.util.tomorrowIsoDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ScheduledFilter(val label: String) {
    ALL("Todos"),
    PENDING("Agendados"),
    EXECUTED("Executados"),
    FINISHED("Cancelados/Falhos")
}

data class ScheduledTransfersUiState(
    val items: List<ScheduledTransferResponse> = emptyList(),
    val filter: ScheduledFilter = ScheduledFilter.ALL,
    val isLoading: Boolean = true,
    val cancellingId: Long? = null,
    val error: String? = null,
    val message: String? = null,
    // Create form fields
    val pixKey: String = "",
    val amount: String = "",
    val description: String = "",
    val scheduledFor: String = tomorrowIsoDate(),
    val verifiedRecipient: PixKeyLookupResponse? = null,
    val isLookingUp: Boolean = false,
    val isSubmitting: Boolean = false,
    val formError: String? = null
) {
    val filteredItems: List<ScheduledTransferResponse>
        get() = when (filter) {
            ScheduledFilter.ALL -> items
            ScheduledFilter.PENDING -> items.filter { it.status == "pending" || it.status == "processing" }
            ScheduledFilter.EXECUTED -> items.filter { it.status == "executed" }
            ScheduledFilter.FINISHED -> items.filter { it.status == "cancelled" || it.status == "failed" }
        }
}

class ScheduledTransfersViewModel(
    private val accountId: Long,
    private val repository: ScheduledTransferRepository,
    private val pixKeyRepository: PixKeyRepository,
    private val todayProvider: () -> String = { todayIsoDate() },
    private val tomorrowProvider: () -> String = { tomorrowIsoDate() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ScheduledTransfersUiState(scheduledFor = tomorrowProvider())
    )
    val uiState: StateFlow<ScheduledTransfersUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repository.list(accountId)
                .onSuccess { list ->
                    _uiState.update { it.copy(items = list, isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun setFilter(filter: ScheduledFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun onPixKeyChange(value: String) {
        _uiState.update { it.copy(pixKey = value, verifiedRecipient = null, formError = null) }
    }

    fun onAmountChange(value: String) {
        _uiState.update { it.copy(amount = value, formError = null) }
    }

    fun onDescriptionChange(value: String) {
        _uiState.update { it.copy(description = value.take(140), formError = null) }
    }

    fun onScheduledForChange(isoDate: String) {
        _uiState.update { it.copy(scheduledFor = isoDate, formError = null) }
    }

    fun resetForm() {
        _uiState.update {
            it.copy(
                pixKey = "",
                amount = "",
                description = "",
                scheduledFor = tomorrowProvider(),
                verifiedRecipient = null,
                isLookingUp = false,
                isSubmitting = false,
                formError = null
            )
        }
    }

    fun lookupRecipient() {
        val state = _uiState.value
        if (state.isLookingUp || state.isSubmitting) return
        val key = state.pixKey.trim()
        if (key.isEmpty()) {
            _uiState.update { it.copy(formError = "Informe a chave Pix do destinatário.") }
            return
        }
        _uiState.update { it.copy(isLookingUp = true, formError = null) }
        viewModelScope.launch {
            pixKeyRepository.lookupKey(key, accountId)
                .onSuccess { found ->
                    _uiState.update {
                        it.copy(
                            isLookingUp = false,
                            pixKey = found.keyValue,
                            verifiedRecipient = found,
                            formError = null
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLookingUp = false,
                            verifiedRecipient = null,
                            formError = e.message ?: "Chave Pix não encontrada."
                        )
                    }
                }
        }
    }

    fun createScheduledTransfer(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.isSubmitting || state.isLookingUp) return

        val key = state.pixKey.trim()
        val parsedAmount = parseAmountInput(state.amount)
        val date = state.scheduledFor.trim()
        val today = todayProvider()

        val validationError = when {
            key.isEmpty() -> "Informe a chave Pix do destinatário."
            parsedAmount == null -> "Informe um valor válido maior que zero (ex.: 25,00)."
            !date.matches(Regex("""\d{4}-\d{2}-\d{2}""")) -> "Escolha uma data válida para o agendamento."
            date <= today -> "A data do agendamento deve ser futura (a partir de amanhã)."
            else -> null
        }

        if (validationError != null || parsedAmount == null) {
            _uiState.update { it.copy(formError = validationError) }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, formError = null, message = null) }
        viewModelScope.launch {
            if (state.verifiedRecipient == null) {
                val lookup = pixKeyRepository.lookupKey(key, accountId)
                if (lookup.isFailure) {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            formError = lookup.exceptionOrNull()?.message ?: "Chave Pix inválida."
                        )
                    }
                    return@launch
                }
                lookup.getOrNull()?.let { found ->
                    _uiState.update { it.copy(verifiedRecipient = found, pixKey = found.keyValue) }
                }
            }

            val resolvedKey = _uiState.value.pixKey.trim()
            val request = ScheduledTransferCreateRequest(
                pixKey = resolvedKey,
                amount = parsedAmount,
                description = state.description.trim().ifEmpty { null },
                scheduledFor = date
            )

            repository.create(accountId, request)
                .onSuccess { created ->
                    _uiState.update { current ->
                        current.copy(
                            items = (current.items + created).sortedWith(
                                compareBy<ScheduledTransferResponse> { it.scheduledFor }.thenBy { it.id }
                            ),
                            isSubmitting = false,
                            message = "Pix agendado com sucesso!"
                        )
                    }
                    resetForm()
                    onSuccess()
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            formError = e.message ?: "Não foi possível agendar a transferência."
                        )
                    }
                }
        }
    }

    fun cancel(item: ScheduledTransferResponse) {
        if (_uiState.value.cancellingId != null) return
        _uiState.update { it.copy(cancellingId = item.id, error = null, message = null) }
        viewModelScope.launch {
            repository.cancel(item.id)
                .onSuccess { updated ->
                    _uiState.update { current ->
                        current.copy(
                            items = current.items.map { if (it.id == updated.id) updated else it },
                            cancellingId = null,
                            message = "Agendamento cancelado."
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            cancellingId = null,
                            error = e.message ?: "Não foi possível cancelar o agendamento."
                        )
                    }
                }
        }
    }

    companion object {
        fun factory(accountId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = container()
                ScheduledTransfersViewModel(
                    accountId = accountId,
                    repository = c.scheduledTransferRepository,
                    pixKeyRepository = c.pixKeyRepository
                )
            }
        }
    }
}
