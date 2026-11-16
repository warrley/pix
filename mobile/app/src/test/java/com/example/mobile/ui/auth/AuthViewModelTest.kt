package com.example.mobile.ui.auth

import com.example.mobile.model.SignUpRequest
import com.example.mobile.model.UserResponse
import com.example.mobile.network.ApiException
import com.example.mobile.network.Session
import com.example.mobile.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
class AuthViewModelTest {

    private class FakeAuth : AuthRepository {
        override val session = MutableStateFlow<Session?>(null)
        override val sessionExpired: StateFlow<Boolean> = MutableStateFlow(false)
        val user = UserResponse(1, "Ana", "a@x.com")
        var signUpResult: Result<UserResponse> = Result.success(user)
        var loginResult: Result<UserResponse> = Result.success(user)
        var signUps = 0
        var logins = 0
        var lastSignUp: SignUpRequest? = null

        override suspend fun signUp(request: SignUpRequest): Result<UserResponse> {
            signUps++; lastSignUp = request; return signUpResult
        }
        override suspend fun login(email: String, password: String): Result<UserResponse> {
            logins++; return loginResult
        }
        override suspend fun updateProfile(name: String, email: String, phone: String?): Result<UserResponse> =
            Result.success(user)
        override suspend fun logout() {}
        override fun acknowledgeExpiration() {}
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun login_withBlankFields_showsErrorWithoutCallingApi() {
        val auth = FakeAuth()
        val vm = AuthViewModel(auth)
        vm.submit()

        assertNotNull(vm.uiState.value.error)
        assertEquals(0, auth.logins)
    }

    @Test fun login_failure_showsServerMessage() {
        val auth = FakeAuth().apply { loginResult = Result.failure(ApiException("Invalid email or password")) }
        val vm = AuthViewModel(auth)
        vm.onEmailChange("a@x.com"); vm.onPasswordChange("whatever")
        vm.submit()

        assertEquals("Invalid email or password", vm.uiState.value.error)
        assertEquals(false, vm.uiState.value.isLoading)
    }

    @Test fun signUp_requiresLongPassword() {
        val auth = FakeAuth()
        val vm = AuthViewModel(auth)
        vm.switchMode()
        vm.onNameChange("Ana"); vm.onDocIdChange("529.982.247-25"); vm.onEmailChange("a@x.com"); vm.onPasswordChange("short")
        vm.submit()

        assertNotNull(vm.uiState.value.error)
        assertEquals(0, auth.signUps)
    }

    @Test fun signUp_success_logsInAndNormalizesFields() {
        val auth = FakeAuth()
        val vm = AuthViewModel(auth)
        vm.switchMode()
        vm.onNameChange(" Ana "); vm.onDocIdChange("529.982.247-25"); vm.onPhoneChange("(11) 99999-9999")
        vm.onEmailChange("a@x.com"); vm.onPasswordChange("correct horse battery staple")
        vm.submit()

        assertEquals("52998224725", auth.lastSignUp?.docId)
        assertEquals("+5511999999999", auth.lastSignUp?.phone)
        assertEquals("Ana", auth.lastSignUp?.name)
        assertEquals(1, auth.logins)
        assertNull(vm.uiState.value.error)
    }

    @Test fun signUp_failure_doesNotLogin() {
        val auth = FakeAuth().apply { signUpResult = Result.failure(ApiException("email: has already been taken")) }
        val vm = AuthViewModel(auth)
        vm.switchMode()
        vm.onNameChange("Ana"); vm.onDocIdChange("52998224725"); vm.onEmailChange("a@x.com"); vm.onPasswordChange("correct horse battery staple")
        vm.submit()

        assertEquals(0, auth.logins)
        assertEquals("email: has already been taken", vm.uiState.value.error)
    }
}
