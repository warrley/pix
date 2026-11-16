package com.example.mobile.ui.home

import com.example.mobile.model.AccountLimitResponse
import com.example.mobile.model.AccountResponse
import com.example.mobile.model.SignUpRequest
import com.example.mobile.model.StatementEntry
import com.example.mobile.model.TransferReceipt
import com.example.mobile.model.TransferRequest
import com.example.mobile.model.UserResponse
import com.example.mobile.network.InMemoryNotificationPreferencesStore
import com.example.mobile.network.Session
import com.example.mobile.repository.AccountRepository
import com.example.mobile.repository.AuthRepository
import com.example.mobile.repository.TransferRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private class FakeAuthRepo : AuthRepository {
        private val flow = MutableStateFlow<Session?>(
            Session("tok", UserResponse(id = 7L, name = "Alice", email = "alice@rubank.com", phone = "+5511999990000"))
        )
        override val session: StateFlow<Session?> = flow
        override val sessionExpired: StateFlow<Boolean> = MutableStateFlow(false)
        override suspend fun login(email: String, password: String) = Result.success(flow.value!!.user)
        override suspend fun signUp(request: SignUpRequest) =
            Result.success(flow.value!!.user)
        override suspend fun updateProfile(name: String, email: String, phone: String?) =
            Result.success(flow.value!!.user)
        override suspend fun logout() {}
        override fun acknowledgeExpiration() {}
    }

    private class FakeAccountRepo : AccountRepository {
        override suspend fun getAccounts(userId: Long) = Result.success(emptyList<AccountResponse>())
        override suspend fun createAccount(agencyNumber: String): Result<AccountResponse> =
            Result.failure(UnsupportedOperationException())
        override suspend fun closeAccount(id: Long) = Result.success(Unit)
        override suspend fun getLimit(accountId: Long): Result<AccountLimitResponse> =
            Result.failure(UnsupportedOperationException())
        override suspend fun updateLimit(accountId: Long, dailyLimit: String): Result<AccountLimitResponse> =
            Result.failure(UnsupportedOperationException())
    }

    private class FakeTransferRepo : TransferRepository {
        override suspend fun transfer(request: TransferRequest, idempotencyKey: String): Result<TransferReceipt> =
            Result.failure(UnsupportedOperationException())
        override suspend fun statement(
            accountId: Long,
            startDate: String?,
            endDate: String?,
            status: String?,
            page: Int,
            perPage: Int
        ) = Result.success(emptyList<StatementEntry>())
        override suspend fun refund(transactionId: Long, reason: String): Result<TransferReceipt> =
            Result.failure(UnsupportedOperationException())
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun togglesSmsAndEmailNotifications_persistsPerUser() {
        val store = InMemoryNotificationPreferencesStore()
        val vm = HomeViewModel(FakeAuthRepo(), FakeAccountRepo(), FakeTransferRepo(), store)

        assertFalse(vm.uiState.value.notificationPreferences.smsEnabled)
        assertTrue(vm.uiState.value.notificationPreferences.emailEnabled)

        vm.setSmsNotifications(true)
        vm.setEmailNotifications(false)

        assertTrue(store.load(7L).smsEnabled)
        assertFalse(store.load(7L).emailEnabled)
    }
}
