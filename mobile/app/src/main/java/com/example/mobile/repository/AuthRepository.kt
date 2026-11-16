package com.example.mobile.repository

import com.example.mobile.model.SignUpRequest
import com.example.mobile.model.UserResponse
import com.example.mobile.network.AuthRemoteDataSource
import com.example.mobile.network.Session
import com.example.mobile.network.SessionStore
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val session: StateFlow<Session?>
    val sessionExpired: StateFlow<Boolean>

    suspend fun signUp(request: SignUpRequest): Result<UserResponse>
    suspend fun login(email: String, password: String): Result<UserResponse>
    suspend fun logout()
    fun acknowledgeExpiration()
}

class DefaultAuthRepository(
    private val remote: AuthRemoteDataSource,
    private val sessionStore: SessionStore
) : AuthRepository {
    override val session: StateFlow<Session?> = sessionStore.session
    override val sessionExpired: StateFlow<Boolean> = sessionStore.expired

    override suspend fun signUp(request: SignUpRequest) = remote.signUp(request)

    override suspend fun login(email: String, password: String): Result<UserResponse> =
        remote.login(email.trim(), password).map { token ->
            sessionStore.start(Session(token.accessToken, token.user))
            token.user
        }

    override suspend fun logout() {
        // Best effort: the local session ends even if the server cannot be reached.
        remote.logout()
        sessionStore.end()
    }

    override fun acknowledgeExpiration() = sessionStore.acknowledgeExpiration()
}
