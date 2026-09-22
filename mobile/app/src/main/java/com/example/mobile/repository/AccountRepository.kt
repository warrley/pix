package com.example.mobile.repository

import com.example.mobile.model.AccountResponse
import com.example.mobile.network.AccountRemoteDataSource
import com.example.mobile.network.KtorAccountRemoteDataSource

interface AccountRepository {
    suspend fun getAccount(id: Long): Result<AccountResponse>
    suspend fun createAccount(userId: Long, agencyNumber: String = "0001"): Result<AccountResponse>
    suspend fun deleteAccount(id: Long): Result<Unit>
}

class DefaultAccountRepository(
    private val remoteDataSource: AccountRemoteDataSource = KtorAccountRemoteDataSource()
) : AccountRepository {

    override suspend fun getAccount(id: Long): Result<AccountResponse> {
        return remoteDataSource.getAccount(id)
    }

    override suspend fun createAccount(userId: Long, agencyNumber: String): Result<AccountResponse> {
        return remoteDataSource.createAccount(userId, agencyNumber)
    }

    override suspend fun deleteAccount(id: Long): Result<Unit> {
        return remoteDataSource.deleteAccount(id)
    }
}
