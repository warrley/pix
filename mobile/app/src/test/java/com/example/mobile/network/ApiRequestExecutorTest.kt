package com.example.mobile.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiRequestExecutorTest {

    private fun client(body: String, status: HttpStatusCode, onRequest: (io.ktor.client.request.HttpRequestData) -> Unit = {}) =
        HttpClient(MockEngine { request ->
            onRequest(request)
            respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            defaultRequest {
                url("http://localhost/api/v1/")
                contentType(ContentType.Application.Json)
            }
        }

    @Test fun getAccounts_parsesDecimalBalanceAsString() = runTest {
        val json = """{"data":[{"id":1,"user_id":1,"account_number":"123456","agency_number":"0001","balance":"1500.50","status":"active"}],"error":null}"""
        val result = KtorUserRemoteDataSource(client(json, HttpStatusCode.OK)).getUserAccounts(1)

        val account = result.getOrThrow().single()
        assertEquals("1500.50", account.balance)
        assertEquals("1500.50".toBigDecimal(), account.balanceAmount)
    }

    @Test fun login_parsesTokenAndUser() = runTest {
        val json = """{"data":{"access_token":"tok","expires_at":"2030-01-01T00:00:00Z","user":{"id":7,"name":"Ana","email":"a@x.com"}},"error":null}"""
        val token = KtorAuthRemoteDataSource(client(json, HttpStatusCode.Created)).login("a@x.com", "pw").getOrThrow()

        assertEquals("tok", token.accessToken)
        assertEquals(7L, token.user.id)
    }

    @Test fun login_invalidCredentials_usesServerMessage() = runTest {
        val json = """{"data":null,"error":{"code":"invalid_credentials","message":"Invalid email or password"}}"""
        val result = KtorAuthRemoteDataSource(client(json, HttpStatusCode.Unauthorized)).login("a@x.com", "bad")

        assertTrue(result.exceptionOrNull() is UnauthorizedException)
        assertEquals("Invalid email or password", result.exceptionOrNull()?.message)
    }

    @Test fun signUp_validationErrors_listFields() = runTest {
        val json = """{"detail":"One or more fields are invalid","errors":{"email":["has already been taken"]},"data":null,"error":"One or more fields are invalid"}"""
        val result = KtorAuthRemoteDataSource(client(json, HttpStatusCode.UnprocessableEntity))
            .signUp(com.example.mobile.model.SignUpRequest("A", "a@x.com", "52998224725", "", "correct horse battery"))

        assertEquals("email: has already been taken", result.exceptionOrNull()?.message)
    }

    @Test fun transfer_sendsIdempotencyKeyAndParsesReceipt() = runTest {
        var key: String? = null
        val json = """{"data":{"id":9,"amount":"10.50","status":"completed","sender":{"account_number":"1"},"receiver":{"account_number":"2","user_name":"Bia"}},"error":null}"""
        val source = KtorTransferRemoteDataSource(client(json, HttpStatusCode.Created) { key = it.headers["X-Idempotency-Key"] })

        val receipt = source.transfer(
            com.example.mobile.model.TransferRequest(1, "bia@x.com", "10.50"), "abc123"
        ).getOrThrow()

        assertEquals("abc123", key)
        assertEquals("Bia", receipt.receiver.userName)
    }

    @Test fun accountClose_conflict_returnsServerMessage() = runTest {
        val json = """{"detail":"Cannot close account with existing balance","data":null,"error":"Cannot close account with existing balance"}"""
        val result = KtorAccountRemoteDataSource(client(json, HttpStatusCode.Conflict)).deleteAccount(1)

        assertEquals("Cannot close account with existing balance", result.exceptionOrNull()?.message)
    }

    @Test fun connectionFailure_becomesNetworkException() = runTest {
        val failing = HttpClient(MockEngine { throw java.net.ConnectException("refused") })
        val result = KtorAccountRemoteDataSource(failing).getAccount(1)

        assertTrue(result.exceptionOrNull() is NetworkException)
    }

    @Test fun serverError_becomesServerException() = runTest {
        val result = KtorAccountRemoteDataSource(client("{}", HttpStatusCode.InternalServerError)).getAccount(1)

        assertTrue(result.exceptionOrNull() is ServerException)
    }
}
