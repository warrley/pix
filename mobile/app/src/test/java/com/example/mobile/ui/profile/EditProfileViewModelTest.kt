package com.example.mobile.ui.profile

import com.example.mobile.model.SignUpRequest
import com.example.mobile.model.UserResponse
import com.example.mobile.network.ApiException
import com.example.mobile.network.InMemoryProfilePhotoStore
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
class EditProfileViewModelTest {

    private class FakeAuthRepo : AuthRepository {
        val initialUser = UserResponse(
            id = 5L,
            name = "Maria Silva",
            email = "maria@rubank.com",
            phone = "+5511999998888"
        )
        private val _session = MutableStateFlow<Session?>(Session("tok", initialUser))
        override val session: StateFlow<Session?> = _session
        override val sessionExpired: StateFlow<Boolean> = MutableStateFlow(false)

        var lastUpdate: Triple<String, String, String?>? = null
        var updateError: String? = null

        override suspend fun signUp(request: SignUpRequest) = Result.success(initialUser)
        override suspend fun login(email: String, password: String) = Result.success(initialUser)
        override suspend fun updateProfile(name: String, email: String, phone: String?): Result<UserResponse> {
            updateError?.let { return Result.failure(ApiException(it)) }
            lastUpdate = Triple(name, email, phone)
            val updated = initialUser.copy(name = name, email = email, phone = phone)
            _session.value = Session("tok", updated)
            return Result.success(updated)
        }
        override suspend fun logout() {}
        override fun acknowledgeExpiration() {}
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun initializesFromCurrentSessionAndValidatesInputs() {
        val auth = FakeAuthRepo()
        val photoStore = InMemoryProfilePhotoStore()
        val vm = EditProfileViewModel(auth, photoStore)

        assertEquals("Maria Silva", vm.uiState.value.name)
        assertEquals("maria@rubank.com", vm.uiState.value.email)

        vm.onNameChange("   ")
        vm.save()
        assertNotNull(vm.uiState.value.error)
        assertNull(auth.lastUpdate)
    }

    @Test
    fun save_normalizesPhoneAndUpdatesSession() {
        val auth = FakeAuthRepo()
        val photoStore = InMemoryProfilePhotoStore()
        val vm = EditProfileViewModel(auth, photoStore)

        vm.onNameChange("  Maria Souza  ")
        vm.onEmailChange(" MARIA.SOUZA@RUBANK.COM ")
        vm.onPhoneChange("(11) 98888-7777")
        vm.save()

        assertEquals(Triple("Maria Souza", "maria.souza@rubank.com", "+5511988887777"), auth.lastUpdate)
        assertEquals("Maria Souza", auth.session.value?.user?.name)
        assertNotNull(vm.uiState.value.message)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun photoPickAndRemove_updatesPhotoState() {
        val auth = FakeAuthRepo()
        val photoStore = InMemoryProfilePhotoStore()
        val vm = EditProfileViewModel(auth, photoStore, ioDispatcher = UnconfinedTestDispatcher())

        vm.onPhotoPicked("content://media/external/images/1")
        assertNotNull(vm.uiState.value.photoPath)

        vm.removePhoto()
        assertNull(vm.uiState.value.photoPath)
        assertNull(photoStore.getPhotoPath(5L))
    }
}
