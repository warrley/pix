package com.example.mobile.repository

import com.example.mobile.model.PixKeyLookupResponse
import com.example.mobile.model.PixKeyResponse
import com.example.mobile.network.PixKeyRemoteDataSource

interface PixKeyRepository {
    suspend fun getKeys(accountId: Long): Result<List<PixKeyResponse>>
    suspend fun createKey(accountId: Long, keyType: String, keyValue: String?): Result<PixKeyResponse>
    suspend fun deleteKey(id: Long): Result<Unit>
    suspend fun suspendKey(id: Long): Result<PixKeyResponse>
    suspend fun unblockKey(id: Long): Result<PixKeyResponse>
    suspend fun reassociateKey(id: Long, targetAccountId: Long): Result<PixKeyResponse>
    suspend fun lookupKey(key: String, sourceAccountId: Long): Result<PixKeyLookupResponse>
}

class DefaultPixKeyRepository(
    private val remote: PixKeyRemoteDataSource
) : PixKeyRepository {
    override suspend fun getKeys(accountId: Long) = remote.list(accountId)
    override suspend fun createKey(accountId: Long, keyType: String, keyValue: String?) =
        remote.create(accountId, keyType, keyValue)
    override suspend fun deleteKey(id: Long) = remote.delete(id)
    override suspend fun suspendKey(id: Long) = remote.suspendKey(id)
    override suspend fun unblockKey(id: Long) = remote.unblock(id)
    override suspend fun reassociateKey(id: Long, targetAccountId: Long) =
        remote.reassociate(id, targetAccountId)
    override suspend fun lookupKey(key: String, sourceAccountId: Long) =
        remote.lookup(key, sourceAccountId)
}
