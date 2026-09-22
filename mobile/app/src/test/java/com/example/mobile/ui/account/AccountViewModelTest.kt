package com.example.mobile.ui.account

import com.example.mobile.model.AccountResponse
import com.example.mobile.model.UserCreateRequest
import com.example.mobile.model.UserResponse
import com.example.mobile.model.UserUpdateRequest
import com.example.mobile.repository.AccountRepository
import com.example.mobile.repository.UserRepository
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
            status = "active",
            user_id = 1L
        )
    )

    override suspend fun getAccount(id: Long): Result<AccountResponse> {
        return resultToReturn
    }

    override suspend fun createAccount(userId: Long, agencyNumber: String): Result<AccountResponse> {
        return resultToReturn
    }

    override suspend fun deleteAccount(id: Long): Result<Unit> {
        return Result.success(Unit)
    }
}

class FakeUserRepository : UserRepository {
    var userToReturn: Result<UserResponse> = Result.success(
        UserResponse(
            id = 1L,
            name = "Warley Silva",
            email = "warley@example.com",
            doc_id = "52998224725",
            phone = "+5585999999999"
        )
    )

    override suspend fun getUser(id: Long): Result<UserResponse> = userToReturn

    override suspend fun createUser(request: UserCreateRequest): Result<UserResponse> = userToReturn

    override suspend fun updateUser(id: Long, request: UserUpdateRequest): Result<UserResponse> =
        Result.success(
            UserResponse(
                id = id,
                name = request.name,
                email = request.email,
                doc_id = "52998224725",
                phone = request.phone
            )
        )

    override suspend fun deleteUser(id: Long): Result<Unit> = Result.success(Unit)

    override suspend fun getUserAccounts(userId: Long): Result<List<AccountResponse>> =
        Result.success(emptyList())
}

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeAccountRepository: FakeAccountRepository
    private lateinit var fakeUserRepository: FakeUserRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeAccountRepository = FakeAccountRepository()
        fakeUserRepository = FakeUserRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_loadsAccountSuccessfully_emitsSuccessState() = runTest(testDispatcher) {
        val viewModel = AccountViewModel(
            accountRepository = fakeAccountRepository,
            userRepository = fakeUserRepository
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Success state, but was $state", state is AccountUiState.Success)
        val successState = state as AccountUiState.Success
        assertEquals(1L, successState.account.id)
        assertEquals("123456", successState.account.account_number)
        assertEquals(1000.0, successState.account.balance, 0.0)
        assertEquals("Warley Silva", successState.user?.name)
    }

    @Test
    fun loadAccount_failure_emitsErrorState() = runTest(testDispatcher) {
        fakeAccountRepository.resultToReturn = Result.failure(Exception("Conta não encontrada"))

        val viewModel = AccountViewModel(
            accountRepository = fakeAccountRepository,
            userRepository = fakeUserRepository
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Error state, but was $state", state is AccountUiState.Error)
        assertEquals("Conta não encontrada", (state as AccountUiState.Error).message)
    }

    @Test
    fun toggleBalanceVisibility_togglesFlagCorrectly() = runTest(testDispatcher) {
        val viewModel = AccountViewModel(
            accountRepository = fakeAccountRepository,
            userRepository = fakeUserRepository
        )

        advanceUntilIdle()

        val initialState = viewModel.uiState.value as AccountUiState.Success
        assertTrue(initialState.isBalanceVisible)

        viewModel.toggleBalanceVisibility()
        val toggledState = viewModel.uiState.value as AccountUiState.Success
        assertFalse(toggledState.isBalanceVisible)

        viewModel.toggleBalanceVisibility()
        val restoredState = viewModel.uiState.value as AccountUiState.Success
        assertTrue(restoredState.isBalanceVisible)
    }

    @Test
    fun updateUser_success_updatesUserState() = runTest(testDispatcher) {
        val viewModel = AccountViewModel(
            accountRepository = fakeAccountRepository,
            userRepository = fakeUserRepository
        )

        advanceUntilIdle()

        var callbackSuccess = false
        viewModel.updateUser("Novo Nome", "novo@email.com", "+5585988888888") { success, _ ->
            callbackSuccess = success
        }

        advanceUntilIdle()

        assertTrue(callbackSuccess)
        val state = viewModel.uiState.value as AccountUiState.Success
        assertEquals("Novo Nome", state.user?.name)
    }

    @Test
    fun deleteAccount_withBalance_isBlocked() = runTest(testDispatcher) {
        val viewModel = AccountViewModel(
            accountRepository = fakeAccountRepository,
            userRepository = fakeUserRepository
        )

        advanceUntilIdle()

        var callbackSuccess = true
        var callbackMessage = ""
        viewModel.deleteAccount { success, msg ->
            callbackSuccess = success
            callbackMessage = msg
        }

        advanceUntilIdle()

        assertFalse(callbackSuccess)
        assertTrue(callbackMessage.contains("Não é possível encerrar conta com saldo positivo"))
    }
}
