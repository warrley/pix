package com.example.mobile.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Pix
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mobile.model.AccountResponse
import com.example.mobile.model.StatementEntry
import com.example.mobile.ui.components.ErrorText
import com.example.mobile.ui.components.LoadingBox
import com.example.mobile.ui.components.RubyGemIcon
import com.example.mobile.ui.theme.RuCoralRed
import com.example.mobile.ui.theme.RuGreen
import com.example.mobile.ui.theme.RuRubyDark
import com.example.mobile.ui.theme.RuRubyRed
import com.example.mobile.util.toBrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenPixHub: (Long, String) -> Unit,
    onOpenPixKeys: (Long) -> Unit,
    onOpenTransfer: (Long, String) -> Unit,
    onOpenSummary: (Long) -> Unit,
    viewModelKey: String,
    viewModel: HomeViewModel = viewModel(key = viewModelKey, factory = HomeViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsState()
    var showProfileSheet by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(Unit) { viewModel.refresh() }

    val selectedAccount = state.selectedAccount

    Scaffold(
        containerColor = colors.background,
        bottomBar = {
            if (selectedAccount != null) {
                RuFloatingDock(
                    onHomeClick = viewModel::refresh,
                    onPixClick = { onOpenPixHub(selectedAccount.id, selectedAccount.balance) },
                    onSummaryClick = { onOpenSummary(selectedAccount.id) },
                    onKeysClick = { onOpenPixKeys(selectedAccount.id) }
                )
            }
        }
    ) { padding ->
        if (state.isLoading && state.accounts.isEmpty()) {
            LoadingBox()
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(bottom = padding.calculateBottomPadding())
                .verticalScroll(rememberScrollState())
        ) {
            // Original Nubank Header (Figma Tela 1 — leftmost original screen)
            HomeHeroHeader(
                userName = state.user?.name?.substringBefore(' ').orEmpty(),
                isBalanceVisible = state.isBalanceVisible,
                onAvatarClick = { showProfileSheet = true },
                onToggleBalance = viewModel::toggleBalanceVisibility,
                onRefresh = viewModel::refresh,
                onSummaryPromoClick = {
                    selectedAccount?.let { onOpenSummary(it.id) }
                },
                onPixKeysPromoClick = {
                    selectedAccount?.let { onOpenPixKeys(it.id) }
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                state.message?.let {
                    Text(it, color = RuGreen, style = MaterialTheme.typography.bodyMedium)
                }
                ErrorText(state.error)

                if (selectedAccount == null) {
                    EmptyAccountCard(
                        isBusy = state.isBusy,
                        onCreateAccount = viewModel::createAccount
                    )
                } else {
                    // "Saldo em conta" section (Original Nubank Tela 1)
                    BalanceSection(
                        account = selectedAccount,
                        balanceVisible = state.isBalanceVisible,
                        onClick = { onOpenSummary(selectedAccount.id) }
                    )

                    // Circular Quick Actions Row (without Nova Conta / Encerrar Conta, which live in Profile)
                    QuickActionsRow(
                        enabled = selectedAccount.isActive && !state.isBusy,
                        onAreaPix = { onOpenPixHub(selectedAccount.id, selectedAccount.balance) },
                        onTransfer = { onOpenTransfer(selectedAccount.id, selectedAccount.balance) },
                        onPixKeys = { onOpenPixKeys(selectedAccount.id) },
                        onSummary = { onOpenSummary(selectedAccount.id) }
                    )

                    // Pill Card ("Dados da conta" — opens Profile / Account Switcher or Pix Keys)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surface)
                            .clickable { showProfileSheet = true }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = colors.onSurface
                            )
                            Column {
                                Text(
                                    text = "Agência ${selectedAccount.agencyNumber} • Conta ${selectedAccount.accountNumber}",
                                    color = colors.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Toque para alternar ou gerenciar suas contas",
                                    color = colors.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant
                        )
                    }

                    // Promo Card (Figma Tela 1 bottom card with heart icon)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSummary(selectedAccount.id) },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Convide todo mundo para o RuBank",
                                    color = colors.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Coisa boa a gente compartilha.",
                                    color = colors.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = colors.outline)

                    // Extrato / Movimentações
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Histórico de movimentações",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { onOpenSummary(selectedAccount.id) }) {
                            Text("Ver estatísticas", color = colors.primary)
                        }
                    }

                    if (state.statement.isEmpty()) {
                        Text(
                            text = "Nenhuma movimentação registrada nesta conta ainda.",
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        state.statement.forEach { entry ->
                            StatementRow(entry)
                        }
                    }
                }
                Spacer(Modifier.height(72.dp))
            }
        }
    }

    // Profile & Account Management ModalBottomSheet (Criar conta, Alternar conta, Encerrar conta, Logout)
    if (showProfileSheet) {
        ModalBottomSheet(
            onDismissRequest = { showProfileSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(colors.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PersonOutline,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column {
                        Text(
                            text = state.user?.name ?: "Cliente RuBank",
                            color = colors.onSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = state.user?.email.orEmpty(),
                            color = colors.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }

                HorizontalDivider(color = colors.outline)

                Text(
                    text = "Minhas contas bancárias",
                    color = colors.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                state.accounts.forEach { acc ->
                    val isSelected = acc.id == selectedAccount?.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) colors.primaryContainer else colors.surfaceVariant)
                            .clickable {
                                viewModel.selectAccount(acc.id)
                                showProfileSheet = false
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Ag. ${acc.agencyNumber} • Conta ${acc.accountNumber}",
                                color = if (isSelected) colors.onPrimaryContainer else colors.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Saldo: ${acc.balance.toBrl()} (${acc.status})",
                                color = if (isSelected) colors.onPrimaryContainer.copy(alpha = 0.85f) else colors.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selecionada",
                                tint = colors.onPrimaryContainer
                            )
                        }
                    }
                }

                // Create new bank account button inside Profile
                Button(
                    onClick = {
                        viewModel.createAccount()
                        showProfileSheet = false
                    },
                    enabled = !state.isBusy,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Abrir nova conta bancária", color = Color.White, fontWeight = FontWeight.Bold)
                }

                // Close current active account button inside Profile
                if (selectedAccount != null && selectedAccount.isActive) {
                    OutlinedButton(
                        onClick = {
                            showProfileSheet = false
                            confirmClose = true
                        },
                        enabled = !state.isBusy,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = RuCoralRed)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Encerrar conta ${selectedAccount.accountNumber}",
                            color = RuCoralRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                HorizontalDivider(color = colors.outline)

                // Logout button inside Profile
                TextButton(
                    onClick = {
                        showProfileSheet = false
                        viewModel.logout()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = RuCoralRed)
                    Spacer(Modifier.width(8.dp))
                    Text("Sair do aplicativo", color = RuCoralRed, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (confirmClose) {
        AlertDialog(
            containerColor = colors.surface,
            titleContentColor = colors.onSurface,
            textContentColor = colors.onSurfaceVariant,
            onDismissRequest = { confirmClose = false },
            title = { Text("Encerrar conta?") },
            text = { Text("Só é possível encerrar contas com saldo zero. Essa ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClose = false
                        viewModel.closeSelectedAccount()
                    }
                ) { Text("Encerrar", color = RuCoralRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClose = false }) {
                    Text("Cancelar", color = colors.onSurface)
                }
            }
        )
    }
}

@Composable
private fun HomeHeroHeader(
    userName: String,
    isBalanceVisible: Boolean,
    onAvatarClick: () -> Unit,
    onToggleBalance: () -> Unit,
    onRefresh: () -> Unit,
    onSummaryPromoClick: () -> Unit,
    onPixKeysPromoClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(RuRubyRed, RuRubyDark)
                )
            )
            .padding(top = 42.dp, bottom = 20.dp)
    ) {
        // Top row: Avatar (opens Profile) + Eye + Help + Shield (matches Figma Tela 1)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.clickable(onClick = onAvatarClick)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PersonOutline,
                        contentDescription = "Perfil e contas",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .align(Alignment.TopEnd)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleBalance) {
                    Icon(
                        imageVector = if (isBalanceVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Ocultar ou mostrar saldo",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = "Atualizar",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onAvatarClick) {
                    Icon(
                        imageVector = Icons.Outlined.VerifiedUser,
                        contentDescription = "Perfil e segurança",
                        tint = Color.White
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "Olá, ${userName.ifBlank { "Cliente" }}",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .clickable(onClick = onAvatarClick)
        )

        Spacer(Modifier.height(14.dp))

        // Figma Tela 1 Header Highlight Cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(175.dp)
                    .height(96.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.16f))
                    .clickable(onClick = onPixKeysPromoClick)
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        Icons.Default.Key,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Confira as chaves Pix ativas na conta",
                        color = Color.White,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(185.dp)
                    .height(96.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
                    .clickable(onClick = onSummaryPromoClick)
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RubyGemIcon(size = 24.dp)
                        Text(
                            text = "⋮",
                            color = RuRubyDark,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Chegou seu resumo financeiro mensal",
                        color = RuRubyRed,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun BalanceSection(
    account: AccountResponse,
    balanceVisible: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Saldo em conta",
                color = colors.onBackground,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.onSurfaceVariant
            )
        }
        Text(
            text = if (balanceVisible) account.balance.toBrl() else "••••",
            color = colors.onBackground,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold
        )
        if (!account.isActive) {
            Text(
                text = "Conta ${account.status}",
                color = RuCoralRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun QuickActionsRow(
    enabled: Boolean,
    onAreaPix: () -> Unit,
    onTransfer: () -> Unit,
    onPixKeys: () -> Unit,
    onSummary: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularActionItem(
            icon = Icons.Default.Pix,
            label = "Área Pix e\nTransferir",
            enabled = enabled,
            onClick = onAreaPix
        )
        CircularActionItem(
            icon = Icons.AutoMirrored.Filled.CompareArrows,
            label = "Transferir",
            enabled = enabled,
            onClick = onTransfer
        )
        CircularActionItem(
            icon = Icons.Default.Key,
            label = "Minhas\nchaves",
            enabled = enabled,
            onClick = onPixKeys
        )
        CircularActionItem(
            icon = Icons.Default.Analytics,
            label = "Resumo\nmensal",
            enabled = true,
            onClick = onSummary
        )
    }
}

@Composable
private fun CircularActionItem(
    icon: ImageVector,
    label: String,
    badge: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(74.dp)
    ) {
        Box(contentAlignment = Alignment.BottomCenter) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .clickable(enabled = enabled, onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (enabled) colors.onSurface else colors.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.primary)
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = badge,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = colors.onBackground,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyAccountCard(
    isBusy: Boolean,
    onCreateAccount: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Abra sua primeira conta RuBank",
                color = colors.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Text(
                text = "Com ela você pode cadastrar chaves Pix, enviar e receber transferências instantâneas e visualizar o Resumo Financeiro.",
                color = colors.onSurfaceVariant,
                fontSize = 14.sp
            )
            Button(
                onClick = onCreateAccount,
                enabled = !isBusy,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Criar minha primeira conta", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun StatementRow(entry: StatementEntry) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(colors.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                        contentDescription = null,
                        tint = if (entry.isDebit) RuCoralRed else RuGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = if (entry.isDebit) "Transferência enviada" else "Transferência recebida",
                        color = colors.onBackground,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = entry.counterparty.name ?: "Pix • ${entry.status}",
                        color = colors.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
            Text(
                text = (if (entry.isDebit) "- " else "+ ") + entry.amount.toBrl(),
                color = if (entry.isDebit) RuCoralRed else RuGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
        HorizontalDivider(color = colors.outline)
    }
}

@Composable
private fun RuFloatingDock(
    onHomeClick: () -> Unit,
    onPixClick: () -> Unit,
    onSummaryClick: () -> Unit,
    onKeysClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(colors.surface.copy(alpha = 0.96f))
                .border(1.dp, colors.outline, RoundedCornerShape(50))
                .padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onHomeClick,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(colors.primary)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.CompareArrows,
                    contentDescription = "Início",
                    tint = Color.White
                )
            }
            IconButton(onClick = onPixClick, modifier = Modifier.size(42.dp)) {
                Icon(
                    Icons.Default.AttachMoney,
                    contentDescription = "Área Pix",
                    tint = colors.onSurfaceVariant
                )
            }
            IconButton(onClick = onSummaryClick, modifier = Modifier.size(42.dp)) {
                Icon(
                    Icons.Default.Analytics,
                    contentDescription = "Resumo Financeiro",
                    tint = colors.onSurfaceVariant
                )
            }
            IconButton(onClick = onKeysClick, modifier = Modifier.size(42.dp)) {
                Icon(
                    Icons.Default.ShoppingBag,
                    contentDescription = "Chaves Pix",
                    tint = colors.onSurfaceVariant
                )
            }
        }
    }
}
