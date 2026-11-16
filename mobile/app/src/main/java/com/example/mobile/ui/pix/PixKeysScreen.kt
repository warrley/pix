package com.example.mobile.ui.pix

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mobile.model.AccountResponse
import com.example.mobile.model.PixKeyResponse
import com.example.mobile.ui.components.ErrorText
import com.example.mobile.ui.components.FormField
import com.example.mobile.ui.components.PixScaffold
import com.example.mobile.ui.components.ScreenColumn
import com.example.mobile.ui.theme.RuCoralRed
import com.example.mobile.ui.theme.RuGreen
import com.example.mobile.ui.theme.RuWarning

private fun copyToClipboard(context: Context, keyValue: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Chave Pix", keyValue))
    Toast.makeText(context, "Chave Pix copiada: $keyValue", Toast.LENGTH_SHORT).show()
}

private fun PixKeyType.icon(): ImageVector = when (this) {
    PixKeyType.CPF, PixKeyType.CNPJ -> Icons.Default.Person
    PixKeyType.EMAIL -> Icons.Default.Email
    PixKeyType.PHONE -> Icons.Default.Phone
    PixKeyType.RANDOM -> Icons.Default.Shield
}

/**
 * Nubank-style "Minhas chaves" screen:
 * - Title: "Minhas chaves"
 * - Subtitle: "Gerencie suas chaves para receber transferências pelo Pix."
 * - "Registrar chave   +" row that opens a ModalBottomSheet from the bottom with the key types.
 * - List of registered active keys (tap to copy to clipboard, trash icon to remove).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixKeysScreen(
    accountId: Long,
    otherAccounts: List<AccountResponse> = emptyList(),
    onBack: () -> Unit
) {
    val viewModel: PixKeysViewModel = viewModel(
        key = "pix-keys-$accountId",
        factory = PixKeysViewModel.factory(accountId)
    )
    val state by viewModel.uiState.collectAsState()
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    var showTypeSheet by remember { mutableStateOf(false) }
    var showValueInputInSheet by remember { mutableStateOf(false) }
    var managingKey by remember { mutableStateOf<PixKeyResponse?>(null) }
    var showReassociateList by remember { mutableStateOf(false) }

    PixScaffold(title = "", onBack = onBack) { padding ->
        ScreenColumn(padding) {
            Text(
                text = "Minhas chaves",
                color = colors.onBackground,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                text = "Gerencie suas chaves para receber transferências pelo Pix.",
                color = colors.onSurfaceVariant,
                fontSize = 15.sp,
                lineHeight = 20.sp
            )

            // "Registrar chave   +" action row (opens ModalBottomSheet from bottom)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .clickable {
                        showValueInputInSheet = false
                        showTypeSheet = true
                    }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Registrar chave",
                    color = colors.primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Registrar chave",
                    tint = colors.primary
                )
            }

            state.message?.let {
                Text(it, color = RuGreen, style = MaterialTheme.typography.bodyMedium)
            }
            ErrorText(state.error)

            HorizontalDivider(color = colors.outline)

            Text(
                text = "${state.keys.size} de 5 chaves",
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )

            if (!state.isLoading && state.keys.isEmpty()) {
                Text(
                    text = "Você ainda não tem chaves registradas nesta conta. Toque em \"Registrar chave +\" acima para criar uma.",
                    color = colors.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }

            state.keys.forEach { key ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { copyToClipboard(context, key.keyValue) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
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
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(colors.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Key,
                                    contentDescription = null,
                                    tint = colors.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = key.keyType.uppercase(),
                                        color = colors.onSurface,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val isSuspended = key.status == "suspended"
                                    val badgeColor = if (isSuspended) RuWarning else RuGreen
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(badgeColor.copy(alpha = 0.14f))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isSuspended) "Suspensa" else "Ativa",
                                            color = badgeColor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    text = key.keyValue,
                                    color = colors.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            }
                        }
                        Row {
                            IconButton(onClick = { copyToClipboard(context, key.keyValue) }) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copiar chave",
                                    tint = colors.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    managingKey = key
                                    showReassociateList = false
                                }
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "Gerenciar chave",
                                    tint = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Bottom Sheet coming from the bottom when "Registrar chave +" is tapped
    if (showTypeSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showTypeSheet = false
                showValueInputInSheet = false
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!showValueInputInSheet) {
                    Text(
                        text = "Registrar chave",
                        color = colors.onSurface,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Escolha o tipo de chave que você quer registrar nesta conta:",
                        color = colors.onSurfaceVariant,
                        fontSize = 14.sp
                    )

                    Spacer(Modifier.height(4.dp))

                    PixKeyType.entries.forEach { type ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    viewModel.onTypeChange(type)
                                    if (type.needsValue) {
                                        showValueInputInSheet = true
                                    } else {
                                        viewModel.create()
                                        showTypeSheet = false
                                    }
                                }
                                .padding(vertical = 14.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Icon(
                                    imageVector = type.icon(),
                                    contentDescription = null,
                                    tint = colors.onSurface
                                )
                                Text(
                                    text = type.label,
                                    color = colors.onSurface,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant
                            )
                        }
                        HorizontalDivider(color = colors.outline)
                    }
                    Spacer(Modifier.height(16.dp))
                } else {
                    Text(
                        text = "Registrar ${state.type.label}",
                        color = colors.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    FormField(
                        value = state.value,
                        onValueChange = viewModel::onValueChange,
                        label = state.type.label,
                        enabled = !state.isBusy
                    )
                    ErrorText(state.error)

                    Button(
                        onClick = {
                            if (state.value.isNotBlank()) {
                                viewModel.create()
                                showTypeSheet = false
                                showValueInputInSheet = false
                            } else {
                                viewModel.create()
                            }
                        },
                        enabled = !state.isBusy,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = if (state.isBusy) "Registrando…" else "Confirmar e registrar",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    TextButton(
                        onClick = { showValueInputInSheet = false },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Voltar aos tipos de chave", color = colors.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    managingKey?.let { key ->
        val activeOtherAccounts = otherAccounts.filter { it.isActive && it.id != accountId }
        ModalBottomSheet(
            onDismissRequest = {
                managingKey = null
                showReassociateList = false
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (showReassociateList) "Trocar conta associada" else "Gerenciar chave Pix",
                    color = colors.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "${key.keyType.uppercase()} • ${key.keyValue}",
                    color = colors.onSurfaceVariant,
                    fontSize = 13.sp
                )
                HorizontalDivider(color = colors.outline)

                if (!showReassociateList) {
                    KeyActionRow(
                        icon = Icons.Default.ContentCopy,
                        label = "Copiar chave",
                        tint = colors.onSurface,
                        onClick = {
                            copyToClipboard(context, key.keyValue)
                            managingKey = null
                        }
                    )

                    if (key.status == "active") {
                        KeyActionRow(
                            icon = Icons.Default.PauseCircleOutline,
                            label = "Suspender chave",
                            tint = RuWarning,
                            onClick = {
                                managingKey = null
                                viewModel.suspend(key)
                            }
                        )
                    } else if (key.status == "suspended") {
                        KeyActionRow(
                            icon = Icons.Default.CheckCircle,
                            label = "Desuspender / reativar chave",
                            tint = RuGreen,
                            onClick = {
                                managingKey = null
                                viewModel.unblock(key)
                            }
                        )
                    }

                    if (key.status == "active" && activeOtherAccounts.isNotEmpty()) {
                        KeyActionRow(
                            icon = Icons.Default.SwapHoriz,
                            label = "Trocar conta associada",
                            tint = colors.primary,
                            onClick = { showReassociateList = true }
                        )
                    }

                    KeyActionRow(
                        icon = Icons.Default.DeleteOutline,
                        label = "Excluir chave",
                        tint = RuCoralRed,
                        onClick = {
                            managingKey = null
                            viewModel.delete(key)
                        }
                    )
                } else {
                    Text(
                        text = "Escolha a nova conta de destino para esta chave:",
                        color = colors.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    activeOtherAccounts.forEach { acc ->
                        val label = "Ag. ${acc.agencyNumber} • Conta ${acc.accountNumber}"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.background)
                                .clickable {
                                    managingKey = null
                                    showReassociateList = false
                                    viewModel.reassociate(key, acc.id, label)
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = colors.primary)
                            Text(
                                text = label,
                                color = colors.onBackground,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                    TextButton(
                        onClick = { showReassociateList = false },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Voltar", color = colors.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun KeyActionRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
        Text(
            text = label,
            color = tint,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
