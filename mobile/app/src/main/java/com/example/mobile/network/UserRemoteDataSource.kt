package com.example.mobile.network

import com.example.mobile.model.AccountResponse
import com.example.mobile.model.ApiResponse
import com.example.mobile.model.UserCreateBody
import com.example.mobile.model.UserCreateRequest
import com.example.mobile.model.UserResponse
import com.example.mobile.model.UserUpdateBody
import com.example.mobile.model.UserUpdateRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class UserNotFoundException(message: String = "Usuário não encontrado") : ApiException(message)

interface UserRemoteDataSource {
    suspend fun getUser(id: Long): Result<UserResponse>
    suspend fun createUser(request: UserCreateRequest): Result<UserResponse>
    suspend fun updateUser(id: Long, request: UserUpdateRequest): Result<UserResponse>
    suspend fun deleteUser(id: Long): Result<Unit>
    suspend fun getUserAccounts(userId: Long): Result<List<AccountResponse>>
}

class KtorUserRemoteDataSource(
    private val client: HttpClient = KtorClient.httpClient
) : UserRemoteDataSource {

    private suspend fun <T> executeRequest(
        block: suspend (baseUrl: String) -> HttpResponse,
        onSuccess: suspend (HttpResponse) -> Result<T>
    ): Result<T> = withContext(Dispatchers.IO) {
        val baseUrlsToTry = NetworkEndpointResolver.resolvedBaseUrl?.let { listOf(it) }
            ?: NetworkEndpointResolver.candidateBaseUrls
        var lastException: Throwable? = null

        for (base in baseUrlsToTry) {
            try {
                val response = block(base.trimEnd('/'))
                NetworkEndpointResolver.resolvedBaseUrl = base

                return@withContext when (response.status) {
                    HttpStatusCode.OK, HttpStatusCode.Created, HttpStatusCode.NoContent -> onSuccess(response)
                    HttpStatusCode.NotFound -> Result.failure(UserNotFoundException("Usuário não encontrado (404)"))
                    HttpStatusCode.UnprocessableEntity -> {
                        val errorBody = runCatching { response.body<ApiResponse<Unit>>() }.getOrNull()
                        Result.failure(ApiException(errorBody?.errorMessage ?: "Dados inválidos para o usuário."))
                    }
                    HttpStatusCode.BadRequest -> {
                        val errorBody = runCatching { response.body<ApiResponse<Unit>>() }.getOrNull()
                        Result.failure(ApiException(errorBody?.errorMessage ?: "Requisição inválida."))
                    }
                    HttpStatusCode.InternalServerError, HttpStatusCode.BadGateway, HttpStatusCode.ServiceUnavailable -> {
                        Result.failure(ServerException("Erro no servidor (${response.status.value}). Tente novamente mais tarde."))
                    }
                    else -> Result.failure(ApiException("Erro ${response.status.value}: ${response.status.description}"))
                }
            } catch (e: Exception) {
                lastException = e
            }
        }

        when (val e = lastException) {
            is HttpRequestTimeoutException -> Result.failure(NetworkException("Tempo limite esgotado (5s). Servidor demorou para responder.", e))
            is SocketTimeoutException -> Result.failure(NetworkException("Tempo limite de conexão esgotado (5s).", e))
            is ConnectException -> Result.failure(NetworkException("Não foi possível conectar ao servidor. Verifique se o backend está ativo.", e))
            is UnknownHostException -> Result.failure(NetworkException("Servidor inacessível ou sem conexão com a internet.", e))
            is IOException -> Result.failure(NetworkException("Erro de rede: ${e.message}", e))
            null -> Result.failure(NetworkException("Nenhum servidor disponível."))
            else -> Result.failure(e)
        }
    }

    override suspend fun getUser(id: Long): Result<UserResponse> {
        return executeRequest(
            block = { baseUrl -> client.get("$baseUrl/users/$id") },
            onSuccess = { response ->
                val apiResponse = response.body<ApiResponse<UserResponse>>()
                val user = apiResponse.data
                if (user != null) {
                    Result.success(user)
                } else {
                    Result.failure(ApiException(apiResponse.errorMessage ?: "Resposta inválida do servidor"))
                }
            }
        )
    }

    override suspend fun createUser(request: UserCreateRequest): Result<UserResponse> {
        return executeRequest(
            block = { baseUrl ->
                client.post("$baseUrl/users") {
                    contentType(ContentType.Application.Json)
                    setBody(UserCreateBody(request))
                }
            },
            onSuccess = { response ->
                val apiResponse = response.body<ApiResponse<UserResponse>>()
                val user = apiResponse.data
                if (user != null) {
                    Result.success(user)
                } else {
                    Result.failure(ApiException(apiResponse.errorMessage ?: "Erro ao criar usuário"))
                }
            }
        )
    }

    override suspend fun updateUser(id: Long, request: UserUpdateRequest): Result<UserResponse> {
        return executeRequest(
            block = { baseUrl ->
                client.put("$baseUrl/users/$id") {
                    contentType(ContentType.Application.Json)
                    setBody(UserUpdateBody(request))
                }
            },
            onSuccess = { response ->
                val apiResponse = response.body<ApiResponse<UserResponse>>()
                val user = apiResponse.data
                if (user != null) {
                    Result.success(user)
                } else {
                    Result.failure(ApiException(apiResponse.errorMessage ?: "Erro ao atualizar dados do usuário"))
                }
            }
        )
    }

    override suspend fun deleteUser(id: Long): Result<Unit> {
        return executeRequest(
            block = { baseUrl -> client.delete("$baseUrl/users/$id") },
            onSuccess = { Result.success(Unit) }
        )
    }

    override suspend fun getUserAccounts(userId: Long): Result<List<AccountResponse>> {
        return executeRequest(
            block = { baseUrl -> client.get("$baseUrl/users/$userId/accounts") },
            onSuccess = { response ->
                val apiResponse = response.body<ApiResponse<List<AccountResponse>>>()
                Result.success(apiResponse.data ?: emptyList())
            }
        )
    }
}
