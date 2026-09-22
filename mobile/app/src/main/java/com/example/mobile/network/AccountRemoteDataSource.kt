package com.example.mobile.network

import com.example.mobile.model.AccountResponse
import com.example.mobile.model.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
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

    override suspend fun getAccount(id: Long): Result<AccountResponse> = withContext(Dispatchers.IO) {
        try {
            val response = client.get("accounts/$id")
            when (response.status) {
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
        } catch (e: ConnectException) {
            Result.failure(NetworkException("Não foi possível conectar ao servidor. Verifique se o backend está ativo.", e))
        } catch (e: UnknownHostException) {
            Result.failure(NetworkException("Sem conexão com a internet ou servidor inacessível.", e))
        } catch (e: SocketTimeoutException) {
            Result.failure(NetworkException("Tempo limite esgotado ao aguardar resposta do servidor.", e))
        } catch (e: IOException) {
            Result.failure(NetworkException("Erro de rede durante a comunicação.", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
