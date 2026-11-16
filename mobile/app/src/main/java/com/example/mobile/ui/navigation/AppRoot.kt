package com.example.mobile.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mobile.ui.auth.AuthScreen
import com.example.mobile.ui.components.UnderDevelopmentScreen
import com.example.mobile.ui.home.HomeScreen
import com.example.mobile.ui.home.HomeViewModel
import com.example.mobile.ui.pix.PixHubScreen
import com.example.mobile.ui.pix.PixKeysScreen
import com.example.mobile.ui.summary.FinancialSummaryScreen
import com.example.mobile.ui.transfer.TransferScreen

@Composable
fun AppRoot(viewModel: AppViewModel = viewModel(factory = AppViewModel.Factory)) {
    val screen by viewModel.screen.collectAsState()
    val userId by viewModel.userId.collectAsState()

    BackHandler(enabled = screen != Screen.Login && screen != Screen.Home) { viewModel.back() }

    when (val current = screen) {
        Screen.Login -> AuthScreen()
        Screen.Home -> HomeScreen(
            viewModelKey = "home-$userId",
            onOpenPixHub = { accountId, balance ->
                viewModel.navigate(Screen.PixHub(accountId, balance))
            },
            onOpenTransfer = { accountId, balance ->
                viewModel.navigate(Screen.Transfer(accountId, balance))
            },
            onOpenSummary = { accountId ->
                viewModel.navigate(Screen.FinancialSummary(accountId))
            },
            onOpenUnderDevelopment = { feature ->
                viewModel.navigate(Screen.UnderDevelopment(feature))
            }
        )
        is Screen.PixHub -> PixHubScreen(
            onBack = viewModel::back,
            onOpenTransfer = { key, amount ->
                viewModel.navigate(
                    Screen.Transfer(
                        accountId = current.accountId,
                        balance = current.balance,
                        initialKey = key,
                        initialAmount = amount
                    )
                )
            },
            onOpenPixKeys = {
                viewModel.navigate(Screen.PixKeys(current.accountId))
            },
            onOpenUnderDevelopment = { feature ->
                viewModel.navigate(Screen.UnderDevelopment(feature))
            }
        )
        is Screen.PixKeys -> PixKeysScreen(current.accountId, onBack = viewModel::back)
        is Screen.Transfer -> {
            val homeViewModel: HomeViewModel = viewModel(
                key = "home-$userId",
                factory = HomeViewModel.Factory
            )
            val homeState by homeViewModel.uiState.collectAsState()
            TransferScreen(
                accountId = current.accountId,
                accountBalance = current.balance,
                recentStatement = homeState.statement,
                initialKey = current.initialKey,
                initialAmount = current.initialAmount,
                onBack = viewModel::back
            )
        }
        is Screen.FinancialSummary -> {
            val homeViewModel: HomeViewModel = viewModel(
                key = "home-$userId",
                factory = HomeViewModel.Factory
            )
            FinancialSummaryScreen(
                accountId = current.accountId,
                homeViewModel = homeViewModel,
                onBack = viewModel::back
            )
        }
        is Screen.UnderDevelopment -> UnderDevelopmentScreen(
            featureName = current.featureName,
            onBack = viewModel::back
        )
    }
}
