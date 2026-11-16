package com.example.mobile.ui.summary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mobile.ui.components.PixScaffold
import com.example.mobile.ui.components.RubyGemIcon
import com.example.mobile.ui.components.ScreenColumn
import com.example.mobile.ui.home.HomeViewModel
import com.example.mobile.ui.theme.RuBlue
import com.example.mobile.ui.theme.RuCoralRed
import com.example.mobile.ui.theme.RuGreen
import com.example.mobile.ui.theme.RuWarning
import com.example.mobile.util.toBrl
import java.math.BigDecimal

/**
 * Resumo Financeiro Mensal — Pure statistics and charts (no chat interface):
 * - KPI Summary cards (Entradas, Saídas, Saldo em Conta, Total de Transações)
 * - Comparative stacked column chart (Saldo, Saídas, Entradas)
 * - Speedometer financial health gauge arc + transaction breakdown
 */
@Composable
fun FinancialSummaryScreen(
    accountId: Long,
    homeViewModel: HomeViewModel,
    onBack: () -> Unit
) {
    val homeState by homeViewModel.uiState.collectAsState()
    val colors = MaterialTheme.colorScheme
    val account = homeState.accounts.firstOrNull { it.id == accountId } ?: homeState.selectedAccount

    val credits = homeState.statement
        .filter { !it.isDebit }
        .fold(BigDecimal.ZERO) { acc, tx -> acc + (tx.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO) }

    val debits = homeState.statement
        .filter { it.isDebit }
        .fold(BigDecimal.ZERO) { acc, tx -> acc + (tx.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO) }

    val balance = account?.balanceAmount ?: BigDecimal.ZERO
    val totalFlow = (balance + credits + debits).coerceAtLeast(BigDecimal.ONE)

    val balanceFraction = (balance.toFloat() / totalFlow.toFloat()).coerceIn(0.08f, 0.70f)
    val debitFraction = (debits.toFloat() / totalFlow.toFloat()).coerceIn(0.08f, 0.70f)
    val creditFraction = (credits.toFloat() / totalFlow.toFloat()).coerceIn(0.08f, 0.70f)

    val incomingCount = homeState.statement.count { !it.isDebit }
    val outgoingCount = homeState.statement.count { it.isDebit }

    PixScaffold(title = "Resumo Financeiro", onBack = onBack) { padding ->
        ScreenColumn(padding) {
            // Top Header Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RubyGemIcon(size = 36.dp)
                Column {
                    Text(
                        text = "Estatísticas da Conta ${account?.accountNumber.orEmpty()}",
                        color = colors.onBackground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Análise consolidada de entradas, saídas e saldo",
                        color = colors.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }

            // KPI Cards Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KpiStatCard(
                    title = "Entradas ($incomingCount)",
                    value = credits.toBrl(),
                    accentColor = RuGreen,
                    modifier = Modifier.weight(1f)
                )
                KpiStatCard(
                    title = "Saídas ($outgoingCount)",
                    value = debits.toBrl(),
                    accentColor = RuCoralRed,
                    modifier = Modifier.weight(1f)
                )
            }

            // Comparative Bar Chart Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Distribuição Financeira",
                        color = colors.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            LegendMetric(
                                color = RuGreen,
                                label = "Saldo atual",
                                value = balance.toBrl()
                            )
                            LegendMetric(
                                color = RuCoralRed,
                                label = "Total de saídas",
                                value = debits.toBrl()
                            )
                            LegendMetric(
                                color = RuBlue,
                                label = "Total de entradas",
                                value = credits.toBrl()
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = (balance + credits).toBrl(),
                                color = colors.onSurface,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Column(
                                modifier = Modifier
                                    .width(38.dp)
                                    .weight(1f)
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(balanceFraction)
                                        .background(RuGreen)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(debitFraction)
                                        .background(RuCoralRed)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(creditFraction)
                                        .background(RuBlue)
                                )
                            }
                            Text(
                                text = "R$ 0",
                                color = colors.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Speedometer Gauge Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Termômetro de Movimentações",
                        color = colors.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        CategoryDot(color = RuGreen, label = "Saldo")
                        CategoryDot(color = RuCoralRed, label = "Saídas Pix")
                        CategoryDot(color = RuWarning, label = "Entradas Pix")
                    }

                    Spacer(Modifier.height(4.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Canvas(modifier = Modifier.size(200.dp, 110.dp)) {
                            val strokeWidth = 28.dp.toPx()
                            val arcSize = Size(size.width - strokeWidth, (size.height - strokeWidth / 2) * 1.8f)
                            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                            drawArc(
                                color = RuGreen,
                                startAngle = 180f,
                                sweepAngle = 60f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                            )
                            drawArc(
                                color = RuCoralRed,
                                startAngle = 240f,
                                sweepAngle = 60f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                            )
                            drawArc(
                                color = RuWarning,
                                startAngle = 300f,
                                sweepAngle = 60f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${homeState.statement.size} movimentações",
                                color = colors.onSurface,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Saldo líquido: ${balance.toBrl()}",
                                color = colors.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (homeState.statement.isNotEmpty()) {
                        HorizontalDivider(color = colors.outline)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Maiores movimentações recentes",
                                color = colors.onSurfaceVariant,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            homeState.statement.take(4).forEach { tx ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = tx.counterparty.name ?: "Transferência Pix",
                                        color = colors.onSurface,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = (if (tx.isDebit) "- " else "+ ") + tx.amount.toBrl(),
                                        color = if (tx.isDebit) RuCoralRed else RuGreen,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiStatCard(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                color = colors.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                color = accentColor,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun LegendMetric(
    color: Color,
    label: String,
    value: String
) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Column {
            Text(
                text = label,
                color = colors.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                color = colors.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun CategoryDot(color: Color, label: String) {
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            color = colors.onSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
