package com.example.mobile.ui.pix

import com.example.mobile.model.PixKeyLookupResponse
import com.example.mobile.model.PixKeyResponse
import com.example.mobile.repository.PixKeyRepository
import com.example.mobile.util.PixQrCodeCodec
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
class PixChargeViewModelTest {

    private class FakePixKeyRepository(
        var keys: List<PixKeyResponse> = emptyList(),
        var createdKey: PixKeyResponse = PixKeyResponse(99L, "random", "uuid-key-99")
    ) : PixKeyRepository {
        override suspend fun getKeys(accountId: Long) = Result.success(keys)
        override suspend fun createKey(accountId: Long, keyType: String, keyValue: String?) =
            Result.success(createdKey)
        override suspend fun deleteKey(id: Long) = Result.success(Unit)
        override suspend fun suspendKey(id: Long): Result<PixKeyResponse> =
            Result.failure(UnsupportedOperationException())
        override suspend fun unblockKey(id: Long): Result<PixKeyResponse> =
            Result.failure(UnsupportedOperationException())
        override suspend fun reassociateKey(id: Long, targetAccountId: Long): Result<PixKeyResponse> =
            Result.failure(UnsupportedOperationException())
        override suspend fun lookupKey(key: String, sourceAccountId: Long): Result<PixKeyLookupResponse> =
            Result.failure(UnsupportedOperationException())
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun defaultAmountIsZero_andGeneratesOpenValueQrCode() {
        val repo = FakePixKeyRepository(
            keys = listOf(PixKeyResponse(1L, "email", "bia@rubank.com"))
        )
        val vm = PixChargeViewModel(accountId = 10L, userName = "Bia", repository = repo)

        val state = vm.uiState.value
        assertEquals("0", state.amountInput)
        assertNull(state.normalizedAmount)
        assertEquals("R$ 0,00", state.formattedAmount)
        assertNotNull(state.qrCodeText)

        val decoded = PixQrCodeCodec.decode(state.qrCodeText!!)
        assertEquals("bia@rubank.com", decoded?.pixKey)
        assertNull(decoded?.amount)
    }

    @Test
    fun typingAmountReplacesDefaultZero_andUpdatesQrCode() {
        val repo = FakePixKeyRepository(
            keys = listOf(PixKeyResponse(1L, "email", "bia@rubank.com"))
        )
        val vm = PixChargeViewModel(accountId = 10L, userName = "Bia", repository = repo)

        vm.onAmountChange("05")
        assertEquals("5", vm.uiState.value.amountInput)
        vm.onAmountChange("52,75")
        assertEquals("52,75", vm.uiState.value.amountInput)
        assertEquals("52.75", vm.uiState.value.normalizedAmount)

        val decoded = PixQrCodeCodec.decode(vm.uiState.value.qrCodeText!!)
        assertEquals("52.75", decoded?.amount)
    }

    @Test
    fun createRandomKey_addsAndSelectsKeyWhenNoneExist() {
        val repo = FakePixKeyRepository(keys = emptyList())
        val vm = PixChargeViewModel(accountId = 10L, userName = "Bia", repository = repo)

        assertNull(vm.uiState.value.qrCodeText)
        vm.createRandomKey()

        assertEquals("uuid-key-99", vm.uiState.value.selectedKey?.keyValue)
        assertNotNull(vm.uiState.value.qrCodeText)
    }
}
