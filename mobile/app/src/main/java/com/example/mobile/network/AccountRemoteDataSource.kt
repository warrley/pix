package com.example.mobile.network

import com.example.mobile.BuildConfig
import com.example.mobile.model.AccountResponse
import com.example.mobile.model.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
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
}

class KtorAccountRemoteDataSource(
    private val client: HttpClient = KtorClient.httpClient
) : AccountRemoteDataSource {

    companion object {
        @Volatile
        var resolvedBaseUrl: String? = null

        val candidateBaseUrls: List<String> = listOf(
            BuildConfig.API_BASE_URL,
            "http://localhost:3000/api/v1/",
            "http://10.0.2.2:3000/api/v1/",
            "http://192.168.0.8:3000/api/v1/",
            "http://10.0.2.2:80/api/v1/"
        ).distinct()
    }

    override suspend fun getAccount(id: Long): Result<AccountResponse> = withContext(Dispatchers.IO) {
        val baseUrlsToTry = resolvedBaseUrl?.let { listOf(it) } ?: candidateBaseUrls
        var lastException: Throwable? = null

        for (base in baseUrlsToTry) {
            val fullUrl = "${base.trimEnd('/')}/accounts/$id"
            try {
                val response: HttpResponse = client.get(fullUrl)

                // Connected to a real Rails server! Lock in this base URL for future calls
                resolvedBaseUrl = base

                return@withContext when (response.status) {
                    HttpStatusCode.OK -> {
                        val apiResponse = response.body<ApiResponse<AccountResponse>>()
                        val account = apiResponse.data
                        if (account != null) {
                            Result.success(account)
                        } else {
                            Result.failure(ApiException(apiResponse.error ?: "Resposta inválida do servidor"))
                        }
                    }
                    HttpStatusCode.NotFound -> {
                        Result.failure(AccountNotFoundException("Conta não encontrada (404)"))
                    }
                    HttpStatusCode.InternalServerError, HttpStatusCode.BadGateway, HttpStatusCode.ServiceUnavailable -> {
                        Result.failure(ServerException("Erro no servidor (${response.status.value}). Tente novamente mais tarde."))
                    }
                    else -> {
                        Result.failure(ApiException("Erro ${response.status.value}: ${response.status.description}"))
                    }
                }
            } catch (e: Exception) {
                lastException = e
            }
        }

        // All candidates failed
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
}
