package com.example.mobile.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mobile.container
import com.example.mobile.model.StatementEntry
import com.example.mobile.repository.TransferRepository
import com.example.mobile.util.daysAgoIsoDate
import com.example.mobile.util.todayIsoDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HistoryPeriodFilter(val label: String) {
    ALL("Tudo"),
    TODAY("Hoje"),
    DAYS_7("7 dias"),
    DAYS_30("30 dias"),
    CUSTOM("Período")
}

enum class HistoryTypeFilter(val label: String) {
    ALL("Todas"),
    SENT("Enviadas"),
    RECEIVED("Recebidas"),
    REFUNDED("Estornadas"),
    CANCELLED("Canceladas")
}

data class TransactionHistoryUiState(
    val entries: List<StatementEntry> = emptyList(),
    val periodFilter: HistoryPeriodFilter = HistoryPeriodFilter.ALL,
    val typeFilter: HistoryTypeFilter = HistoryTypeFilter.ALL,
    val searchQuery: String = "",
    val customStartDate: String? = null,
    val customEndDate: String? = null,
    val isLoading: Boolean = true,
    val isRefunding: Boolean = false,
    val error: String? = null,
    val refundError: String? = null,
    val message: String? = null
) {
    val filteredEntries: List<StatementEntry>
        get() {
            val query = searchQuery.trim().lowercase()
            return entries.filter { entry ->
                val matchesType = when (typeFilter) {
                    HistoryTypeFilter.ALL -> true
                    HistoryTypeFilter.SENT -> entry.isDebit
                    HistoryTypeFilter.RECEIVED -> !entry.isDebit
                    HistoryTypeFilter.REFUNDED -> entry.status == "refunded"
                    HistoryTypeFilter.CANCELLED -> entry.status == "cancelled"
                }
                val matchesQuery = query.isEmpty() ||
                    entry.counterparty.name.orEmpty().lowercase().contains(query) ||
                    entry.counterparty.docId.orEmpty().lowercase().contains(query) ||
                    entry.pixKeyUsed.orEmpty().lowercase().contains(query) ||
                    entry.endToEndId.orEmpty().lowercase().contains(query)
                matchesType && matchesQuery
            }
        }
}

class TransactionHistoryViewModel(
    private val accountId: Long,
    private val repository: TransferRepository,
    private val todayProvider: () -> String = { todayIsoDate() },
    private val daysAgoProvider: (Int) -> String = { daysAgoIsoDate(it) }
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionHistoryUiState())
    val uiState: StateFlow<TransactionHistoryUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        val state = _uiState.value
        val (startDate, endDate) = resolveDateRange(state)
        val backendStatus = when (state.typeFilter) {
            HistoryTypeFilter.CANCELLED -> "cancelled"
            else -> null
        }

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repository.statement(
                accountId = accountId,
                startDate = startDate,
                endDate = endDate,
                status = backendStatus,
                perPage = 100
            )
                .onSuccess { list ->
                    _uiState.update {
                        it.copy(
                            entries = list.filter { tx -> tx.status != "failed" },
                            isLoading = false
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun setPeriodFilter(filter: HistoryPeriodFilter) {
        _uiState.update { it.copy(periodFilter = filter) }
        if (filter != HistoryPeriodFilter.CUSTOM ||
            (_uiState.value.customStartDate != null && _uiState.value.customEndDate != null)
        ) {
            load()
        }
    }

    fun setCustomDateRange(startDate: String, endDate: String) {
        _uiState.update {
            it.copy(
                periodFilter = HistoryPeriodFilter.CUSTOM,
                customStartDate = startDate,
                customEndDate = endDate
            )
        }
        load()
    }

    fun setTypeFilter(filter: HistoryTypeFilter) {
        val prev = _uiState.value.typeFilter
        _uiState.update { it.copy(typeFilter = filter) }
        if (prev == HistoryTypeFilter.CANCELLED || filter == HistoryTypeFilter.CANCELLED) {
            load()
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun clearRefundError() {
        _uiState.update { it.copy(refundError = null) }
    }

    fun refundTransaction(
        entry: StatementEntry,
        reason: String,
        onSuccess: (updated: StatementEntry) -> Unit
    ) {
        val cleanReason = reason.trim()
        if (cleanReason.isEmpty()) {
            _uiState.update { it.copy(refundError = "Informe o motivo do estorno.") }
            return
        }
        if (_uiState.value.isRefunding) return

        _uiState.update { it.copy(isRefunding = true, refundError = null, message = null) }
        viewModelScope.launch {
            repository.refund(entry.id, cleanReason)
                .onSuccess { receipt ->
                    val updated = entry.copy(status = receipt.status)
                    _uiState.update { current ->
                        current.copy(
                            entries = current.entries.map { if (it.id == entry.id) updated else it },
                            isRefunding = false,
                            message = "Estorno realizado com sucesso!"
                        )
                    }
                    onSuccess(updated)
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isRefunding = false,
                            refundError = e.message ?: "Não foi possível estornar esta transação."
                        )
                    }
                }
        }
    }

    private fun resolveDateRange(state: TransactionHistoryUiState): Pair<String?, String?> {
        val today = todayProvider()
        return when (state.periodFilter) {
            HistoryPeriodFilter.ALL -> null to null
            HistoryPeriodFilter.TODAY -> today to today
            HistoryPeriodFilter.DAYS_7 -> daysAgoProvider(6) to today
            HistoryPeriodFilter.DAYS_30 -> daysAgoProvider(29) to today
            HistoryPeriodFilter.CUSTOM -> state.customStartDate to state.customEndDate
        }
    }

    companion object {
        fun factory(accountId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { TransactionHistoryViewModel(accountId, container().transferRepository) }
        }
    }
}
