package com.example.mobile.ui.pix

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mobile.model.ScheduledTransferResponse
import com.example.mobile.ui.components.ErrorText
import com.example.mobile.ui.components.FormField
import com.example.mobile.ui.components.LoadingBox
import com.example.mobile.ui.components.PixScaffold
import com.example.mobile.ui.components.ScreenColumn
import com.example.mobile.ui.theme.RuBlue
import com.example.mobile.ui.theme.RuCoralRed
import com.example.mobile.ui.theme.RuGreen
import com.example.mobile.ui.theme.RuWarning
import com.example.mobile.util.formatIsoDateToBr
import com.example.mobile.util.isoDateToUtcMillis
import com.example.mobile.util.toBrl
import com.example.mobile.util.todayIsoDate
import com.example.mobile.util.utcMillisToIsoDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledTransfersScreen(
    accountId: Long,
    accountBalance: String,
    onBack: () -> Unit
) {
    val viewModel: ScheduledTransfersViewModel = viewModel(
        key = "scheduled-transfers-$accountId",
        factory = ScheduledTransfersViewModel.factory(accountId)
    )
    val state by viewModel.uiState.collectAsState()
    val colors = MaterialTheme.colorScheme

    var showCreateSheet by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var pendingCancel by remember { mutableStateOf<ScheduledTransferResponse?>(null) }

    PixScaffold(title = "", onBack = onBack) { padding ->
        if (state.isLoading && state.items.isEmpty()) {
            LoadingBox()
            return@PixScaffold
        }

        ScreenColumn(padding) {
            Text(
                text = "Pix agendado",
                color = colors.onBackground,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Programe transferências para datas futuras ou cancele agendamentos pendentes. Saldo atual: ${accountBalance.toBrl()}.",
                color = colors.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            // Action button to create a new scheduled transfer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .clickable {
                        viewModel.resetForm()
                        showCreateSheet = true
                    }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = colors.primary
                    )
                    Text(
                        text = "Agendar novo Pix",
                        color = colors.primary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Agendar novo Pix",
                    tint = colors.primary
                )
            }

            state.message?.let {
                Text(it, color = RuGreen, style = MaterialTheme.typography.bodyMedium)
            }
            ErrorText(state.error)

            // Status Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScheduledFilter.entries.forEach { filter ->
                    val selected = state.filter == filter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) colors.primary else colors.surface)
                            .border(
                                1.dp,
                                if (selected) colors.primary else colors.outline,
                                RoundedCornerShape(50)
                            )
                            .clickable { viewModel.setFilter(filter) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = filter.label,
                            color = if (selected) Color.White else colors.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            HorizontalDivider(color = colors.outline)

            val visibleItems = state.filteredItems
            if (visibleItems.isEmpty()) {
                Text(
                    text = if (state.items.isEmpty()) {
                        "Você não possui transferências Pix agendadas nesta conta."
                    } else {
                        "Nenhum agendamento encontrado para este filtro."
                    },
                    color = colors.onSurfaceVariant,
                    fontSize = 14.sp
                )
            } else {
                visibleItems.forEach { item ->
                    ScheduledTransferCard(
                        item = item,
                        isCancelling = state.cancellingId == item.id,
                        onCancelClick = { pendingCancel = item }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showCreateSheet) {
        ModalBottomSheet(
            onDismissRequest = { if (!state.isSubmitting) showCreateSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Agendar transferência Pix",
                    color = colors.onSurface,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "O saldo não é bloqueado agora; ele será debitado na data escolhida.",
                    color = colors.onSurfaceVariant,
                    fontSize = 13.sp
                )

                FormField(
                    value = state.pixKey,
                    onValueChange = viewModel::onPixKeyChange,
                    label = "Chave Pix do destinatário",
                    placeholder = "CPF, CNPJ, e-mail, celular ou chave aleatória",
                    enabled = !state.isSubmitting && !state.isLookingUp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.verifiedRecipient != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = RuGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Para: ${state.verifiedRecipient?.recipientName}",
                                color = RuGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }

                    TextButton(
                        onClick = viewModel::lookupRecipient,
                        enabled = state.pixKey.isNotBlank() && !state.isLookingUp && !state.isSubmitting
                    ) {
                        Text(
                            text = if (state.isLookingUp) "Verificando…" else "Verificar chave",
                            color = colors.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                FormField(
                    value = state.amount,
                    onValueChange = viewModel::onAmountChange,
                    label = "Valor (R$)",
                    placeholder = "Ex.: 100,00",
                    keyboardType = KeyboardType.Decimal,
                    enabled = !state.isSubmitting
                )

                // Future date selector row
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Data do pagamento",
                        color = colors.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.background)
                            .border(1.dp, colors.outline, RoundedCornerShape(14.dp))
                            .clickable(enabled = !state.isSubmitting) { showDatePicker = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = formatIsoDateToBr(state.scheduledFor),
                                color = colors.onBackground,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Toque para alterar a data",
                                color = colors.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Selecionar data",
                            tint = colors.primary
                        )
                    }
                }

                FormField(
                    value = state.description,
                    onValueChange = viewModel::onDescriptionChange,
                    label = "Descrição (opcional)",
                    placeholder = "Ex.: Aluguel, Mensalidade...",
                    enabled = !state.isSubmitting
                )

                ErrorText(state.formError)

                Button(
                    onClick = {
                        viewModel.createScheduledTransfer {
                            showCreateSheet = false
                        }
                    },
                    enabled = !state.isSubmitting && !state.isLookingUp,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = "Confirmar agendamento",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showDatePicker) {
        val todayIso = remember { todayIsoDate() }
        val initialMillis = remember(state.scheduledFor) {
            isoDateToUtcMillis(state.scheduledFor)
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcMillisToIsoDate(utcTimeMillis) > todayIso
                }
            }
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            viewModel.onScheduledForChange(utcMillisToIsoDate(millis))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Confirmar", color = colors.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancelar", color = colors.onSurfaceVariant)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    pendingCancel?.let { target ->
        AlertDialog(
            containerColor = colors.surface,
            titleContentColor = colors.onSurface,
            textContentColor = colors.onSurfaceVariant,
            onDismissRequest = { pendingCancel = null },
            title = { Text("Cancelar agendamento?") },
            text = {
                Text(
                    "O Pix de ${target.amount.toBrl()} para ${target.pixKeyUsed} agendado para ${formatIsoDateToBr(target.scheduledFor)} não será mais executado."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingCancel = null
                        viewModel.cancel(target)
                    }
                ) {
                    Text("Cancelar Pix", color = RuCoralRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCancel = null }) {
                    Text("Manter", color = colors.onSurface)
                }
            }
        )
    }
}

@Composable
private fun ScheduledTransferCard(
    item: ScheduledTransferResponse,
    isCancelling: Boolean,
    onCancelClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val (statusLabel, statusColor) = when (item.status) {
        "pending" -> "Agendado" to RuBlue
        "processing" -> "Processando" to RuWarning
        "executed" -> "Executado" to RuGreen
        "cancelled" -> "Cancelado" to colors.onSurfaceVariant
        "failed" -> "Falhou" to RuCoralRed
        else -> item.status to colors.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = formatIsoDateToBr(item.scheduledFor),
                        color = colors.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(statusColor.copy(alpha = 0.14f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = item.amount.toBrl(),
                color = colors.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                text = "Chave: ${item.pixKeyUsed}",
                color = colors.onSurfaceVariant,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            item.description?.takeIf { it.isNotBlank() }?.let { desc ->
                Text(
                    text = desc,
                    color = colors.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            item.failureReason?.takeIf { it.isNotBlank() }?.let { reason ->
                Text(
                    text = "Motivo da falha: $reason",
                    color = RuCoralRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            if (item.status == "pending") {
                OutlinedButton(
                    onClick = onCancelClick,
                    enabled = !isCancelling,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isCancelling) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = RuCoralRed
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = null,
                            tint = RuCoralRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Cancelar agendamento",
                            color = RuCoralRed,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
