package com.example.mobile.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mobile.model.StatementEntry
import com.example.mobile.ui.components.ErrorText
import com.example.mobile.ui.components.FormField
import com.example.mobile.ui.components.LoadingBox
import com.example.mobile.ui.components.PixScaffold
import com.example.mobile.ui.components.ScreenColumn
import com.example.mobile.ui.theme.RuCoralRed
import com.example.mobile.ui.theme.RuGreen
import com.example.mobile.ui.theme.RuWarning
import com.example.mobile.util.formatIsoDateToBr
import com.example.mobile.util.formatIsoTimestampToBr
import com.example.mobile.util.toBrl
import com.example.mobile.util.utcMillisToIsoDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    accountId: Long,
    accountBalance: String,
    onRefreshBalance: () -> Unit,
    onBack: () -> Unit
) {
    val viewModel: TransactionHistoryViewModel = viewModel(
        key = "history-$accountId",
        factory = TransactionHistoryViewModel.factory(accountId)
    )
    val state by viewModel.uiState.collectAsState()
    val colors = MaterialTheme.colorScheme

    var selectedEntry by remember { mutableStateOf<StatementEntry?>(null) }
    var showRefundForm by remember { mutableStateOf(false) }
    var refundReason by remember { mutableStateOf("") }
    var showDateRangePicker by remember { mutableStateOf(false) }

    PixScaffold(title = "", onBack = onBack) { padding ->
        if (state.isLoading && state.entries.isEmpty()) {
            LoadingBox()
            return@PixScaffold
        }

        ScreenColumn(padding) {
            Text(
                text = "Histórico de movimentações",
                color = colors.onBackground,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold
            )

            // Current balance summary card (Nubank style)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Saldo disponível",
                        color = colors.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = accountBalance.toBrl(),
                        color = colors.onSurface,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Search by person / CPF / key
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                placeholder = {
                    Text("Buscar por pessoa, CPF/CNPJ ou chave", color = colors.onSurfaceVariant, fontSize = 14.sp)
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = colors.onSurfaceVariant)
                },
                singleLine = true,
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.onSurface,
                    unfocusedTextColor = colors.onSurface,
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface,
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.outline
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Period filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HistoryPeriodFilter.entries.forEach { period ->
                    val selected = state.periodFilter == period
                    val label = if (period == HistoryPeriodFilter.CUSTOM &&
                        state.customStartDate != null && state.customEndDate != null
                    ) {
                        "${formatIsoDateToBr(state.customStartDate!!)} – ${formatIsoDateToBr(state.customEndDate!!)}"
                    } else {
                        period.label
                    }
                    FilterChipItem(
                        label = label,
                        selected = selected,
                        onClick = {
                            if (period == HistoryPeriodFilter.CUSTOM) {
                                showDateRangePicker = true
                            } else {
                                viewModel.setPeriodFilter(period)
                            }
                        }
                    )
                }
            }

            // Direction / status filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HistoryTypeFilter.entries.forEach { type ->
                    FilterChipItem(
                        label = type.label,
                        selected = state.typeFilter == type,
                        onClick = { viewModel.setTypeFilter(type) }
                    )
                }
            }

            state.message?.let {
                Text(it, color = RuGreen, style = MaterialTheme.typography.bodyMedium)
            }
            ErrorText(state.error)

            HorizontalDivider(color = colors.outline)

            val visible = state.filteredEntries
            if (visible.isEmpty()) {
                Text(
                    text = "Nenhuma movimentação encontrada para os filtros selecionados.",
                    color = colors.onSurfaceVariant,
                    fontSize = 14.sp
                )
            } else {
                visible.forEach { entry ->
                    HistoryTransactionRow(
                        entry = entry,
                        onClick = {
                            selectedEntry = entry
                            showRefundForm = false
                            refundReason = ""
                            viewModel.clearRefundError()
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // Transaction Details & Refund Bottom Sheet
    selectedEntry?.let { entry ->
        ModalBottomSheet(
            onDismissRequest = { if (!state.isRefunding) selectedEntry = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (entry.isDebit) "Transferência enviada" else "Transferência recebida",
                    color = colors.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = entry.amount.toBrl(),
                    color = if (entry.isDebit) RuCoralRed else RuGreen,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                HorizontalDivider(color = colors.outline)

                DetailLine("Pessoa", entry.counterparty.name ?: "Não informado")
                entry.counterparty.docId?.takeIf { it.isNotBlank() }?.let {
                    DetailLine("CPF / CNPJ", it)
                }
                DetailLine("Status", statusLabel(entry.status))
                entry.createdAt?.let {
                    DetailLine("Data", formatIsoTimestampToBr(it))
                }
                entry.pixKeyUsed?.takeIf { it.isNotBlank() }?.let {
                    DetailLine("Chave Pix", it)
                }
                entry.endToEndId?.takeIf { it.isNotBlank() }?.let {
                    DetailLine("ID E2E", it)
                }

                if (entry.status == "completed") {
                    Spacer(Modifier.height(4.dp))
                    if (!showRefundForm) {
                        OutlinedButton(
                            onClick = { showRefundForm = true },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.Replay, contentDescription = null, tint = colors.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Devolver / Estornar Pix",
                                color = colors.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Text(
                            text = "Motivo do estorno",
                            color = colors.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "Transferência duplicada",
                                "Valor incorreto",
                                "Desacordo comercial"
                            ).forEach { preset ->
                                FilterChipItem(
                                    label = preset,
                                    selected = refundReason == preset,
                                    onClick = { refundReason = preset }
                                )
                            }
                        }
                        FormField(
                            value = refundReason,
                            onValueChange = { refundReason = it },
                            label = "Motivo",
                            placeholder = "Descreva o motivo da devolução",
                            enabled = !state.isRefunding
                        )
                        ErrorText(state.refundError)
                        Button(
                            onClick = {
                                viewModel.refundTransaction(entry, refundReason) { updated ->
                                    selectedEntry = updated
                                    showRefundForm = false
                                    onRefreshBalance()
                                }
                            },
                            enabled = !state.isRefunding,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = RuCoralRed),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            if (state.isRefunding) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Text("Confirmar estorno", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
            }
        }
    }

    if (showDateRangePicker) {
        val dateRangeState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = dateRangeState.selectedStartDateMillis?.let(::utcMillisToIsoDate)
                        val end = dateRangeState.selectedEndDateMillis?.let(::utcMillisToIsoDate) ?: start
                        if (start != null && end != null) {
                            viewModel.setCustomDateRange(start, end)
                        }
                        showDateRangePicker = false
                    }
                ) {
                    Text("Aplicar", color = colors.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) {
                    Text("Cancelar", color = colors.onSurfaceVariant)
                }
            }
        ) {
            DateRangePicker(
                state = dateRangeState,
                title = {
                    Text(
                        text = "Filtrar por período",
                        modifier = Modifier.padding(16.dp),
                        fontWeight = FontWeight.Bold
                    )
                },
                modifier = Modifier.height(460.dp)
            )
        }
    }
}

@Composable
private fun FilterChipItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) colors.primary else colors.surface)
            .border(1.dp, if (selected) colors.primary else colors.outline, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else colors.onSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun HistoryTransactionRow(
    entry: StatementEntry,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
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
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (entry.isDebit) "Transferência enviada" else "Transferência recebida",
                        color = colors.onBackground,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = entry.counterparty.name ?: "Pix • ${statusLabel(entry.status)}",
                        color = colors.onSurfaceVariant,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        entry.createdAt?.let {
                            Text(
                                text = formatIsoTimestampToBr(it),
                                color = colors.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        if (entry.status != "completed") {
                            Text(
                                text = "• ${statusLabel(entry.status)}",
                                color = if (entry.status == "refunded") RuWarning else RuCoralRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
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
private fun DetailLine(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = colors.onSurfaceVariant,
            fontSize = 13.sp
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            color = colors.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

private fun statusLabel(status: String): String = when (status) {
    "completed" -> "Concluída"
    "refunded" -> "Estornada"
    "cancelled" -> "Cancelada"
    "processing" -> "Processando"
    "failed" -> "Falhou"
    else -> status
}
