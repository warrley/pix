package com.example.mobile.repository

import com.example.mobile.model.StatementEntry
import com.example.mobile.model.TransferReceipt
import com.example.mobile.model.TransferRequest
import com.example.mobile.network.TransferRemoteDataSource

interface TransferRepository {
    suspend fun transfer(request: TransferRequest, idempotencyKey: String): Result<TransferReceipt>
    suspend fun statement(accountId: Long): Result<List<StatementEntry>>
}

class DefaultTransferRepository(
    private val remote: TransferRemoteDataSource
) : TransferRepository {
    override suspend fun transfer(request: TransferRequest, idempotencyKey: String) =
        remote.transfer(request, idempotencyKey)
    override suspend fun statement(accountId: Long) = remote.statement(accountId)
}
