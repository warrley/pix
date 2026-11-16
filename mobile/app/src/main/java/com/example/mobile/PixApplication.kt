package com.example.mobile

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.mobile.network.KtorAccountRemoteDataSource
import com.example.mobile.network.KtorAuthRemoteDataSource
import com.example.mobile.network.KtorClient
import com.example.mobile.network.KtorPixKeyRemoteDataSource
import com.example.mobile.network.KtorTransferRemoteDataSource
import com.example.mobile.network.KtorUserRemoteDataSource
import com.example.mobile.network.SessionStore
import com.example.mobile.repository.AccountRepository
import com.example.mobile.repository.AuthRepository
import com.example.mobile.repository.DefaultAccountRepository
import com.example.mobile.repository.DefaultAuthRepository
import com.example.mobile.repository.DefaultPixKeyRepository
import com.example.mobile.repository.DefaultTransferRepository
import com.example.mobile.repository.PixKeyRepository
import com.example.mobile.repository.TransferRepository

/** Manual dependency container: one HTTP client and one repository per feature. */
class AppContainer {
    private val sessionStore = SessionStore()
    private val client = KtorClient.create(sessionStore)

    val authRepository: AuthRepository by lazy {
        DefaultAuthRepository(KtorAuthRemoteDataSource(client), sessionStore)
    }
    val accountRepository: AccountRepository by lazy {
        DefaultAccountRepository(KtorAccountRemoteDataSource(client), KtorUserRemoteDataSource(client))
    }
    val pixKeyRepository: PixKeyRepository by lazy {
        DefaultPixKeyRepository(KtorPixKeyRemoteDataSource(client))
    }
    val transferRepository: TransferRepository by lazy {
        DefaultTransferRepository(KtorTransferRemoteDataSource(client))
    }
}

class PixApplication : Application() {
    val container: AppContainer by lazy { AppContainer() }
}

fun CreationExtras.container(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as PixApplication).container
