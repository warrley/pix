package com.example.mobile.network

import com.example.mobile.model.UserResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Session(val token: String, val user: UserResponse)

/**
 * In-memory holder of the authenticated session. The bearer token is intentionally not
 * persisted: it lives 15 minutes on the backend, so the user logs in again after a restart.
 */
class SessionStore {
    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    private val _expired = MutableStateFlow(false)
    /** True when the session ended because the server rejected the token. */
    val expired: StateFlow<Boolean> = _expired.asStateFlow()

    val token: String? get() = _session.value?.token

    fun start(session: Session) {
        _expired.value = false
        _session.value = session
    }

    fun updateUser(user: UserResponse) {
        val current = _session.value ?: return
        _session.value = current.copy(user = user)
    }

    fun end(expired: Boolean = false) {
        _expired.value = expired
        _session.value = null
    }

    fun acknowledgeExpiration() {
        _expired.value = false
    }
}
