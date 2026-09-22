package com.example.mobile

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.mobile.repository.AccountRepository
import com.example.mobile.repository.DefaultAccountRepository

class PixApplication : Application() {
    val accountRepository: AccountRepository by lazy {
        DefaultAccountRepository()
    }
}

fun CreationExtras.accountRepository(): AccountRepository =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as PixApplication).accountRepository
