package com.example.mobile.network

import com.example.mobile.model.AccountResponse
import com.example.mobile.model.ApiResponse
import com.example.mobile.model.UserResponse
import com.example.mobile.model.UserUpdateBody
import com.example.mobile.model.UserUpdateRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse

interface UserRemoteDataSource {
    suspend fun getUser(id: Long): Result<UserResponse>
    suspend fun updateUser(id: Long, request: UserUpdateRequest): Result<UserResponse>
    suspend fun getUserAccounts(userId: Long): Result<List<AccountResponse>>
}

class KtorUserRemoteDataSource(
    private val client: HttpClient
) : UserRemoteDataSource {

    private suspend fun <T> execute(
        request: suspend () -> HttpResponse,
        onSuccess: suspend (HttpResponse) -> Result<T>
    ): Result<T> = executeApiRequest(
        notFound = { UserNotFoundException("Usuário não encontrado (404)") },
        request = request,
        onSuccess = onSuccess
    )

    override suspend fun getUser(id: Long): Result<UserResponse> = execute(
        request = { client.get("users/$id") },
        onSuccess = { it.unwrap("Resposta inválida do servidor") }
    )

    override suspend fun updateUser(id: Long, request: UserUpdateRequest): Result<UserResponse> =
        executeApiRequest(
            notFound = { UserNotFoundException("Usuário não encontrado (404)") },
            defaultUnprocessableMessage = "Dados do perfil inválidos.",
            request = {
                client.patch("users/$id") {
                    setBody(UserUpdateBody(request))
                }
            },
            onSuccess = { it.unwrap("Erro ao atualizar perfil") }
        )

    override suspend fun getUserAccounts(userId: Long): Result<List<AccountResponse>> = execute(
        request = { client.get("users/$userId/accounts") },
        onSuccess = { Result.success(it.body<ApiResponse<List<AccountResponse>>>().data ?: emptyList()) }
    )
}
