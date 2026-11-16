package com.example.mobile.ui.pix

import com.example.mobile.model.PixKeyLookupResponse
import com.example.mobile.model.PixKeyResponse
import com.example.mobile.network.ApiException
import com.example.mobile.repository.PixKeyRepository
import com.example.mobile.util.PixQrCodeCodec
import com.example.mobile.util.PixQrPayload
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
class PixQrScanViewModelTest {

    private class FakePixKeyRepository(
        var lookupResult: Result<PixKeyLookupResponse>
    ) : PixKeyRepository {
        val lookedUpKeys = mutableListOf<String>()
        override suspend fun getKeys(accountId: Long): Result<List<PixKeyResponse>> = Result.success(emptyList())
        override suspend fun createKey(accountId: Long, keyType: String, keyValue: String?): Result<PixKeyResponse> =
            Result.failure(UnsupportedOperationException())
        override suspend fun deleteKey(id: Long): Result<Unit> = Result.success(Unit)
        override suspend fun lookupKey(key: String, sourceAccountId: Long): Result<PixKeyLookupResponse> {
            lookedUpKeys += key
            return lookupResult
        }
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun scanningValidQrWithAmount_looksUpRecipientAndExposesTransferPayload() {
        val recipient = PixKeyLookupResponse(
            keyType = "email",
            keyValue = "bia@rubank.com",
            accountId = 2L,
            accountNumber = "123456-7",
            agencyNumber = "0001",
            recipientName = "Beatriz Souza"
        )
        val repo = FakePixKeyRepository(Result.success(recipient))
        val vm = PixQrScanViewModel(sourceAccountId = 1L, startInPasteMode = false, repository = repo)

        val qrText = PixQrCodeCodec.encode(
            PixQrPayload(
                pixKey = "bia@rubank.com",
                recipientName = "Beatriz Souza",
                amount = "75.00",
                description = "Jantar"
            )
        )

        vm.onCameraQrDetected(qrText)

        val verified = vm.uiState.value.verifiedTransfer
        assertNotNull(verified)
        assertEquals("bia@rubank.com", verified?.pixKey)
        assertEquals("75.00", verified?.amount)
        assertEquals("Beatriz Souza", verified?.recipient?.recipientName)
        assertEquals("Jantar", verified?.description)
    }

    @Test
    fun scanningInvalidQr_setsErrorWithoutCallingLookup() {
        val repo = FakePixKeyRepository(Result.failure(ApiException("not called")))
        val vm = PixQrScanViewModel(sourceAccountId = 1L, startInPasteMode = false, repository = repo)

        vm.onCameraQrDetected("invalid-qr-content")

        assertNull(vm.uiState.value.verifiedTransfer)
        assertNotNull(vm.uiState.value.error)
        assertEquals(0, repo.lookedUpKeys.size)
    }

    @Test
    fun pastingCodeForOwnKey_showsBackendError() {
        val repo = FakePixKeyRepository(
            Result.failure(ApiException("Você não pode transferir para uma chave Pix do próprio usuário."))
        )
        val vm = PixQrScanViewModel(sourceAccountId = 1L, startInPasteMode = true, repository = repo)

        val qrText = PixQrCodeCodec.encode(
            PixQrPayload(pixKey = "eu@rubank.com", recipientName = "Eu", amount = "10.00")
        )
        vm.onPastedCodeChange(qrText)
        vm.submitPastedCode()

        assertNull(vm.uiState.value.verifiedTransfer)
        assertEquals("Você não pode transferir para uma chave Pix do próprio usuário.", vm.uiState.value.error)
    }
}
