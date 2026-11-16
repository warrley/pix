package com.example.mobile.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mobile.ui.components.ErrorText
import com.example.mobile.ui.components.FormField
import com.example.mobile.ui.components.RuBankBrandBadge
import com.example.mobile.ui.theme.RuRubyDark
import com.example.mobile.ui.theme.RuRubyRed

@Composable
fun AuthScreen(viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory)) {
    val state by viewModel.uiState.collectAsState()
    val expired by viewModel.sessionExpired.collectAsState()
    val signUp = state.mode == AuthMode.SIGN_UP
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
    ) {
        // Top Brand Hero Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(RuRubyRed, RuRubyDark)
                    )
                )
                .padding(horizontal = 24.dp, vertical = 48.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RuBankBrandBadge(gemSize = 44.dp, fontSizeSp = 28)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (signUp) "Abra sua conta digital RuBank" else "Um mundo financeiro sem complexidades",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (signUp) "Preencha seus dados para começar a usar o Pix instantâneo." else "Entre com seu e-mail e senha para acessar sua conta.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (signUp) "Criar conta" else "Entrar na conta",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold
                )

                if (expired && !signUp) {
                    Text(
                        text = "Sua sessão expirou. Entre novamente.",
                        color = colors.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (signUp) {
                    FormField(
                        value = state.name,
                        onValueChange = viewModel::onNameChange,
                        label = "Nome completo",
                        enabled = !state.isLoading
                    )
                    FormField(
                        value = state.docId,
                        onValueChange = viewModel::onDocIdChange,
                        label = "CPF (11 dígitos) ou CNPJ (14 dígitos)",
                        keyboardType = KeyboardType.Number,
                        enabled = !state.isLoading
                    )
                    FormField(
                        value = state.phone,
                        onValueChange = viewModel::onPhoneChange,
                        label = "Telefone com DDD (opcional)",
                        keyboardType = KeyboardType.Phone,
                        enabled = !state.isLoading
                    )
                }

                FormField(
                    value = state.email,
                    onValueChange = viewModel::onEmailChange,
                    label = "E-mail",
                    keyboardType = KeyboardType.Email,
                    enabled = !state.isLoading
                )
                FormField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = if (signUp) "Senha (mínimo 12 caracteres)" else "Senha",
                    isPassword = true,
                    enabled = !state.isLoading
                )

                ErrorText(state.error)

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = viewModel::submit,
                    enabled = !state.isLoading,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = if (signUp) "Cadastrar e entrar" else "Continuar",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                TextButton(
                    onClick = viewModel::switchMode,
                    enabled = !state.isLoading,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = if (signUp) "Já tenho uma conta • Entrar" else "Ainda não é cliente? Criar uma conta",
                        color = colors.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
