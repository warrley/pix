package com.example.mobile.repository

import com.example.mobile.model.AccountResponse
import com.example.mobile.network.AccountRemoteDataSource
import com.example.mobile.network.KtorAccountRemoteDataSource

interface AccountRepository {
    suspend fun getAccount(id: Long): Result<AccountResponse>
}

class DefaultAccountRepository(
    private val remoteDataSource: AccountRemoteDataSource = KtorAccountRemoteDataSource()
) : AccountRepository {

    override suspend fun getAccount(id: Long): Result<AccountResponse> {
        return remoteDataSource.getAccount(id)
    }
}
