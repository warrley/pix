package com.example.mobile.repository

import com.example.mobile.model.StatementEntry
import com.example.mobile.model.TransferReceipt
import com.example.mobile.model.TransferRequest
import com.example.mobile.network.TransferRemoteDataSource

interface TransferRepository {
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

class DefaultTransferRepository(
    private val remote: TransferRemoteDataSource
) : TransferRepository {
    override suspend fun transfer(request: TransferRequest, idempotencyKey: String) =
        remote.transfer(request, idempotencyKey)

    override suspend fun statement(
        accountId: Long,
        startDate: String?,
        endDate: String?,
        status: String?,
        page: Int,
        perPage: Int
    ) = remote.statement(accountId, startDate, endDate, status, page, perPage)

    override suspend fun refund(transactionId: Long, reason: String) =
        remote.refund(transactionId, reason)
}
