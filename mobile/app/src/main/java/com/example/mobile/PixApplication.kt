package com.example.mobile

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.mobile.repository.AccountRepository
import com.example.mobile.repository.DefaultAccountRepository
import com.example.mobile.repository.DefaultUserRepository
import com.example.mobile.repository.UserRepository

class PixApplication : Application() {
    val accountRepository: AccountRepository by lazy {
        DefaultAccountRepository()
    }

    val userRepository: UserRepository by lazy {
        DefaultUserRepository()
    }
}

fun CreationExtras.accountRepository(): AccountRepository =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as PixApplication).accountRepository

fun CreationExtras.userRepository(): UserRepository =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as PixApplication).userRepository
