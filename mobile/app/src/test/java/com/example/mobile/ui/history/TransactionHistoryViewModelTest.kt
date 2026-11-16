package com.example.mobile.ui.history

import com.example.mobile.model.Counterparty
import com.example.mobile.model.StatementEntry
import com.example.mobile.model.TransferReceipt
import com.example.mobile.model.TransferRequest
import com.example.mobile.network.ApiException
import com.example.mobile.repository.TransferRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionHistoryViewModelTest {

    private class FakeTransferRepository(
        var statementEntries: List<StatementEntry> = emptyList()
    ) : TransferRepository {
        val lastQueryParams = mutableListOf<Triple<String?, String?, String?>>()
        var refundResult: Result<TransferReceipt> = Result.success(
            TransferReceipt(id = 1L, amount = "50.00", status = "refunded")
        )

        override suspend fun transfer(request: TransferRequest, idempotencyKey: String): Result<TransferReceipt> =
            Result.failure(UnsupportedOperationException())

        override suspend fun statement(
            accountId: Long,
            startDate: String?,
            endDate: String?,
            status: String?,
            page: Int,
            perPage: Int
        ): Result<List<StatementEntry>> {
            lastQueryParams += Triple(startDate, endDate, status)
            return Result.success(statementEntries)
        }

        override suspend fun refund(transactionId: Long, reason: String) = refundResult
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val sampleEntries = listOf(
        StatementEntry(
            id = 1L,
            amount = "50.00",
            operationType = "debit",
            status = "completed",
            counterparty = Counterparty(name = "Maria Oliveira", docId = "***.123.456-**")
        ),
        StatementEntry(
            id = 2L,
            amount = "120.00",
            operationType = "credit",
            status = "refunded",
            counterparty = Counterparty(name = "Empresa LTDA", docId = "***")
        )
    )

    @Test
    fun filtersByTypeAndPersonSearch() {
        val repo = FakeTransferRepository(sampleEntries)
        val vm = TransactionHistoryViewModel(1L, repo, todayProvider = { "2026-11-16" })

        assertEquals(2, vm.uiState.value.filteredEntries.size)

        vm.setTypeFilter(HistoryTypeFilter.SENT)
        assertEquals(1, vm.uiState.value.filteredEntries.size)
        assertEquals(1L, vm.uiState.value.filteredEntries.single().id)

        vm.setTypeFilter(HistoryTypeFilter.ALL)
        vm.onSearchQueryChange("123.456")
        assertEquals(1, vm.uiState.value.filteredEntries.size)
        assertEquals("Maria Oliveira", vm.uiState.value.filteredEntries.single().counterparty.name)
    }

    @Test
    fun periodFilter_sendsDateRangeToBackend() {
        val repo = FakeTransferRepository(sampleEntries)
        val vm = TransactionHistoryViewModel(
            accountId = 1L,
            repository = repo,
            todayProvider = { "2026-11-16" },
            daysAgoProvider = { "2026-11-10" }
        )

        vm.setPeriodFilter(HistoryPeriodFilter.DAYS_7)
        assertEquals(Triple("2026-11-10", "2026-11-16", null), repo.lastQueryParams.last())
    }

    @Test
    fun refundTransaction_updatesStatusAndValidatesReason() {
        val repo = FakeTransferRepository(sampleEntries)
        val vm = TransactionHistoryViewModel(1L, repo)

        vm.refundTransaction(sampleEntries[0], "   ") {}
        assertNotNull(vm.uiState.value.refundError)

        var updatedStatus: String? = null
        vm.refundTransaction(sampleEntries[0], "Valor incorreto") { updatedStatus = it.status }
        assertEquals("refunded", updatedStatus)
        assertEquals("refunded", vm.uiState.value.entries.first().status)
    }

    @Test
    fun refundFailure_exposesBackendError() {
        val repo = FakeTransferRepository(sampleEntries).apply {
            refundResult = Result.failure(ApiException("Saldo insuficiente no destino"))
        }
        val vm = TransactionHistoryViewModel(1L, repo)

        vm.refundTransaction(sampleEntries[0], "Erro") {}
        assertEquals("Saldo insuficiente no destino", vm.uiState.value.refundError)
    }
}
