package com.example.mobile.ui.pix

import com.example.mobile.model.AccountLimitResponse
import com.example.mobile.model.AccountResponse
import com.example.mobile.network.ApiException
import com.example.mobile.repository.AccountRepository
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
class DailyLimitViewModelTest {

    private class FakeAccountRepository : AccountRepository {
        var limit = AccountLimitResponse("5000.00", "250.00", "4750.00")
        var updatedValue: String? = null
        var updateError: String? = null

        override suspend fun getAccounts(userId: Long): Result<List<AccountResponse>> = Result.success(emptyList())
        override suspend fun createAccount(agencyNumber: String): Result<AccountResponse> =
            Result.failure(UnsupportedOperationException())
        override suspend fun closeAccount(id: Long): Result<Unit> = Result.success(Unit)
        override suspend fun getLimit(accountId: Long) = Result.success(limit)
        override suspend fun updateLimit(accountId: Long, dailyLimit: String): Result<AccountLimitResponse> {
            updateError?.let { return Result.failure(ApiException(it)) }
            updatedValue = dailyLimit
            limit = limit.copy(dailyLimit = dailyLimit, remaining = dailyLimit)
            return Result.success(limit)
        }
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadsLimitAndUpdatesWithNormalizedAmount() {
        val repo = FakeAccountRepository()
        val vm = DailyLimitViewModel(1L, repo)

        assertEquals("5000,00", vm.uiState.value.limitInput)

        vm.onLimitInputChange("1500,5")
        vm.save()

        assertEquals("1500.50", repo.updatedValue)
        assertNotNull(vm.uiState.value.message)
    }

    @Test
    fun rejectsInvalidLimitInputBeforeApiCall() {
        val repo = FakeAccountRepository()
        val vm = DailyLimitViewModel(1L, repo)

        vm.onLimitInputChange("abc")
        vm.save()

        assertNotNull(vm.uiState.value.error)
        assertEquals(null, repo.updatedValue)
    }
}
