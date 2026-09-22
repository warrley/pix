package com.example.mobile.network

import com.example.mobile.model.AccountCreateBody
import com.example.mobile.model.AccountCreateRequest
import com.example.mobile.model.AccountResponse
import com.example.mobile.model.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
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

open class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause)
class AccountNotFoundException(message: String = "Conta não encontrada") : ApiException(message)
class ServerException(message: String = "Erro interno no servidor") : ApiException(message)
class NetworkException(message: String = "Sem conexão com a internet", cause: Throwable? = null) : ApiException(message, cause)

interface AccountRemoteDataSource {
    suspend fun getAccount(id: Long): Result<AccountResponse>
    suspend fun createAccount(userId: Long, agencyNumber: String = "0001"): Result<AccountResponse>
    suspend fun deleteAccount(id: Long): Result<Unit>
}

class KtorAccountRemoteDataSource(
    private val client: HttpClient = KtorClient.httpClient
) : AccountRemoteDataSource {

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
                    HttpStatusCode.NotFound -> Result.failure(AccountNotFoundException("Conta não encontrada (404)"))
                    HttpStatusCode.BadRequest -> {
                        val errorBody = runCatching { response.body<ApiResponse<Unit>>() }.getOrNull()
                        Result.failure(ApiException(errorBody?.errorMessage ?: "Saldo deve ser zero para encerrar a conta."))
                    }
                    HttpStatusCode.UnprocessableEntity -> {
                        val errorBody = runCatching { response.body<ApiResponse<Unit>>() }.getOrNull()
                        Result.failure(ApiException(errorBody?.errorMessage ?: "Conta já se encontra encerrada."))
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

    override suspend fun getAccount(id: Long): Result<AccountResponse> {
        return executeRequest(
            block = { baseUrl -> client.get("$baseUrl/accounts/$id") },
            onSuccess = { response ->
                val apiResponse = response.body<ApiResponse<AccountResponse>>()
                val account = apiResponse.data
                if (account != null) {
                    Result.success(account)
                } else {
                    Result.failure(ApiException(apiResponse.errorMessage ?: "Resposta inválida do servidor"))
                }
            }
        )
    }

    override suspend fun createAccount(userId: Long, agencyNumber: String): Result<AccountResponse> {
        return executeRequest(
            block = { baseUrl ->
                client.post("$baseUrl/accounts") {
                    contentType(ContentType.Application.Json)
                    setBody(AccountCreateBody(AccountCreateRequest(user_id = userId, agency_number = agencyNumber)))
                }
            },
            onSuccess = { response ->
                val apiResponse = response.body<ApiResponse<AccountResponse>>()
                val account = apiResponse.data
                if (account != null) {
                    Result.success(account)
                } else {
                    Result.failure(ApiException(apiResponse.errorMessage ?: "Erro ao criar conta bancária"))
                }
            }
        )
    }

    override suspend fun deleteAccount(id: Long): Result<Unit> {
        return executeRequest(
            block = { baseUrl -> client.delete("$baseUrl/accounts/$id") },
            onSuccess = { Result.success(Unit) }
        )
    }
}
