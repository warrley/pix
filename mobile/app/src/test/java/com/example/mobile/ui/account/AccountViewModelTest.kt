package com.example.mobile.ui.account

import com.example.mobile.model.AccountResponse
import com.example.mobile.repository.AccountRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeAccountRepository : AccountRepository {
    var resultToReturn: Result<AccountResponse> = Result.success(
        AccountResponse(
            id = 1L,
            agency_number = "0001",
            account_number = "123456",
            balance = 1000.0,
            status = "active"
        )
    )

    override suspend fun getAccount(id: Long): Result<AccountResponse> {
        return resultToReturn
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeAccountRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeAccountRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_loadsAccountSuccessfully_emitsSuccessState() = runTest(testDispatcher) {
        val expectedAccount = AccountResponse(
            id = 1L,
            agency_number = "0001",
            account_number = "123456",
            balance = 2500.50,
            status = "active"
        )
        fakeRepository.resultToReturn = Result.success(expectedAccount)

        val viewModel = AccountViewModel(fakeRepository)

        // Advance dispatcher to execute launch block
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Success state, but was $state", state is AccountUiState.Success)
        val successState = state as AccountUiState.Success
        assertEquals(expectedAccount, successState.account)
        assertTrue(successState.isBalanceVisible)
    }

    @Test
    fun loadAccount_failure_emitsErrorState() = runTest(testDispatcher) {
        val errorMessage = "Conta não encontrada (404)"
        fakeRepository.resultToReturn = Result.failure(Exception(errorMessage))

        val viewModel = AccountViewModel(fakeRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Error state, but was $state", state is AccountUiState.Error)
        val errorState = state as AccountUiState.Error
        assertEquals(errorMessage, errorState.message)
    }

    @Test
    fun toggleBalanceVisibility_togglesFlagCorrectly() = runTest(testDispatcher) {
        val viewModel = AccountViewModel(fakeRepository)
        advanceUntilIdle()

        val initialSuccess = viewModel.uiState.value as AccountUiState.Success
        assertTrue(initialSuccess.isBalanceVisible)

        viewModel.toggleBalanceVisibility()
        val toggledState = viewModel.uiState.value as AccountUiState.Success
        assertFalse(toggledState.isBalanceVisible)

        viewModel.toggleBalanceVisibility()
        val restoredState = viewModel.uiState.value as AccountUiState.Success
        assertTrue(restoredState.isBalanceVisible)
    }
}
