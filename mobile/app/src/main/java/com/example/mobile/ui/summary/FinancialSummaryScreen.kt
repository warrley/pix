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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.mobile.ui.theme.RuCoralRed
import com.example.mobile.ui.theme.RuGreen
import com.example.mobile.util.toBrl
import java.math.BigDecimal
import kotlin.math.roundToInt

/**
 * Resumo Financeiro Mensal — Concise statistics & proportional charts with zero redundancy:
 * - Account header with current balance
 * - Entradas vs Saídas proportional breakdown bar + totals
 * - Real data-driven flow gauge (net result + transaction count)
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
    val netFlow = credits - debits
    val totalMoved = credits + debits
    val hasMovement = totalMoved > BigDecimal.ZERO

    val creditRatio = if (hasMovement) {
        (credits.toFloat() / totalMoved.toFloat()).coerceIn(0f, 1f)
    } else {
        0.5f
    }
    val debitRatio = 1f - creditRatio
    val creditPct = (creditRatio * 100f).roundToInt()
    val debitPct = 100 - creditPct

    val incomingCount = homeState.statement.count { !it.isDebit }
    val outgoingCount = homeState.statement.count { it.isDebit }

    PixScaffold(title = "Resumo Financeiro", onBack = onBack) { padding ->
        ScreenColumn(padding) {
            // Account header + current balance (shown once)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RubyGemIcon(size = 36.dp)
                Column {
                    Text(
                        text = "Conta ${account?.accountNumber.orEmpty()}",
                        color = colors.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Saldo atual: ${balance.toBrl()}",
                        color = colors.onBackground,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Card 1: Entradas vs Saídas (single place for totals & proportional bar)
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
                        text = "Entradas e saídas",
                        color = colors.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SummaryMetricColumn(
                            dotColor = RuGreen,
                            title = "Entradas ($incomingCount)",
                            amount = credits.toBrl(),
                            shareLabel = if (hasMovement) "$creditPct%" else "0%",
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMetricColumn(
                            dotColor = RuCoralRed,
                            title = "Saídas ($outgoingCount)",
                            amount = debits.toBrl(),
                            shareLabel = if (hasMovement) "$debitPct%" else "0%",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Proportional horizontal bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(50))
                            .background(colors.outline.copy(alpha = 0.35f))
                    ) {
                        if (hasMovement) {
                            if (creditRatio > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(creditRatio.coerceAtLeast(0.02f))
                                        .fillMaxHeight()
                                        .background(RuGreen)
                                )
                            }
                            if (debitRatio > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(debitRatio.coerceAtLeast(0.02f))
                                        .fillMaxHeight()
                                        .background(RuCoralRed)
                                )
                            }
                        }
                    }
                }
            }

            // Card 2: Proportional Flow Gauge (net result & transaction count)
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
                        text = "Balanço do período",
                        color = colors.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        val emptyArcColor = colors.outline
                        Canvas(modifier = Modifier.size(210.dp, 115.dp)) {
                            val strokeWidth = 24.dp.toPx()
                            val arcSize = Size(
                                size.width - strokeWidth,
                                (size.height - strokeWidth / 2) * 1.8f
                            )
                            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                            if (!hasMovement) {
                                drawArc(
                                    color = emptyArcColor,
                                    startAngle = 180f,
                                    sweepAngle = 180f,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                            } else {
                                val creditSweep = 180f * creditRatio
                                val debitSweep = 180f - creditSweep
                                if (creditSweep > 0f) {
                                    drawArc(
                                        color = RuGreen,
                                        startAngle = 180f,
                                        sweepAngle = creditSweep,
                                        useCenter = false,
                                        topLeft = topLeft,
                                        size = arcSize,
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                                    )
                                }
                                if (debitSweep > 0f) {
                                    drawArc(
                                        color = RuCoralRed,
                                        startAngle = 180f + creditSweep,
                                        sweepAngle = debitSweep,
                                        useCenter = false,
                                        topLeft = topLeft,
                                        size = arcSize,
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                                    )
                                }
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val netPrefix = if (netFlow >= BigDecimal.ZERO) "+ " else "- "
                            val netColor = if (netFlow >= BigDecimal.ZERO) RuGreen else RuCoralRed
                            Text(
                                text = netPrefix + netFlow.abs().toBrl(),
                                color = if (hasMovement) netColor else colors.onSurfaceVariant,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "${homeState.statement.size} movimentações",
                                color = colors.onSurfaceVariant,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricColumn(
    dotColor: Color,
    title: String,
    amount: String,
    shareLabel: String,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = title,
                color = colors.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = amount,
            color = colors.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "$shareLabel do fluxo",
            color = colors.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
}
