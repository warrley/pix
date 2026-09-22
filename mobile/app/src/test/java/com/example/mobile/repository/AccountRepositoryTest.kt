package com.example.mobile.repository

import com.example.mobile.network.AccountNotFoundException
import com.example.mobile.network.KtorAccountRemoteDataSource
import com.example.mobile.network.NetworkException
import com.example.mobile.network.ServerException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountRepositoryTest {

    private fun createMockClient(
        content: String,
        status: HttpStatusCode = HttpStatusCode.OK
    ): HttpClient {
        val mockEngine = MockEngine { _ ->
            respond(
                content = content,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        return HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            defaultRequest {
                url("http://10.0.2.2:3000/api/v1/")
            }
        }
    }

    @Test
    fun getAccount_success_returnsParsedAccount() = runTest {
        // Mock valid Rails response
        val jsonResponse = """
            {
                "data": {
                    "id": 1,
                    "user_id": 1,
                    "account_number": "123456",
                    "agency_number": "0001",
                    "balance": "1500.50",
                    "status": "active"
                },
                "error": null
            }
        """.trimIndent()

        val mockClient = createMockClient(jsonResponse, HttpStatusCode.OK)
        val dataSource = KtorAccountRemoteDataSource(mockClient)
        val repository = DefaultAccountRepository(dataSource)

        val result = repository.getAccount(1L)

        assertTrue("Expected success result", result.isSuccess)
        val account = result.getOrNull()
        assertNotNull(account)
        assertEquals(1L, account?.id)
        assertEquals("123456", account?.account_number)
        assertEquals("0001", account?.agency_number)
        assertEquals(1500.50, account?.balance ?: 0.0, 0.001)
        assertEquals("active", account?.status)
    }

    @Test
    fun getAccount_numericBalance_returnsParsedAccount() = runTest {
        // Mock response with numeric balance instead of string
        val jsonResponse = """
            {
                "data": {
                    "id": 2,
                    "account_number": "654321",
                    "agency_number": "0001",
                    "balance": 250.75,
                    "status": "active"
                },
                "error": null
            }
        """.trimIndent()

        val mockClient = createMockClient(jsonResponse, HttpStatusCode.OK)
        val repository = DefaultAccountRepository(KtorAccountRemoteDataSource(mockClient))

        val result = repository.getAccount(2L)

        assertTrue(result.isSuccess)
        assertEquals(250.75, result.getOrNull()?.balance ?: 0.0, 0.001)
    }

    @Test
    fun getAccount_notFound_returnsAccountNotFoundException() = runTest {
        val errorJson = """
            {
                "data": null,
                "error": "Record not found"
            }
        """.trimIndent()

        val mockClient = createMockClient(errorJson, HttpStatusCode.NotFound)
        val repository = DefaultAccountRepository(KtorAccountRemoteDataSource(mockClient))

        val result = repository.getAccount(999L)

        assertTrue("Expected failure result", result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue("Expected AccountNotFoundException", exception is AccountNotFoundException)
        assertTrue(exception?.message?.contains("404") == true)
    }

    @Test
    fun getAccount_serverError_returnsServerException() = runTest {
        val errorJson = """
            {
                "data": null,
                "error": "Internal Server Error"
            }
        """.trimIndent()

        val mockClient = createMockClient(errorJson, HttpStatusCode.InternalServerError)
        val repository = DefaultAccountRepository(KtorAccountRemoteDataSource(mockClient))

        val result = repository.getAccount(1L)

        assertTrue(result.isFailure)
        assertTrue("Expected ServerException", result.exceptionOrNull() is ServerException)
    }

    @Test
    fun getAccount_networkFailure_returnsNetworkException() = runTest {
        val mockEngine = MockEngine {
            throw java.net.ConnectException("Connection refused")
        }
        val mockClient = HttpClient(mockEngine)
        val repository = DefaultAccountRepository(KtorAccountRemoteDataSource(mockClient))

        val result = repository.getAccount(1L)

        assertTrue(result.isFailure)
        assertTrue("Expected NetworkException", result.exceptionOrNull() is NetworkException)
    }
}
