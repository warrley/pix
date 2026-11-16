package com.example.mobile.ui.pix

import com.example.mobile.model.PixKeyLookupResponse
import com.example.mobile.model.PixKeyResponse
import com.example.mobile.model.ScheduledTransferCreateRequest
import com.example.mobile.model.ScheduledTransferResponse
import com.example.mobile.network.ApiException
import com.example.mobile.repository.PixKeyRepository
import com.example.mobile.repository.ScheduledTransferRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduledTransfersViewModelTest {

    private class FakeScheduledRepo(
        var listResult: Result<List<ScheduledTransferResponse>> = Result.success(emptyList())
    ) : ScheduledTransferRepository {
        val createdRequests = mutableListOf<ScheduledTransferCreateRequest>()
        val cancelledIds = mutableListOf<Long>()

        override suspend fun list(accountId: Long) = listResult

        override suspend fun create(accountId: Long, request: ScheduledTransferCreateRequest): Result<ScheduledTransferResponse> {
            createdRequests += request
            return Result.success(
                ScheduledTransferResponse(
                    id = 55L,
                    accountId = accountId,
                    destinationAccountId = 2L,
                    pixKeyUsed = request.pixKey,
                    amount = request.amount,
                    description = request.description,
                    scheduledFor = request.scheduledFor,
                    status = "pending"
                )
            )
        }

        override suspend fun cancel(id: Long): Result<ScheduledTransferResponse> {
            cancelledIds += id
            return Result.success(
                ScheduledTransferResponse(
                    id = id,
                    accountId = 1L,
                    destinationAccountId = 2L,
                    pixKeyUsed = "bob@rubank.com",
                    amount = "25.00",
                    scheduledFor = "2026-11-20",
                    status = "cancelled"
                )
            )
        }
    }

    private class FakePixKeyRepo : PixKeyRepository {
        var lookupResult: Result<PixKeyLookupResponse> = Result.success(
            PixKeyLookupResponse(
                keyType = "email",
                keyValue = "bob@rubank.com",
                accountId = 2L,
                accountNumber = "1234-5",
                recipientName = "Bob"
            )
        )
        override suspend fun getKeys(accountId: Long): Result<List<PixKeyResponse>> = Result.success(emptyList())
        override suspend fun createKey(accountId: Long, keyType: String, keyValue: String?): Result<PixKeyResponse> =
            Result.failure(UnsupportedOperationException())
        override suspend fun deleteKey(id: Long): Result<Unit> = Result.success(Unit)
        override suspend fun lookupKey(key: String, sourceAccountId: Long) = lookupResult
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(
        repo: FakeScheduledRepo = FakeScheduledRepo(),
        keyRepo: FakePixKeyRepo = FakePixKeyRepo()
    ) = ScheduledTransfersViewModel(
        accountId = 1L,
        repository = repo,
        pixKeyRepository = keyRepo,
        todayProvider = { "2026-11-16" },
        tomorrowProvider = { "2026-11-17" }
    )

    @Test
    fun createScheduledTransfer_rejectsTodayOrPastDates() {
        val repo = FakeScheduledRepo()
        val vm = vm(repo)

        vm.onPixKeyChange("bob@rubank.com")
        vm.onAmountChange("50")
        vm.onScheduledForChange("2026-11-16")
        vm.createScheduledTransfer {}

        assertNotNull(vm.uiState.value.formError)
        assertEquals(0, repo.createdRequests.size)
    }

    @Test
    fun createScheduledTransfer_success_addsItemAndClearsForm() {
        val repo = FakeScheduledRepo()
        val vm = vm(repo)

        vm.onPixKeyChange("bob@rubank.com")
        vm.onAmountChange("45,5")
        vm.onScheduledForChange("2026-11-20")
        vm.onDescriptionChange("Aluguel")

        var callbackCalled = false
        vm.createScheduledTransfer { callbackCalled = true }

        assertTrue(callbackCalled)
        assertEquals(1, repo.createdRequests.size)
        assertEquals("45.50", repo.createdRequests.single().amount)
        assertEquals("2026-11-20", repo.createdRequests.single().scheduledFor)
        assertEquals(1, vm.uiState.value.items.size)
        assertEquals("", vm.uiState.value.pixKey)
    }

    @Test
    fun cancel_updatesStatusToCancelled() {
        val existing = ScheduledTransferResponse(
            id = 12L,
            accountId = 1L,
            destinationAccountId = 2L,
            pixKeyUsed = "bob@rubank.com",
            amount = "25.00",
            scheduledFor = "2026-11-20",
            status = "pending"
        )
        val repo = FakeScheduledRepo(Result.success(listOf(existing)))
        val vm = vm(repo)

        vm.cancel(existing)

        assertEquals(listOf(12L), repo.cancelledIds)
        assertEquals("cancelled", vm.uiState.value.items.single().status)
    }

    @Test
    fun createScheduledTransfer_propagatesLookupError() {
        val keyRepo = FakePixKeyRepo().apply {
            lookupResult = Result.failure(ApiException("Chave Pix não encontrada."))
        }
        val vm = vm(keyRepo = keyRepo)

        vm.onPixKeyChange("missing@x.com")
        vm.onAmountChange("10")
        vm.createScheduledTransfer {}

        assertEquals("Chave Pix não encontrada.", vm.uiState.value.formError)
    }
}
