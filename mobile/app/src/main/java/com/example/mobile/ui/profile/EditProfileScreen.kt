package com.example.mobile.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mobile.ui.components.ErrorText
import com.example.mobile.ui.components.FormField
import com.example.mobile.ui.components.PixScaffold
import com.example.mobile.ui.components.ProfileAvatar
import com.example.mobile.ui.components.ScreenColumn
import com.example.mobile.ui.theme.RuCoralRed
import com.example.mobile.ui.theme.RuGreen

@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    viewModel: EditProfileViewModel = viewModel(factory = EditProfileViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsState()
    val colors = MaterialTheme.colorScheme

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onPhotoPicked(uri.toString())
        }
    }

    PixScaffold(title = "", onBack = onBack) { padding ->
        ScreenColumn(padding) {
            Text(
                text = "Editar perfil",
                color = colors.onBackground,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Atualize sua foto e seus dados de contato no RuBank.",
                color = colors.onSurfaceVariant,
                fontSize = 14.sp
            )

            // Avatar section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProfileAvatar(
                    photoPath = state.photoPath,
                    photoVersion = state.photoVersion,
                    size = 96.dp,
                    iconSize = 46.dp,
                    backgroundColor = colors.primary.copy(alpha = 0.14f),
                    iconTint = colors.primary,
                    contentDescription = "Foto de perfil"
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !state.isUpdatingPhoto && !state.isSaving,
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (state.photoPath != null) "Trocar foto" else "Escolher foto",
                            color = colors.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (state.photoPath != null) {
                        TextButton(
                            onClick = viewModel::removePhoto,
                            enabled = !state.isUpdatingPhoto && !state.isSaving
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = RuCoralRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Remover", color = RuCoralRed, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Text(
                    text = "A foto de perfil fica salva localmente neste aparelho.",
                    color = colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }

            HorizontalDivider(color = colors.outline)

            FormField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = "Nome completo",
                enabled = !state.isSaving
            )

            FormField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = "E-mail",
                keyboardType = KeyboardType.Email,
                enabled = !state.isSaving
            )

            FormField(
                value = state.phone,
                onValueChange = viewModel::onPhoneChange,
                label = "Telefone",
                placeholder = "(11) 99999-9999",
                keyboardType = KeyboardType.Phone,
                enabled = !state.isSaving
            )

            state.message?.let {
                Text(it, color = RuGreen, style = MaterialTheme.typography.bodyMedium)
            }
            ErrorText(state.error)

            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving && !state.isUpdatingPhoto,
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
                        text = "Salvar alterações",
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
