package com.example.mobile.network

import com.example.mobile.model.ApiResponse
import com.example.mobile.model.ScheduledTransferCreateBody
import com.example.mobile.model.ScheduledTransferCreateRequest
import com.example.mobile.model.ScheduledTransferResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody

interface ScheduledTransferRemoteDataSource {
    suspend fun list(accountId: Long): Result<List<ScheduledTransferResponse>>
    suspend fun create(accountId: Long, request: ScheduledTransferCreateRequest): Result<ScheduledTransferResponse>
    suspend fun cancel(id: Long): Result<ScheduledTransferResponse>
}

class KtorScheduledTransferRemoteDataSource(
    private val client: HttpClient
) : ScheduledTransferRemoteDataSource {

    override suspend fun list(accountId: Long): Result<List<ScheduledTransferResponse>> = executeApiRequest(
        notFound = { AccountNotFoundException() },
        request = { client.get("accounts/$accountId/scheduled_transfers") },
        onSuccess = {
            Result.success(it.body<ApiResponse<List<ScheduledTransferResponse>>>().data ?: emptyList())
        }
    )

    override suspend fun create(
        accountId: Long,
        request: ScheduledTransferCreateRequest
    ): Result<ScheduledTransferResponse> = executeApiRequest(
        notFound = { AccountNotFoundException() },
        defaultUnprocessableMessage = "Não foi possível agendar a transferência Pix.",
        request = {
            client.post("accounts/$accountId/scheduled_transfers") {
                setBody(ScheduledTransferCreateBody(request))
            }
        },
        onSuccess = { it.unwrap("Erro ao criar agendamento Pix") }
    )

    override suspend fun cancel(id: Long): Result<ScheduledTransferResponse> = executeApiRequest(
        notFound = { ApiException("Agendamento Pix não encontrado.") },
        defaultUnprocessableMessage = "Este agendamento não pode mais ser cancelado.",
        request = { client.delete("scheduled_transfers/$id") },
        onSuccess = { it.unwrap("Erro ao cancelar agendamento Pix") }
    )
}
