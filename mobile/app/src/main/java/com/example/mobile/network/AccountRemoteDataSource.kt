package com.example.mobile.network

import com.example.mobile.model.AccountCreateBody
import com.example.mobile.model.AccountCreateRequest
import com.example.mobile.model.AccountResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse

interface AccountRemoteDataSource {
    suspend fun getAccount(id: Long): Result<AccountResponse>
    suspend fun createAccount(agencyNumber: String = "0001"): Result<AccountResponse>
    suspend fun deleteAccount(id: Long): Result<Unit>
}

class KtorAccountRemoteDataSource(
    private val client: HttpClient
) : AccountRemoteDataSource {

    private suspend fun <T> execute(
        request: suspend () -> HttpResponse,
        onSuccess: suspend (HttpResponse) -> Result<T>
    ): Result<T> = executeApiRequest(
        notFound = { AccountNotFoundException("Conta não encontrada (404)") },
        defaultBadRequestMessage = "Saldo deve ser zero para encerrar a conta.",
        defaultUnprocessableMessage = "Conta já se encontra encerrada.",
        request = request,
        onSuccess = onSuccess
    )

    override suspend fun getAccount(id: Long): Result<AccountResponse> = execute(
        request = { client.get("accounts/$id") },
        onSuccess = { it.unwrap("Resposta inválida do servidor") }
    )

    override suspend fun createAccount(agencyNumber: String): Result<AccountResponse> = execute(
        request = {
            client.post("accounts") {
                setBody(AccountCreateBody(AccountCreateRequest(agencyNumber)))
            }
        },
        onSuccess = { it.unwrap("Erro ao criar conta bancária") }
    )

    override suspend fun deleteAccount(id: Long): Result<Unit> = execute(
        request = { client.delete("accounts/$id") },
        onSuccess = { Result.success(Unit) }
    )
}
