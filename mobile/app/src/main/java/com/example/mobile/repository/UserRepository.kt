package com.example.mobile.repository

import com.example.mobile.model.AccountResponse
import com.example.mobile.model.UserCreateRequest
import com.example.mobile.model.UserResponse
import com.example.mobile.model.UserUpdateRequest
import com.example.mobile.network.KtorUserRemoteDataSource
import com.example.mobile.network.UserRemoteDataSource

interface UserRepository {
    suspend fun getUser(id: Long): Result<UserResponse>
    suspend fun createUser(request: UserCreateRequest): Result<UserResponse>
    suspend fun updateUser(id: Long, request: UserUpdateRequest): Result<UserResponse>
    suspend fun deleteUser(id: Long): Result<Unit>
    suspend fun getUserAccounts(userId: Long): Result<List<AccountResponse>>
}

class DefaultUserRepository(
    private val remoteDataSource: UserRemoteDataSource = KtorUserRemoteDataSource()
) : UserRepository {

    override suspend fun getUser(id: Long): Result<UserResponse> {
        return remoteDataSource.getUser(id)
    }

    override suspend fun createUser(request: UserCreateRequest): Result<UserResponse> {
        return remoteDataSource.createUser(request)
    }

    override suspend fun updateUser(id: Long, request: UserUpdateRequest): Result<UserResponse> {
        return remoteDataSource.updateUser(id, request)
    }

    override suspend fun deleteUser(id: Long): Result<Unit> {
        return remoteDataSource.deleteUser(id)
    }

    override suspend fun getUserAccounts(userId: Long): Result<List<AccountResponse>> {
        return remoteDataSource.getUserAccounts(userId)
    }
}
