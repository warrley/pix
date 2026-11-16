package com.example.mobile.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mobile.container
import com.example.mobile.model.PixKeyLookupResponse
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
    data class DailyLimit(val accountId: Long) : Screen
    data class ScheduledTransfers(val accountId: Long, val balance: String) : Screen
    data class PixCharge(val accountId: Long) : Screen
    data class PixQrScan(
        val accountId: Long,
        val balance: String,
        val startInPasteMode: Boolean = false
    ) : Screen
    data class Transfer(
        val accountId: Long,
        val balance: String = "0.00",
        val initialKey: String = "",
        val initialAmount: String = "",
        val initialRecipient: PixKeyLookupResponse? = null,
        val initialDescription: String = "",
        val flowId: Long = System.nanoTime()
    ) : Screen
    data class FinancialSummary(val accountId: Long) : Screen
    data class TransactionHistory(val accountId: Long, val balance: String) : Screen
    data object EditProfile : Screen
    data class UnderDevelopment(val featureName: String) : Screen
}

/** Owns the navigation state; the session decides between the auth flow and the app. */
class AppViewModel(authRepository: AuthRepository) : ViewModel() {

    /** Id of the logged-in user; used to scope per-user ViewModels so no data leaks across logins. */
    val userId: StateFlow<Long?> = authRepository.session
        .map { it?.user?.id }
        .stateIn(viewModelScope, SharingStarted.Eagerly, authRepository.session.value?.user?.id)

    val userName: StateFlow<String> = authRepository.session
        .map { it?.user?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, authRepository.session.value?.user?.name.orEmpty())

    private val backStack = mutableListOf<Screen>(
        if (authRepository.session.value != null) Screen.Home else Screen.Login
    )

    private val _screen = MutableStateFlow(backStack.last())
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    init {
        authRepository.session
            .map { it != null }
            .distinctUntilChanged()
            .onEach { loggedIn ->
                val root = if (loggedIn) Screen.Home else Screen.Login
                backStack.clear()
                backStack.add(root)
                _screen.value = root
            }
            .launchIn(viewModelScope)
    }

    fun navigate(screen: Screen) {
        if (backStack.lastOrNull() == screen) return
        backStack.add(screen)
        _screen.value = screen
    }

    fun replaceTop(screen: Screen) {
        if (backStack.isNotEmpty()) {
            backStack[backStack.lastIndex] = screen
        } else {
            backStack.add(screen)
        }
        _screen.value = screen
    }

    fun back() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
            _screen.value = backStack.last()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(container().authRepository) }
        }
    }
}
