package com.example.mobile.ui.pix

import com.example.mobile.model.PixKeyLookupResponse
import com.example.mobile.model.PixKeyResponse
import com.example.mobile.repository.PixKeyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PixKeysViewModelTest {

    private class FakeRepo(initialKeys: List<PixKeyResponse>) : PixKeyRepository {
        private var current = initialKeys
        var reassociatedToAccount: Long? = null

        override suspend fun getKeys(accountId: Long) = Result.success(current)
        override suspend fun createKey(accountId: Long, keyType: String, keyValue: String?) =
            Result.success(PixKeyResponse(99L, keyType, keyValue ?: "random-uuid"))
        override suspend fun deleteKey(id: Long) = Result.success(Unit)
        override suspend fun suspendKey(id: Long) =
            Result.success(PixKeyResponse(id, "email", "bia@rubank.com", status = "suspended"))
        override suspend fun unblockKey(id: Long) =
            Result.success(PixKeyResponse(id, "email", "bia@rubank.com", status = "active"))
        override suspend fun reassociateKey(id: Long, targetAccountId: Long): Result<PixKeyResponse> {
            reassociatedToAccount = targetAccountId
            return Result.success(PixKeyResponse(id, "email", "bia@rubank.com", status = "active"))
        }
        override suspend fun lookupKey(key: String, sourceAccountId: Long): Result<PixKeyLookupResponse> =
            Result.failure(UnsupportedOperationException())
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun suspendAndUnblock_updateKeyStatusInState() {
        val key = PixKeyResponse(1L, "email", "bia@rubank.com", status = "active")
        val vm = PixKeysViewModel(10L, FakeRepo(listOf(key)))

        vm.suspend(key)
        assertEquals("suspended", vm.uiState.value.keys.single().status)

        vm.unblock(vm.uiState.value.keys.single())
        assertEquals("active", vm.uiState.value.keys.single().status)
    }

    @Test
    fun reassociate_movesKeyOutOfCurrentAccountList() {
        val key = PixKeyResponse(1L, "email", "bia@rubank.com", status = "active")
        val repo = FakeRepo(listOf(key))
        val vm = PixKeysViewModel(10L, repo)

        vm.reassociate(key, 20L, "Conta 2020")

        assertEquals(20L, repo.reassociatedToAccount)
        assertTrue(vm.uiState.value.keys.isEmpty())
    }
}
