package com.example.mobile.network

import com.example.mobile.model.ApiResponse
import com.example.mobile.model.PixKeyCreateBody
import com.example.mobile.model.PixKeyCreateRequest
import com.example.mobile.model.PixKeyLookupResponse
import com.example.mobile.model.PixKeyResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody

interface PixKeyRemoteDataSource {
    suspend fun list(accountId: Long): Result<List<PixKeyResponse>>
    suspend fun create(accountId: Long, keyType: String, keyValue: String?): Result<PixKeyResponse>
    suspend fun delete(id: Long): Result<Unit>
    suspend fun lookup(key: String, sourceAccountId: Long): Result<PixKeyLookupResponse>
}

class KtorPixKeyRemoteDataSource(
    private val client: HttpClient
) : PixKeyRemoteDataSource {

    override suspend fun list(accountId: Long): Result<List<PixKeyResponse>> = executeApiRequest(
        notFound = { ApiException("Conta não encontrada") },
        request = { client.get("accounts/$accountId/pix_keys") },
        onSuccess = { Result.success(it.body<ApiResponse<List<PixKeyResponse>>>().data ?: emptyList()) }
    )

    override suspend fun create(accountId: Long, keyType: String, keyValue: String?): Result<PixKeyResponse> =
        executeApiRequest(
            notFound = { ApiException("Conta não encontrada") },
            defaultUnprocessableMessage = "Chave Pix inválida.",
            request = {
                client.post("accounts/$accountId/pix_keys") {
                    setBody(PixKeyCreateBody(PixKeyCreateRequest(keyType, keyValue?.takeIf { it.isNotBlank() })))
                }
            },
            onSuccess = { it.unwrap("Erro ao cadastrar chave Pix") }
        )

    override suspend fun delete(id: Long): Result<Unit> = executeApiRequest(
        notFound = { ApiException("Chave Pix não encontrada") },
        request = { client.delete("pix_keys/$id") },
        onSuccess = { Result.success(Unit) }
    )

    override suspend fun lookup(key: String, sourceAccountId: Long): Result<PixKeyLookupResponse> =
        executeApiRequest(
            notFound = { ApiException("Chave Pix não encontrada. Verifique a chave digitada.") },
            defaultUnprocessableMessage = "Não é permitido transferir para uma chave Pix do próprio usuário.",
            request = {
                client.get("pix_keys/lookup") {
                    parameter("key", key)
                    parameter("source_account_id", sourceAccountId)
                }
            },
            onSuccess = { it.unwrap("Chave Pix inválida") }
        )
}
