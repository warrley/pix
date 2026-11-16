package com.example.mobile.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mobile.container
import com.example.mobile.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

sealed interface Screen {
    data object Login : Screen
    data object Home : Screen
    data class PixHub(val accountId: Long, val balance: String) : Screen
    data class PixKeys(val accountId: Long) : Screen
    data class Transfer(
        val accountId: Long,
        val balance: String = "0.00",
        val initialKey: String = "",
        val initialAmount: String = ""
    ) : Screen
    data class FinancialSummary(val accountId: Long) : Screen
}

/** Owns the navigation state; the session decides between the auth flow and the app. */
class AppViewModel(authRepository: AuthRepository) : ViewModel() {

    /** Id of the logged-in user; used to scope per-user ViewModels so no data leaks across logins. */
    val userId: StateFlow<Long?> = authRepository.session
        .map { it?.user?.id }
        .stateIn(viewModelScope, SharingStarted.Eagerly, authRepository.session.value?.user?.id)

    private val _screen = MutableStateFlow<Screen>(
        if (authRepository.session.value != null) Screen.Home else Screen.Login
    )
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    init {
        authRepository.session
            .map { it != null }
            .distinctUntilChanged()
            .onEach { loggedIn -> _screen.value = if (loggedIn) Screen.Home else Screen.Login }
            .launchIn(viewModelScope)
    }

    fun navigate(screen: Screen) {
        _screen.value = screen
    }

    fun back() {
        when (val current = _screen.value) {
            Screen.Login, Screen.Home -> Unit
            is Screen.Transfer -> _screen.value = Screen.Home
            is Screen.PixKeys -> _screen.value = Screen.Home
            is Screen.PixHub -> _screen.value = Screen.Home
            is Screen.FinancialSummary -> _screen.value = Screen.Home
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(container().authRepository) }
        }
    }
}
