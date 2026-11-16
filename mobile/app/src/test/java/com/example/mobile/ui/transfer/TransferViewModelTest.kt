package com.example.mobile.ui.transfer

import com.example.mobile.model.Counterparty
import com.example.mobile.model.PixKeyLookupResponse
import com.example.mobile.model.StatementEntry
import com.example.mobile.model.TransferParty
import com.example.mobile.model.TransferReceipt
import com.example.mobile.model.TransferRequest
import com.example.mobile.network.ApiException
import com.example.mobile.network.InMemoryRecentPixContactsStore
import com.example.mobile.network.NetworkException
import com.example.mobile.repository.TransferRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransferViewModelTest {

    private class FakeRepo(var result: Result<TransferReceipt>) : TransferRepository {
        val requests = mutableListOf<Pair<TransferRequest, String>>()
        override suspend fun transfer(request: TransferRequest, idempotencyKey: String): Result<TransferReceipt> {
            requests += request to idempotencyKey
            return result
        }
        override suspend fun statement(accountId: Long): Result<List<StatementEntry>> = Result.success(emptyList())
    }

    private val receipt = TransferReceipt(id = 1, amount = "10.50", status = "completed")
    private var counter = 0
    private fun keys() = "key${counter++}"

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(repo: FakeRepo) = TransferViewModel(5, repo, newIdempotencyKey = ::keys)

    @Test fun submit_validatesInputsBeforeCallingTheApi() {
        val repo = FakeRepo(Result.success(receipt))
        val vm = vm(repo)

        vm.submit()
        assertNotNull(vm.uiState.value.error)
        vm.onPixKeyChange("bia@x.com")
        vm.onAmountChange("0")
        vm.submit()

        assertNotNull(vm.uiState.value.error)
        assertEquals(0, repo.requests.size)
    }

    @Test fun submit_success_exposesReceiptAndSendsNormalizedAmount() {
        val repo = FakeRepo(Result.success(receipt))
        val vm = vm(repo)
        vm.onPixKeyChange(" bia@x.com ")
        vm.onAmountChange("10,5")
        vm.submit()

        assertEquals(receipt, vm.uiState.value.receipt)
        val (request, _) = repo.requests.single()
        assertEquals("10.50", request.amount)
        assertEquals("bia@x.com", request.pixKey)
        assertEquals(5L, request.sourceAccountId)
    }

    @Test fun networkFailure_retryReusesIdempotencyKey() {
        val repo = FakeRepo(Result.failure(NetworkException("offline")))
        val vm = vm(repo)
        vm.onPixKeyChange("bia@x.com")
        vm.onAmountChange("10")
        vm.submit()
        vm.submit()

        assertEquals(2, repo.requests.size)
        assertEquals(repo.requests[0].second, repo.requests[1].second)
        assertNull(vm.uiState.value.receipt)
    }

    @Test fun businessFailure_retryUsesNewIdempotencyKey() {
        val repo = FakeRepo(Result.failure(ApiException("Insufficient funds")))
        val vm = vm(repo)
        vm.onPixKeyChange("bia@x.com")
        vm.onAmountChange("10")
        vm.submit()
        vm.submit()

        assertEquals("Insufficient funds", vm.uiState.value.error)
        assertEquals(false, repo.requests[0].second == repo.requests[1].second)
    }

    @Test fun applyPrefill_setsRecipientAmountAndDescription() {
        val repo = FakeRepo(Result.success(receipt))
        val vm = vm(repo)
        val recipient = PixKeyLookupResponse(
            keyType = "email",
            keyValue = "bia@x.com",
            accountId = 9L,
            accountNumber = "9999-1",
            recipientName = "Beatriz"
        )

        vm.applyPrefill("bia@x.com", "25.00", recipient, "Café")

        assertEquals("bia@x.com", vm.uiState.value.pixKey)
        assertEquals("25.00", vm.uiState.value.amount)
        assertEquals("Beatriz", vm.uiState.value.recipient?.recipientName)
        assertEquals("Café", vm.uiState.value.description)
    }

    @Test fun recentContacts_usesRealPixKeyInsteadOfName() {
        val repo = FakeRepo(
            Result.success(
                TransferReceipt(
                    id = 2,
                    amount = "15.00",
                    status = "completed",
                    receiver = TransferParty(accountNumber = "222", userName = "Carlos Silva")
                )
            )
        )
        val store = InMemoryRecentPixContactsStore()
        val vm = TransferViewModel(5, repo, recentContactsStore = store, newIdempotencyKey = ::keys)

        vm.onPixKeyChange("carlos@rubank.com")
        vm.onAmountChange("15")
        vm.submit()

        val statement = listOf(
            StatementEntry(
                id = 10,
                amount = "15.00",
                operationType = "debit",
                status = "completed",
                counterparty = Counterparty(name = "Carlos Silva")
            ),
            StatementEntry(
                id = 11,
                amount = "99.00",
                operationType = "debit",
                status = "completed",
                counterparty = Counterparty(name = "Sem Chave")
            ),
            StatementEntry(
                id = 12,
                amount = "30.00",
                operationType = "debit",
                status = "completed",
                pixKeyUsed = "+5511999990000",
                counterparty = Counterparty(name = "Ana")
            )
        )

        val contacts = vm.buildRecentContacts(statement)
        assertEquals(2, contacts.size)
        assertEquals("Carlos Silva", contacts[0].name)
        assertEquals("carlos@rubank.com", contacts[0].pixKey)
        assertEquals("Ana", contacts[1].name)
        assertEquals("+5511999990000", contacts[1].pixKey)
    }
}
