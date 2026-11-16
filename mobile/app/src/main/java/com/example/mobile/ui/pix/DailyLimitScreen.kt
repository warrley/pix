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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mobile.ui.components.ErrorText
import com.example.mobile.ui.components.FormField
import com.example.mobile.ui.components.LoadingBox
import com.example.mobile.ui.components.PixScaffold
import com.example.mobile.ui.components.ScreenColumn
import com.example.mobile.ui.theme.RuGreen
import com.example.mobile.util.toBrl
import java.math.BigDecimal

@Composable
fun DailyLimitScreen(
    accountId: Long,
    onBack: () -> Unit
) {
    val viewModel: DailyLimitViewModel = viewModel(
        key = "daily-limit-$accountId",
        factory = DailyLimitViewModel.factory(accountId)
    )
    val state by viewModel.uiState.collectAsState()
    val colors = MaterialTheme.colorScheme

    PixScaffold(title = "", onBack = onBack) { padding ->
        if (state.isLoading && state.limit == null) {
            LoadingBox()
            return@PixScaffold
        }

        ScreenColumn(padding) {
            Text(
                text = "Meu limite Pix",
                color = colors.onBackground,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Gerencie o valor máximo que você pode transferir por dia nesta conta.",
                color = colors.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            state.limit?.let { limit ->
                val totalDec = limit.dailyLimit.toBigDecimalOrNull() ?: BigDecimal.ZERO
                val usedDec = limit.usedToday.toBigDecimalOrNull() ?: BigDecimal.ZERO
                val progress = if (totalDec.signum() > 0) {
                    (usedDec.toFloat() / totalDec.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Limite diário atual",
                            color = colors.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = limit.dailyLimit.toBrl(),
                            color = colors.onSurface,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold
                        )

                        LinearProgressIndicator(
                            progress = { progress },
                            color = colors.primary,
                            trackColor = colors.outline,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(50))
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Usado hoje", color = colors.onSurfaceVariant, fontSize = 12.sp)
                                Text(
                                    text = limit.usedToday.toBrl(),
                                    color = colors.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Disponível hoje", color = colors.onSurfaceVariant, fontSize = 12.sp)
                                Text(
                                    text = limit.remaining.toBrl(),
                                    color = RuGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = colors.outline)

            Text(
                text = "Ajustar limite diário",
                color = colors.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "500,00" to "R$ 500",
                    "1000,00" to "R$ 1.000",
                    "2500,00" to "R$ 2.500",
                    "5000,00" to "R$ 5.000",
                    "10000,00" to "R$ 10.000"
                ).forEach { (raw, label) ->
                    val selected = state.limitInput.replace('.', ',') == raw
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) colors.primary else colors.surface)
                            .border(
                                1.dp,
                                if (selected) colors.primary else colors.outline,
                                RoundedCornerShape(50)
                            )
                            .clickable { viewModel.applyPreset(raw) }
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
            }

            FormField(
                value = state.limitInput,
                onValueChange = viewModel::onLimitInputChange,
                label = "Novo limite diário (R$)",
                placeholder = "Ex.: 2000,00",
                keyboardType = KeyboardType.Decimal,
                enabled = !state.isSaving
            )

            state.message?.let {
                Text(it, color = RuGreen, style = MaterialTheme.typography.bodyMedium)
            }
            ErrorText(state.error)

            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text(
                        text = "Salvar limite",
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
