package com.example.mobile.repository

import com.example.mobile.model.UserCreateRequest
import com.example.mobile.model.UserUpdateRequest
import com.example.mobile.network.ApiException
import com.example.mobile.network.KtorUserRemoteDataSource
import com.example.mobile.network.UserNotFoundException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
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
import org.junit.Assert.assertTrue
import org.junit.Test

class UserRepositoryTest {

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
    fun getUser_success_returnsParsedUser() = runTest {
        val jsonResponse = """
            {
                "data": {
                    "id": 1,
                    "name": "Warley Silva",
                    "email": "warley@example.com",
                    "doc_id": "52998224725",
                    "phone": "+5585999999999"
                },
                "error": null
            }
        """.trimIndent()

        val mockClient = createMockClient(jsonResponse, HttpStatusCode.OK)
        val repository = DefaultUserRepository(KtorUserRemoteDataSource(mockClient))

        val result = repository.getUser(1L)
        assertTrue(result.isSuccess)
        val user = result.getOrThrow()
        assertEquals(1L, user.id)
        assertEquals("Warley Silva", user.name)
        assertEquals("52998224725", user.doc_id)
    }

    @Test
    fun createUser_success_returnsCreatedUser() = runTest {
        val jsonResponse = """
            {
                "data": {
                    "id": 2,
                    "name": "Maria Santos",
                    "email": "maria@example.com",
                    "doc_id": "12345678901",
                    "phone": "+5585988888888"
                },
                "error": null
            }
        """.trimIndent()

        val mockClient = createMockClient(jsonResponse, HttpStatusCode.Created)
        val repository = DefaultUserRepository(KtorUserRemoteDataSource(mockClient))

        val request = UserCreateRequest(
            name = "Maria Santos",
            email = "maria@example.com",
            doc_id = "12345678901",
            phone = "+5585988888888"
        )
        val result = repository.createUser(request)
        assertTrue(result.isSuccess)
        val user = result.getOrThrow()
        assertEquals(2L, user.id)
        assertEquals("Maria Santos", user.name)
    }

    @Test
    fun updateUser_success_returnsUpdatedUser() = runTest {
        val jsonResponse = """
            {
                "data": {
                    "id": 1,
                    "name": "Warley Updated",
                    "email": "warley.new@example.com",
                    "doc_id": "52998224725",
                    "phone": "+5585977777777"
                },
                "error": null
            }
        """.trimIndent()

        val mockClient = createMockClient(jsonResponse, HttpStatusCode.OK)
        val repository = DefaultUserRepository(KtorUserRemoteDataSource(mockClient))

        val request = UserUpdateRequest(
            name = "Warley Updated",
            email = "warley.new@example.com",
            phone = "+5585977777777"
        )
        val result = repository.updateUser(1L, request)
        assertTrue(result.isSuccess)
        val user = result.getOrThrow()
        assertEquals("Warley Updated", user.name)
    }

    @Test
    fun deleteUser_success_returnsSuccess() = runTest {
        val mockClient = createMockClient("", HttpStatusCode.NoContent)
        val repository = DefaultUserRepository(KtorUserRemoteDataSource(mockClient))

        val result = repository.deleteUser(2L)
        assertTrue(result.isSuccess)
    }

    @Test
    fun getUserAccounts_success_returnsAccountsList() = runTest {
        val jsonResponse = """
            {
                "data": [
                    {
                        "id": 10,
                        "account_number": "123456",
                        "agency_number": "0001",
                        "balance": 500.0,
                        "status": "active"
                    }
                ],
                "error": null
            }
        """.trimIndent()

        val mockClient = createMockClient(jsonResponse, HttpStatusCode.OK)
        val repository = DefaultUserRepository(KtorUserRemoteDataSource(mockClient))

        val result = repository.getUserAccounts(1L)
        assertTrue(result.isSuccess)
        val accounts = result.getOrThrow()
        assertEquals(1, accounts.size)
        assertEquals(10L, accounts[0].id)
    }

    @Test
    fun getUser_notFound_returnsUserNotFoundException() = runTest {
        val jsonResponse = """
            {
                "data": null,
                "error": "Record not found"
            }
        """.trimIndent()

        val mockClient = createMockClient(jsonResponse, HttpStatusCode.NotFound)
        val repository = DefaultUserRepository(KtorUserRemoteDataSource(mockClient))

        val result = repository.getUser(999L)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is UserNotFoundException)
    }

    @Test
    fun deleteUser_hasAccounts_returnsApiException() = runTest {
        val jsonResponse = """
            {
                "data": null,
                "error": {
                    "base": ["Cannot delete record because dependent accounts exist"]
                }
            }
        """.trimIndent()

        val mockClient = createMockClient(jsonResponse, HttpStatusCode.UnprocessableEntity)
        val repository = DefaultUserRepository(KtorUserRemoteDataSource(mockClient))

        val result = repository.deleteUser(1L)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ApiException)
    }
}
