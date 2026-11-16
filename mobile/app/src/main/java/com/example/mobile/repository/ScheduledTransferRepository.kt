package com.example.mobile.repository

import com.example.mobile.model.ScheduledTransferCreateRequest
import com.example.mobile.model.ScheduledTransferResponse
import com.example.mobile.network.ScheduledTransferRemoteDataSource

interface ScheduledTransferRepository {
    suspend fun list(accountId: Long): Result<List<ScheduledTransferResponse>>
    suspend fun create(accountId: Long, request: ScheduledTransferCreateRequest): Result<ScheduledTransferResponse>
    suspend fun cancel(id: Long): Result<ScheduledTransferResponse>
}

class DefaultScheduledTransferRepository(
    private val remote: ScheduledTransferRemoteDataSource
) : ScheduledTransferRepository {
    override suspend fun list(accountId: Long) = remote.list(accountId)

    override suspend fun create(accountId: Long, request: ScheduledTransferCreateRequest) =
        remote.create(accountId, request)

    override suspend fun cancel(id: Long) = remote.cancel(id)
}
