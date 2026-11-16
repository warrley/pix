package com.example.mobile.network

import com.example.mobile.model.StatementEntry
import com.example.mobile.model.StatementResponse
import com.example.mobile.model.TransferBody
import com.example.mobile.model.TransferReceipt
import com.example.mobile.model.TransferRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody

interface TransferRemoteDataSource {
    /** [idempotencyKey] makes retries of the same user intent safe on the backend. */
    suspend fun transfer(request: TransferRequest, idempotencyKey: String): Result<TransferReceipt>
    suspend fun statement(accountId: Long): Result<List<StatementEntry>>
}

class KtorTransferRemoteDataSource(
    private val client: HttpClient
) : TransferRemoteDataSource {

    override suspend fun transfer(request: TransferRequest, idempotencyKey: String): Result<TransferReceipt> =
        executeApiRequest(
            notFound = { ApiException("Chave Pix ou conta de destino não encontrada") },
            defaultUnprocessableMessage = "Não foi possível concluir a transferência.",
            request = {
                client.post("transfers") {
                    header("X-Idempotency-Key", idempotencyKey)
                    setBody(TransferBody(request))
                }
            },
            onSuccess = { it.unwrap("Erro ao realizar transferência") }
        )

    override suspend fun statement(accountId: Long): Result<List<StatementEntry>> = executeApiRequest(
        notFound = { AccountNotFoundException() },
        request = { client.get("accounts/$accountId/transfers") },
        onSuccess = { Result.success(it.body<StatementResponse>().transactions) }
    )
}
