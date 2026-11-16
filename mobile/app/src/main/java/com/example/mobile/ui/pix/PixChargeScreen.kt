package com.example.mobile.ui.pix

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mobile.ui.components.ErrorText
import com.example.mobile.ui.components.LoadingBox
import com.example.mobile.ui.components.PixScaffold
import com.example.mobile.ui.components.ScreenColumn
import com.example.mobile.ui.theme.RuGreen
import com.example.mobile.util.QrBitmapGenerator
import kotlinx.coroutines.delay

private fun copyQrCode(context: Context, code: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Código Pix Copia e Cola", code))
    Toast.makeText(context, "Código Pix copiado!", Toast.LENGTH_SHORT).show()
}

@Composable
fun PixChargeScreen(
    accountId: Long,
    userName: String,
    onOpenPixKeys: () -> Unit,
    onBack: () -> Unit
) {
    val viewModel: PixChargeViewModel = viewModel(
        key = "pix-charge-$accountId",
        factory = PixChargeViewModel.factory(accountId, userName)
    )
    val state by viewModel.uiState.collectAsState()
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var copiedRecently by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadKeys()
    }

    LaunchedEffect(copiedRecently) {
        if (copiedRecently) {
            delay(2500)
            copiedRecently = false
        }
    }

    PixScaffold(title = "", onBack = onBack) { padding ->
        if (state.isLoading && state.keys.isEmpty()) {
            LoadingBox()
            return@PixScaffold
        }

        Box(modifier = Modifier.imePadding()) {
            ScreenColumn(padding) {
                Text(
                    text = "Cobrar com Pix",
                    color = colors.onBackground,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Digite o valor da cobrança (ou deixe 0 para quem pagar definir) e compartilhe o QR Code ou código.",
                    color = colors.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                ErrorText(state.error)

                if (state.keys.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "Você precisa de uma chave Pix",
                                color = colors.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Para gerar um QR Code de cobrança e receber pagamentos, cadastre ao menos uma chave Pix nesta conta.",
                                color = colors.onSurfaceVariant,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                            Button(
                                onClick = viewModel::createRandomKey,
                                enabled = !state.isCreatingKey,
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                if (state.isCreatingKey) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                } else {
                                    Text(
                                        text = "Gerar chave aleatória agora",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = onOpenPixKeys,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text("Ir para Minhas chaves", color = colors.onBackground)
                            }
                        }
                    }
                    return@ScreenColumn
                }

                // Amount input (defaults to 0)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Valor da cobrança",
                        color = colors.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    TextField(
                        value = state.amountInput,
                        onValueChange = viewModel::onAmountChange,
                        placeholder = {
                            Text(
                                text = "0",
                                color = colors.onSurfaceVariant,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        },
                        prefix = {
                            Text(
                                text = "R$ ",
                                color = colors.onBackground,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        },
                        textStyle = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        ),
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
                }

                // Optional short description
                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::onDescriptionChange,
                    label = { Text("Descrição (opcional)") },
                    placeholder = { Text("Ex.: Almoço, Ingresso...", color = colors.onSurfaceVariant) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.onSurface,
                        unfocusedTextColor = colors.onSurface,
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface,
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.outline,
                        focusedLabelColor = colors.primary,
                        unfocusedLabelColor = colors.onSurfaceVariant,
                        cursorColor = colors.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Key selector if user has multiple keys
                if (state.keys.size > 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Chave de recebimento",
                            color = colors.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.keys.forEach { key ->
                                val selected = key.id == state.selectedKey?.id
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(if (selected) colors.primary else colors.surface)
                                        .border(
                                            1.dp,
                                            if (selected) colors.primary else colors.outline,
                                            RoundedCornerShape(50)
                                        )
                                        .clickable { viewModel.selectKey(key) }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "${key.keyType.uppercase()}: ${key.keyValue}",
                                        color = if (selected) Color.White else colors.onSurface,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Generated QR Code + Copyable Code Card
                val qrCodeText = state.qrCodeText
                val selectedKey = state.selectedKey
                if (qrCodeText != null && selectedKey != null) {
                    val qrBitmap = remember(qrCodeText) {
                        QrBitmapGenerator.generate(qrCodeText, sizePx = 560)
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCode2,
                                    contentDescription = null,
                                    tint = colors.primary
                                )
                                Text(
                                    text = if (state.hasFixedAmount) state.formattedAmount else "Valor em aberto (R$ 0,00)",
                                    color = colors.onSurface,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color.White)
                                    .padding(14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = qrBitmap,
                                    contentDescription = "QR Code Pix",
                                    filterQuality = FilterQuality.None,
                                    modifier = Modifier.size(210.dp)
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = state.userName.ifBlank { "Cliente RuBank" },
                                    color = colors.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Chave: ${selectedKey.keyValue}",
                                    color = colors.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            HorizontalDivider(color = colors.outline)

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Pix Copia e Cola",
                                    color = colors.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.background)
                                        .border(1.dp, colors.outline, RoundedCornerShape(12.dp))
                                        .clickable {
                                            copyQrCode(context, qrCodeText)
                                            copiedRecently = true
                                        }
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = qrCodeText,
                                        color = colors.onBackground,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    copyQrCode(context, qrCodeText)
                                    copiedRecently = true
                                },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (copiedRecently) RuGreen else colors.primary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Icon(
                                    imageVector = if (copiedRecently) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (copiedRecently) "Código copiado!" else "Copiar código",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
