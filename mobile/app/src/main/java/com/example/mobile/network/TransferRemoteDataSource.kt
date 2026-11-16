package com.example.mobile.network

import com.example.mobile.model.RefundRequest
import com.example.mobile.model.StatementEntry
import com.example.mobile.model.StatementResponse
import com.example.mobile.model.TransferBody
import com.example.mobile.model.TransferReceipt
import com.example.mobile.model.TransferRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody

interface TransferRemoteDataSource {
    /** [idempotencyKey] makes retries of the same user intent safe on the backend. */
    suspend fun transfer(request: TransferRequest, idempotencyKey: String): Result<TransferReceipt>
    suspend fun statement(
        accountId: Long,
        startDate: String? = null,
        endDate: String? = null,
        status: String? = null,
        page: Int = 1,
        perPage: Int = 50
    ): Result<List<StatementEntry>>
    suspend fun refund(transactionId: Long, reason: String): Result<TransferReceipt>
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

    override suspend fun statement(
        accountId: Long,
        startDate: String?,
        endDate: String?,
        status: String?,
        page: Int,
        perPage: Int
    ): Result<List<StatementEntry>> = executeApiRequest(
        notFound = { AccountNotFoundException() },
        request = {
            client.get("accounts/$accountId/transfers") {
                parameter("page", page)
                parameter("per_page", perPage)
                startDate?.takeIf { it.isNotBlank() }?.let { parameter("start_date", it) }
                endDate?.takeIf { it.isNotBlank() }?.let { parameter("end_date", it) }
                status?.takeIf { it.isNotBlank() }?.let { parameter("status", it) }
            }
        },
        onSuccess = { Result.success(it.body<StatementResponse>().transactions) }
    )

    override suspend fun refund(transactionId: Long, reason: String): Result<TransferReceipt> =
        executeApiRequest(
            notFound = { ApiException("Transação não encontrada.") },
            defaultUnprocessableMessage = "Não foi possível estornar esta transação.",
            request = {
                client.post("transfers/$transactionId/refund") {
                    setBody(RefundRequest(reason))
                }
            },
            onSuccess = { it.unwrap("Erro ao estornar transação") }
        )
}
