package com.example.mobile.ui.account

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mobile.model.AccountResponse
import com.example.mobile.model.UserResponse
import com.example.mobile.ui.profile.ProfileManagementSheet
import com.example.mobile.ui.theme.NuActionCircle
import com.example.mobile.ui.theme.NuDivider
import com.example.mobile.ui.theme.NuError
import com.example.mobile.ui.theme.NuPurple
import com.example.mobile.ui.theme.NuPurpleDark
import com.example.mobile.ui.theme.NuPurpleLight
import com.example.mobile.ui.theme.NuSuccess
import com.example.mobile.ui.theme.NuTextPrimary
import com.example.mobile.ui.theme.NuTextSecondary
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = viewModel(factory = AccountViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showProfileSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val successState = uiState as? AccountUiState.Success

    LaunchedEffect(successState?.feedbackMessage) {
        val msg = successState?.feedbackMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedbackMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
        ) {
            // Nubank Header (Always visible)
            NubankHeader(
                userName = successState?.user?.name ?: "Warley",
                userInitial = successState?.user?.name?.firstOrNull()?.uppercase() ?: "W",
                isBalanceVisible = successState?.isBalanceVisible ?: true,
                onToggleVisibility = { viewModel.toggleBalanceVisibility() },
                onAvatarClick = { showProfileSheet = true },
                onHelpClick = {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("PIX App — Gestão de Configuração (UFC)")
                    }
                }
            )

            // Body Content based on state
            when (val state = uiState) {
                is AccountUiState.Loading -> {
                    LoadingState(modifier = Modifier.fillMaxSize())
                }

                is AccountUiState.Error -> {
                    ErrorState(
                        message = state.message,
                        onRetry = { viewModel.loadAccount() },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is AccountUiState.Success -> {
                    AccountSuccessContent(
                        account = state.account,
                        isBalanceVisible = state.isBalanceVisible,
                        onRefresh = { viewModel.loadAccount(state.account.id) },
                        onOpenProfile = { showProfileSheet = true },
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }

    // Profile & Account Management BottomSheet
    if (showProfileSheet && successState != null) {
        ProfileManagementSheet(
            sheetState = sheetState,
            user = successState.user,
            currentAccount = successState.account,
            userAccounts = successState.userAccounts,
            onDismiss = { showProfileSheet = false },
            onCreateUser = { name, email, docId, phone ->
                viewModel.createUser(name, email, docId, phone) { success, msg ->
                    coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                }
            },
            onUpdateUser = { name, email, phone ->
                viewModel.updateUser(name, email, phone) { success, msg ->
                    coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                }
            },
            onDeleteUser = {
                viewModel.deleteUser { success, msg ->
                    coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                    if (success) showProfileSheet = false
                }
            },
            onCreateAccount = {
                viewModel.createAccount { success, msg ->
                    coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                }
            },
            onDeleteAccount = {
                viewModel.deleteAccount { success, msg ->
                    coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                    if (success) showProfileSheet = false
                }
            },
            onSwitchAccount = { accountId ->
                viewModel.switchAccount(accountId)
                showProfileSheet = false
            }
        )
    }
}

@Composable
private fun NubankHeader(
    userName: String,
    userInitial: String,
    isBalanceVisible: Boolean,
    onToggleVisibility: () -> Unit,
    onAvatarClick: () -> Unit,
    onHelpClick: () -> Unit
) {
    Surface(
        color = NuPurple,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // User Avatar Circle (clicking opens profile and user management)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(NuPurpleDark)
                        .clickable(onClick = onAvatarClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = userInitial,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                // Header Action Icons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleVisibility) {
                        Icon(
                            imageVector = if (isBalanceVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (isBalanceVisible) "Ocultar saldo" else "Mostrar saldo",
                            tint = Color.White
                        )
                    }

                    IconButton(onClick = onHelpClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "Me ajude",
                            tint = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Olá, $userName",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AccountSuccessContent(
    account: AccountResponse,
    isBalanceVisible: Boolean,
    onRefresh: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(vertical = 16.dp)) {
        // Account Balance Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onRefresh() }
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Conta",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NuTextPrimary
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Ver extrato",
                    tint = NuTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val formattedBalance = formatCurrency(account.balance)
            AnimatedVisibility(
                visible = isBalanceVisible,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = formattedBalance,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = NuTextPrimary
                )
            }

            if (!isBalanceVisible) {
                Text(
                    text = "••••",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = NuTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Account and Agency Details chip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Ag. ${account.agency_number} • C/C ${account.account_number}",
                    fontSize = 13.sp,
                    color = NuTextSecondary
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (account.status == "active") NuSuccess.copy(alpha = 0.15f) else NuError.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (account.status == "active") "Ativa" else account.status.replaceFirstChar { it.uppercase() },
                        color = if (account.status == "active") NuSuccess else NuError,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Action Buttons (horizontal scroll)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NubankActionButton(
                icon = Icons.Default.QrCode,
                label = "Área Pix",
                onClick = {}
            )
            NubankActionButton(
                icon = Icons.Default.NorthEast,
                label = "Transferir",
                onClick = {}
            )
            NubankActionButton(
                icon = Icons.Default.SouthWest,
                label = "Depositar",
                onClick = {}
            )
            NubankActionButton(
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                label = "Extrato",
                onClick = {}
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Profile & Accounts Management Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NuActionCircle),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clickable(onClick = onOpenProfile)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ManageAccounts,
                    contentDescription = null,
                    tint = NuPurple,
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Gerenciar Perfil e Contas",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = NuTextPrimary
                    )
                    Text(
                        text = "Cadastrar usuário, alterar dados ou abrir nova conta",
                        fontSize = 12.sp,
                        color = NuTextSecondary
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = NuTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // My Cards Container
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NuActionCircle),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CreditCard,
                    contentDescription = null,
                    tint = NuPurple,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Meus cartões",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = NuTextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Security / Antifraud Info Banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NuPurpleLight)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Segurança no Pix",
                    fontWeight = FontWeight.Bold,
                    color = NuPurple,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Suas transações Pix contam com limites de segurança e análise antifraude em tempo real.",
                    color = NuTextPrimary,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun NubankActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(NuActionCircle),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = NuTextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = NuTextPrimary
        )
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                color = NuPurple,
                strokeWidth = 3.dp,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Carregando informações da conta...",
                color = NuTextSecondary,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isNotFound = message.contains("não encontrada", ignoreCase = true) || message.contains("404")
    val title = if (isNotFound) "Conta não encontrada" else "Não foi possível carregar a conta"

    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = NuActionCircle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(if (isNotFound) NuPurpleLight.copy(alpha = 0.35f) else NuError.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = "Erro",
                        tint = if (isNotFound) NuPurple else NuError,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = NuTextPrimary,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (isNotFound) {
                        "Não encontramos nenhuma conta com o identificador informado. Verifique os dados ou tente novamente."
                    } else {
                        message
                    },
                    color = NuTextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = NuPurple),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tentar novamente",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

private fun formatCurrency(amount: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
    return formatter.format(amount)
}
