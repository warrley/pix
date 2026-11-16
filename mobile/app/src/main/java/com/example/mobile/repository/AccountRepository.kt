package com.example.mobile.repository

import com.example.mobile.model.AccountLimitResponse
import com.example.mobile.model.AccountResponse
import com.example.mobile.network.AccountRemoteDataSource
import com.example.mobile.network.UserRemoteDataSource

interface AccountRepository {
    suspend fun getAccounts(userId: Long): Result<List<AccountResponse>>
    suspend fun createAccount(agencyNumber: String = "0001"): Result<AccountResponse>
    suspend fun closeAccount(id: Long): Result<Unit>
    suspend fun getLimit(accountId: Long): Result<AccountLimitResponse>
    suspend fun updateLimit(accountId: Long, dailyLimit: String): Result<AccountLimitResponse>
}

class DefaultAccountRepository(
    private val accountDataSource: AccountRemoteDataSource,
    private val userDataSource: UserRemoteDataSource
) : AccountRepository {
    override suspend fun getAccounts(userId: Long) = userDataSource.getUserAccounts(userId)
    override suspend fun createAccount(agencyNumber: String) = accountDataSource.createAccount(agencyNumber)
    override suspend fun closeAccount(id: Long) = accountDataSource.deleteAccount(id)
    override suspend fun getLimit(accountId: Long) = accountDataSource.getLimit(accountId)
    override suspend fun updateLimit(accountId: Long, dailyLimit: String) =
        accountDataSource.updateLimit(accountId, dailyLimit)
}
