package com.example.mobile.network

import com.example.mobile.model.LoginRequest
import com.example.mobile.model.SignUpBody
import com.example.mobile.model.SignUpRequest
import com.example.mobile.model.TokenResponse
import com.example.mobile.model.UserResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody

interface AuthRemoteDataSource {
    suspend fun signUp(request: SignUpRequest): Result<UserResponse>
    suspend fun login(email: String, password: String): Result<TokenResponse>
    suspend fun logout(): Result<Unit>
}

class KtorAuthRemoteDataSource(
    private val client: HttpClient
) : AuthRemoteDataSource {

    override suspend fun signUp(request: SignUpRequest): Result<UserResponse> = executeApiRequest(
        notFound = { ApiException("Serviço de cadastro indisponível") },
        defaultUnprocessableMessage = "Dados de cadastro inválidos.",
        request = { client.post("auth/signup") { setBody(SignUpBody(request)) } },
        onSuccess = { it.unwrap("Erro ao criar usuário") }
    )

    override suspend fun login(email: String, password: String): Result<TokenResponse> = executeApiRequest(
        notFound = { ApiException("Serviço de login indisponível") },
        request = { client.post("auth/token") { setBody(LoginRequest(email, password)) } },
        onSuccess = { it.unwrap("Resposta de login inválida") }
    )

    override suspend fun logout(): Result<Unit> = executeApiRequest(
        notFound = { ApiException("Sessão não encontrada") },
        request = { client.delete("auth/token") },
        onSuccess = { Result.success(Unit) }
    )
}
