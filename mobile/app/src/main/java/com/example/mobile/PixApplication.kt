package com.example.mobile

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.mobile.network.BiometricCredentialStore
import com.example.mobile.network.KeystoreBiometricCredentialStore
import com.example.mobile.network.KtorAccountRemoteDataSource
import com.example.mobile.network.KtorAuthRemoteDataSource
import com.example.mobile.network.KtorClient
import com.example.mobile.network.KtorPixKeyRemoteDataSource
import com.example.mobile.network.KtorScheduledTransferRemoteDataSource
import com.example.mobile.network.KtorTransferRemoteDataSource
import com.example.mobile.network.KtorUserRemoteDataSource
import com.example.mobile.network.LocalProfilePhotoStore
import com.example.mobile.network.NotificationPreferencesStore
import com.example.mobile.network.ProfilePhotoStore
import com.example.mobile.network.RecentPixContactsStore
import com.example.mobile.network.SessionStore
import com.example.mobile.network.SharedPreferencesNotificationPreferencesStore
import com.example.mobile.network.SharedPreferencesRecentPixContactsStore
import com.example.mobile.repository.AccountRepository
import com.example.mobile.repository.AuthRepository
import com.example.mobile.repository.DefaultAccountRepository
import com.example.mobile.repository.DefaultAuthRepository
import com.example.mobile.repository.DefaultPixKeyRepository
import com.example.mobile.repository.DefaultScheduledTransferRepository
import com.example.mobile.repository.DefaultTransferRepository
import com.example.mobile.repository.PixKeyRepository
import com.example.mobile.repository.ScheduledTransferRepository
import com.example.mobile.repository.TransferRepository

/** Manual dependency container: one HTTP client and one repository per feature. */
class AppContainer(context: Context) {
    private val sessionStore = SessionStore()
    private val client = KtorClient.create(sessionStore)

    val recentPixContactsStore: RecentPixContactsStore by lazy {
        SharedPreferencesRecentPixContactsStore(context)
    }
    val notificationPreferencesStore: NotificationPreferencesStore by lazy {
        SharedPreferencesNotificationPreferencesStore(context)
    }
    val profilePhotoStore: ProfilePhotoStore by lazy {
        LocalProfilePhotoStore(context)
    }
    val biometricCredentialStore: BiometricCredentialStore by lazy {
        KeystoreBiometricCredentialStore(context)
    }

    private val userRemoteDataSource by lazy { KtorUserRemoteDataSource(client) }

    val authRepository: AuthRepository by lazy {
        DefaultAuthRepository(KtorAuthRemoteDataSource(client), sessionStore, userRemoteDataSource)
    }
    val accountRepository: AccountRepository by lazy {
        DefaultAccountRepository(KtorAccountRemoteDataSource(client), userRemoteDataSource)
    }
    val pixKeyRepository: PixKeyRepository by lazy {
        DefaultPixKeyRepository(KtorPixKeyRemoteDataSource(client))
    }
    val transferRepository: TransferRepository by lazy {
        DefaultTransferRepository(KtorTransferRemoteDataSource(client))
    }
    val scheduledTransferRepository: ScheduledTransferRepository by lazy {
        DefaultScheduledTransferRepository(KtorScheduledTransferRemoteDataSource(client))
    }
}

class PixApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

fun CreationExtras.container(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as PixApplication).container
