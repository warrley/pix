package com.example.mobile.network

import com.example.mobile.model.ApiResponse
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

open class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause)
class AccountNotFoundException(message: String = "Conta não encontrada") : ApiException(message)
class UserNotFoundException(message: String = "Usuário não encontrado") : ApiException(message)
class UnauthorizedException(message: String = "Não autenticado") : ApiException(message)
class ServerException(message: String = "Erro interno no servidor") : ApiException(message)
class NetworkException(message: String = "Sem conexão com a internet", cause: Throwable? = null) : ApiException(message, cause)

/**
 * Shared HTTP plumbing for every remote data source: runs the request off the main thread,
 * maps HTTP status codes and transport failures to [ApiException] subtypes and decodes
 * the `{ data, error }` envelope returned by the backend.
 */
internal suspend fun <T> executeApiRequest(
    notFound: () -> ApiException,
    defaultBadRequestMessage: String = "Requisição inválida.",
    defaultUnprocessableMessage: String = "Dados inválidos.",
    request: suspend () -> HttpResponse,
    onSuccess: suspend (HttpResponse) -> Result<T>
): Result<T> = withContext(Dispatchers.IO) {
    try {
        val response = request()
        when (response.status) {
            HttpStatusCode.OK, HttpStatusCode.Created, HttpStatusCode.NoContent -> onSuccess(response)
            HttpStatusCode.NotFound -> Result.failure(notFound())
            HttpStatusCode.Unauthorized ->
                Result.failure(UnauthorizedException(response.errorMessage() ?: "Sessão expirada. Faça login novamente."))
            HttpStatusCode.TooManyRequests ->
                Result.failure(ApiException("Muitas tentativas. Aguarde um instante e tente novamente."))
            HttpStatusCode.BadRequest ->
                Result.failure(ApiException(response.errorMessage() ?: defaultBadRequestMessage))
            HttpStatusCode.UnprocessableEntity ->
                Result.failure(ApiException(response.errorMessage() ?: defaultUnprocessableMessage))
            HttpStatusCode.InternalServerError, HttpStatusCode.BadGateway, HttpStatusCode.ServiceUnavailable ->
                Result.failure(ServerException("Erro no servidor (${response.status.value}). Tente novamente mais tarde."))
            else -> Result.failure(
                ApiException(response.errorMessage() ?: "Erro ${response.status.value}: ${response.status.description}")
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpRequestTimeoutException) {
        Result.failure(NetworkException("Tempo limite esgotado. Servidor demorou para responder.", e))
    } catch (e: SocketTimeoutException) {
        Result.failure(NetworkException("Tempo limite de conexão esgotado.", e))
    } catch (e: ConnectException) {
        Result.failure(NetworkException("Não foi possível conectar ao servidor. Verifique se o backend está ativo.", e))
    } catch (e: UnknownHostException) {
        Result.failure(NetworkException("Servidor inacessível ou sem conexão com a internet.", e))
    } catch (e: IOException) {
        Result.failure(NetworkException("Erro de rede: ${e.message}", e))
    } catch (e: Exception) {
        Result.failure(e)
    }
}

private suspend fun HttpResponse.errorMessage(): String? =
    runCatching { body<ApiResponse<Unit>>().errorMessage }.getOrNull()

/** Unwraps the `data` field of the envelope or fails with the server-provided error. */
internal suspend inline fun <reified T> HttpResponse.unwrap(fallbackError: String): Result<T> {
    val envelope = body<ApiResponse<T>>()
    val data = envelope.data
    return if (data != null) Result.success(data)
    else Result.failure(ApiException(envelope.errorMessage ?: fallbackError))
}

