package com.example.mobile.ui.pix

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.mobile.ui.components.PixScaffold
import com.example.mobile.ui.components.ScreenColumn

/**
 * Original Nubank "Área Pix" (Figma Tela 18 — leftmost original flow),
 * showing only the real implemented features (no Pix no crédito, Depositar, Pix por áudio, QR code, or Cobrar).
 */
@Composable
fun PixHubScreen(
    onBack: () -> Unit,
    onOpenTransfer: (initialKey: String, initialAmount: String) -> Unit,
    onOpenPixKeys: () -> Unit
) {
    var smartInput by remember { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme

    fun submitSmartInput() {
        val parts = smartInput.trim().split(Regex("\\s+"))
        val maybeAmount = parts.lastOrNull()?.takeIf { it.matches(Regex("\\d+([.,]\\d{1,2})?")) && parts.size > 1 }
        val key = if (maybeAmount != null) parts.dropLast(1).joinToString(" ") else smartInput.trim()
        onOpenTransfer(key, maybeAmount.orEmpty())
    }

    PixScaffold(
        title = "",
        onBack = onBack,
        useCloseIcon = true,
        showHelpIcon = true
    ) { padding ->
        ScreenColumn(padding) {
            Text(
                text = "Área Pix",
                color = colors.onBackground,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Envie e receba pagamentos a qualquer hora e dia da semana, sem pagar nada por isso.",
                color = colors.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            // "Digite tudo: chave e valor" pill bar (Figma Tela 18)
            OutlinedTextField(
                value = smartInput,
                onValueChange = { smartInput = it },
                placeholder = {
                    Text(
                        "Digite tudo: chave e valor",
                        color = colors.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.onSurface,
                    unfocusedTextColor = colors.onSurface,
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface,
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.primary.copy(alpha = 0.65f),
                    cursorColor = colors.primary
                ),
                trailingIcon = {
                    IconButton(onClick = ::submitSmartInput) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Continuar",
                            tint = colors.primary
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(4.dp))

            // Clean row with only the real implemented actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PixGridButton(
                    icon = Icons.AutoMirrored.Filled.CompareArrows,
                    label = "Transferir",
                    onClick = { onOpenTransfer("", "") }
                )
                PixGridButton(
                    icon = Icons.Default.CalendarToday,
                    label = "Programar",
                    onClick = { onOpenTransfer("", "") }
                )
                PixGridButton(
                    icon = Icons.Default.ContentCopy,
                    label = "Pix Copia\ne Cola",
                    onClick = { onOpenTransfer("", "") }
                )
                PixGridButton(
                    icon = Icons.Default.Key,
                    label = "Minhas\nchaves",
                    onClick = onOpenPixKeys
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = colors.outline)

            Text(
                text = "Preferências",
                color = colors.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .clickable(onClick = onOpenPixKeys)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = colors.onSurface)
                    Column {
                        Text(
                            text = "Registrar ou trazer chaves",
                            color = colors.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Cadastre CPF, CNPJ, e-mail, celular ou chave aleatória",
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
        }
    }
}

@Composable
private fun PixGridButton(
    icon: ImageVector,
    label: String,
    badge: String? = null,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .padding(vertical = 4.dp)
    ) {
        Box(contentAlignment = Alignment.BottomCenter) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .border(1.dp, colors.outline, CircleShape)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = colors.onSurface,
                    modifier = Modifier.size(24.dp)
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
            lineHeight = 14.sp
        )
    }
}
