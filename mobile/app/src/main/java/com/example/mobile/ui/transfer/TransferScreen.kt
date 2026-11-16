package com.example.mobile.ui.transfer

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mobile.model.StatementEntry
import com.example.mobile.model.TransferReceipt
import com.example.mobile.ui.components.ErrorText
import com.example.mobile.ui.components.PixScaffold
import com.example.mobile.ui.components.ScreenColumn
import com.example.mobile.ui.theme.RuGreen
import com.example.mobile.util.parseAmountInput
import com.example.mobile.util.toBrl

private data class ContactItem(
    val initials: String,
    val name: String,
    val subtitle: String,
    val pixKey: String
)

/**
 * Original Nubank 3-step Pix Transfer Flow (Figma Telas 19, 20 e 21):
 * - Step 1 (Tela 19): Validates the Pix key on the server before advancing; blocks non-existent
 *   destinations and keys owned by the current user.
 * - Step 2 (Tela 20): Shows the verified recipient name + amount + Saldo RuBank card.
 * - Step 3 (Tela 21): Review and send.
 */
@Composable
fun TransferScreen(
    accountId: Long,
    accountBalance: String = "0.00",
    recentStatement: List<StatementEntry> = emptyList(),
    initialKey: String = "",
    initialAmount: String = "",
    onBack: () -> Unit
) {
    val viewModel: TransferViewModel = viewModel(
        key = "transfer-$accountId",
        factory = TransferViewModel.factory(accountId)
    )
    val state by viewModel.uiState.collectAsState()
    val colors = MaterialTheme.colorScheme

    var step by remember { mutableIntStateOf(1) }
    var showDetails by remember { mutableStateOf(false) }

    LaunchedEffect(initialKey, initialAmount) {
        if (initialAmount.isNotBlank() && state.amount.isBlank()) {
            viewModel.onAmountChange(initialAmount)
        }
        if (initialKey.isNotBlank() && state.pixKey.isBlank()) {
            viewModel.onPixKeyChange(initialKey)
            viewModel.verifyRecipientKey {
                step = if (initialAmount.isNotBlank()) 3 else 2
            }
        }
    }

    val handleBack = {
        when {
            state.receipt != null -> onBack()
            step > 1 -> step -= 1
            else -> onBack()
        }
    }

    BackHandler(onBack = handleBack)

    val recipientDisplayName = state.recipient?.recipientName ?: state.pixKey

    val contacts = remember(recentStatement) {
        recentStatement
            .mapNotNull { it.counterparty.name?.takeIf { n -> n.isNotBlank() } }
            .distinct()
            .map { name ->
                val parts = name.trim().split(Regex("\\s+"))
                val initials = ((parts.firstOrNull()?.take(1) ?: "") + (parts.getOrNull(1)?.take(1) ?: "")).uppercase()
                ContactItem(
                    initials = initials.ifEmpty { "RB" },
                    name = name,
                    subtitle = "RUBANK PAGAMENTOS S.A.",
                    pixKey = name
                )
            }
    }

    PixScaffold(
        title = "",
        onBack = handleBack,
        useCloseIcon = step == 1
    ) { padding ->
        val receipt = state.receipt
        if (receipt != null) {
            ScreenColumn(padding) {
                ReceiptCard(receipt)
                Button(
                    onClick = {
                        step = 1
                        viewModel.startNewTransfer()
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Nova transferência", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onBack,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Voltar ao início", color = colors.onBackground)
                }
            }
            return@PixScaffold
        }

        when (step) {
            // =========================================================================
            // STEP 1 — Figma Tela 19: "Para quem você quer transferir?"
            // =========================================================================
            1 -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    ScreenColumn(androidx.compose.foundation.layout.PaddingValues()) {
                        Text(
                            text = "Para quem você quer\ntransferir?",
                            color = colors.onBackground,
                            fontSize = 26.sp,
                            lineHeight = 32.sp,
                            fontWeight = FontWeight.ExtraBold
                        )

                        Text(
                            text = "Insira os dados de quem vai receber",
                            color = colors.onSurfaceVariant,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )

                        TextField(
                            value = state.pixKey,
                            onValueChange = viewModel::onPixKeyChange,
                            placeholder = {
                                Text(
                                    "Nome, CPF/CNPJ ou chave Pix",
                                    color = colors.onSurfaceVariant,
                                    fontSize = 16.sp
                                )
                            },
                            singleLine = true,
                            enabled = !state.isLookingUp,
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Contacts,
                                    contentDescription = "Contatos",
                                    tint = colors.onSurfaceVariant
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = colors.onBackground,
                                unfocusedTextColor = colors.onBackground,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = colors.primary,
                                unfocusedIndicatorColor = colors.outline,
                                cursorColor = colors.primary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        ErrorText(state.error)

                        if (contacts.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Você sempre costuma pagar",
                                color = colors.onSurfaceVariant,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                contacts.take(4).forEach { contact ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .width(96.dp)
                                            .clickable {
                                                viewModel.onPixKeyChange(contact.pixKey)
                                            }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(colors.surface)
                                                .border(1.dp, colors.outline, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = contact.initials,
                                                color = colors.onSurface,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = contact.name,
                                            color = colors.onBackground,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = contact.subtitle,
                                            color = colors.onSurfaceVariant,
                                            fontSize = 9.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Todos os seus contatos",
                                color = colors.onSurfaceVariant,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )

                            contacts.forEach { contact ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.onPixKeyChange(contact.pixKey)
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(colors.surface),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = contact.initials,
                                            color = colors.onSurface,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Text(
                                        text = contact.name,
                                        color = colors.onBackground,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    )
                                }
                                HorizontalDivider(color = colors.outline)
                            }
                        } else {
                            Text(
                                text = "Digite o CPF, CNPJ, e-mail, telefone ou chave aleatória de outro usuário e toque na seta para validar o destinatário.",
                                color = colors.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                        Spacer(Modifier.height(76.dp))
                    }

                    // Bottom-right circular FAB validates the Pix key on the backend before proceeding
                    FloatingActionButton(
                        onClick = {
                            viewModel.verifyRecipientKey {
                                step = 2
                            }
                        },
                        shape = CircleShape,
                        containerColor = if (state.pixKey.isNotBlank()) colors.primary else colors.surface,
                        contentColor = if (state.pixKey.isNotBlank()) Color.White else colors.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(24.dp)
                    ) {
                        if (state.isLookingUp) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Continuar")
                        }
                    }
                }
            }

            // =========================================================================
            // STEP 2 — Figma Tela 20: "Qual o valor da transferência?" + "Pagando com"
            // =========================================================================
            2 -> {
                val parsedAmount = parseAmountInput(state.amount)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    ScreenColumn(androidx.compose.foundation.layout.PaddingValues()) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Transferir para",
                                color = colors.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                            Text(
                                text = recipientDisplayName.uppercase(),
                                color = colors.onBackground,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            state.recipient?.let { rec ->
                                Text(
                                    text = "Ag. ${rec.agencyNumber} • Conta ${rec.accountNumber}",
                                    color = colors.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = "Valor",
                            color = colors.onSurfaceVariant,
                            fontSize = 14.sp
                        )

                        TextField(
                            value = state.amount,
                            onValueChange = viewModel::onAmountChange,
                            placeholder = {
                                Text(
                                    "0,00",
                                    color = colors.onSurfaceVariant,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            },
                            prefix = {
                                Text(
                                    "R$ ",
                                    color = colors.onBackground,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = colors.onBackground,
                                unfocusedTextColor = colors.onBackground,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = colors.primary,
                                unfocusedIndicatorColor = colors.outline,
                                cursorColor = colors.primary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = "Pagando com",
                            color = colors.onSurfaceVariant,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )

                        // Single real payment source card: Saldo RuBank (matches Figma Tela 20 card)
                        Column(
                            modifier = Modifier
                                .width(175.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(colors.surface)
                                .border(1.5.dp, colors.primary, RoundedCornerShape(18.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(colors.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.AttachMoney,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Saldo RuBank",
                                    color = colors.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Atual: ${accountBalance.toBrl()}",
                                    color = colors.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        ErrorText(state.error)
                        Spacer(Modifier.height(76.dp))
                    }

                    FloatingActionButton(
                        onClick = {
                            if (parsedAmount == null) {
                                viewModel.submit()
                            } else {
                                step = 3
                            }
                        },
                        shape = CircleShape,
                        containerColor = if (parsedAmount != null) colors.primary else colors.surface,
                        contentColor = if (parsedAmount != null) Color.White else colors.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(24.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Revisar")
                    }
                }
            }

            // =========================================================================
            // STEP 3 — Figma Tela 21: "Você vai enviar" (Exact Review & Send Screen)
            // =========================================================================
            else -> {
                val parsedAmount = parseAmountInput(state.amount) ?: "0.00"
                var editingMessage by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    ScreenColumn(androidx.compose.foundation.layout.PaddingValues()) {
                        Text(
                            text = "Você vai enviar",
                            color = colors.onBackground,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold
                        )

                        // Editable Amount Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { step = 2 }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = parsedAmount.toBrl(),
                                    color = colors.primary,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "Saldo RuBank",
                                    color = colors.primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            IconButton(onClick = { step = 2 }) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Editar valor",
                                    tint = colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Small downward arrow separator
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "↓",
                                color = colors.onSurfaceVariant,
                                fontSize = 16.sp
                            )
                        }

                        // Editable Recipient Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { step = 1 }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = recipientDisplayName.uppercase(),
                                    color = colors.primary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "RUBANK S.A. • CONTA ${state.recipient?.accountNumber.orEmpty()}",
                                    color = colors.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                            IconButton(onClick = { step = 1 }) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Editar destinatário",
                                    tint = colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Expandable "Detalhes v" row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDetails = !showDetails }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Detalhes",
                                color = colors.onSurfaceVariant,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant
                            )
                        }

                        if (showDetails) {
                            Text(
                                text = "Chave Pix: ${state.pixKey} • Agência ${state.recipient?.agencyNumber ?: "0001"} • Conta ${state.recipient?.accountNumber.orEmpty()}",
                                color = colors.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }

                        HorizontalDivider(color = colors.outline)

                        // "Quando: Agora, sem repetir"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Quando", color = colors.onSurfaceVariant, fontSize = 13.sp)
                                Text(
                                    "Agora, sem repetir",
                                    color = colors.onBackground,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                            }
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // "Via: Pix"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Via", color = colors.onSurfaceVariant, fontSize = 13.sp)
                                Text(
                                    "Pix",
                                    color = colors.onBackground,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                            }
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // "Mensagem +"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editingMessage = !editingMessage }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Mensagem", color = colors.onSurfaceVariant, fontSize = 13.sp)
                                if (state.description.isNotBlank()) {
                                    Text(
                                        state.description,
                                        color = colors.onBackground,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Adicionar mensagem",
                                tint = colors.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        if (editingMessage) {
                            OutlinedTextField(
                                value = state.description,
                                onValueChange = viewModel::onDescriptionChange,
                                placeholder = { Text("Escreva uma mensagem...", color = colors.onSurfaceVariant) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = colors.onBackground,
                                    unfocusedTextColor = colors.onBackground,
                                    focusedBorderColor = colors.primary,
                                    unfocusedBorderColor = colors.outline
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        ErrorText(state.error)
                        Spacer(Modifier.height(90.dp))
                    }

                    // Bottom bar: "R$ X,XX / Valor Total" on left + "Enviar" pill button on right
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(colors.background)
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = parsedAmount.toBrl(),
                                color = colors.onBackground,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Valor Total",
                                color = colors.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }

                        Button(
                            onClick = viewModel::submit,
                            enabled = !state.isLoading,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text(
                                text = if (state.isLoading) "Enviando…" else "Enviar",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(horizontal = 18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptCard(receipt: TransferReceipt) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = RuGreen,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Transferência Pix enviada!",
                    style = MaterialTheme.typography.titleMedium,
                    color = RuGreen,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = receipt.amount.toBrl(),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.ExtraBold
            )
            HorizontalDivider(color = colors.outline)
            Text("Para: ${receipt.receiver.userName}", color = colors.onSurface, fontWeight = FontWeight.SemiBold)
            Text("Conta destino: ${receipt.receiver.accountNumber}", color = colors.onSurfaceVariant)
            Text("Status: ${receipt.status}", color = colors.onSurfaceVariant)
            receipt.endToEndId?.let {
                Text("ID E2E: $it", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}
