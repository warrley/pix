package com.example.mobile.ui.profile

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mobile.model.AccountResponse
import com.example.mobile.model.UserResponse
import com.example.mobile.ui.theme.NuActionCircle
import com.example.mobile.ui.theme.NuDivider
import com.example.mobile.ui.theme.NuError
import com.example.mobile.ui.theme.NuPurple
import com.example.mobile.ui.theme.NuPurpleDark
import com.example.mobile.ui.theme.NuPurpleLight
import com.example.mobile.ui.theme.NuSuccess
import com.example.mobile.ui.theme.NuTextPrimary
import com.example.mobile.ui.theme.NuTextSecondary
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileManagementSheet(
    sheetState: SheetState,
    user: UserResponse?,
    currentAccount: AccountResponse,
    userAccounts: List<AccountResponse>,
    onDismiss: () -> Unit,
    onCreateUser: (name: String, email: String, docId: String, phone: String) -> Unit,
    onUpdateUser: (name: String, email: String, phone: String) -> Unit,
    onDeleteUser: () -> Unit,
    onCreateAccount: () -> Unit,
    onDeleteAccount: () -> Unit,
    onSwitchAccount: (Long) -> Unit
) {
    var showCreateUserDialog by remember { mutableStateOf(false) }
    var showEditUserDialog by remember { mutableStateOf(false) }
    var showDeleteUserDialog by remember { mutableStateOf(false) }
    var showCreateAccountDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Perfil e Configurações",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = NuTextPrimary
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar", tint = NuTextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // User Info Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NuActionCircle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(NuPurple),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = user?.name?.firstOrNull()?.uppercase() ?: "U",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = user?.name ?: "Usuário",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = NuTextPrimary
                            )
                            Text(
                                text = user?.email ?: "Sem e-mail cadastrado",
                                fontSize = 13.sp,
                                color = NuTextSecondary
                            )
                            if (user?.doc_id != null) {
                                Text(
                                    text = "CPF: ${user.doc_id}",
                                    fontSize = 12.sp,
                                    color = NuTextSecondary
                                )
                            }
                            if (user?.phone != null) {
                                Text(
                                    text = "Tel: ${user.phone}",
                                    fontSize = 12.sp,
                                    color = NuTextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = NuDivider)
                    Spacer(modifier = Modifier.height(12.dp))

                    // User Actions Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showEditUserDialog = true },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = NuPurple)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Editar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NuPurple)
                        }

                        OutlinedButton(
                            onClick = { showCreateUserDialog = true },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp), tint = NuPurple)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Novo", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NuPurple)
                        }

                        OutlinedButton(
                            onClick = { showDeleteUserDialog = true },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NuError),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp), tint = NuError)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Excluir", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NuError)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bank Accounts Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Contas Bancárias",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = NuTextPrimary
                )

                TextButton(onClick = { showCreateAccountDialog = true }) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = NuPurple)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nova Conta", color = NuPurple, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Account List
            userAccounts.forEach { acc ->
                val isSelected = acc.id == currentAccount.id
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) NuPurpleLight.copy(alpha = 0.25f) else NuActionCircle
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onSwitchAccount(acc.id) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = if (isSelected) NuPurple else NuTextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Ag. ${acc.agency_number} • C/C ${acc.account_number}",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = NuTextPrimary
                                )
                                Text(
                                    text = formatCurrency(acc.balance),
                                    fontSize = 13.sp,
                                    color = if (isSelected) NuPurple else NuTextSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }

                        if (isSelected) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = NuSuccess.copy(alpha = 0.15f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = NuSuccess,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "Atual",
                                        color = NuSuccess,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Close Account Button
            OutlinedButton(
                onClick = { showDeleteAccountDialog = true },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NuError),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, tint = NuError)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Encerrar Conta Atual (${currentAccount.account_number})",
                    color = NuError,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }
    }

    // --- Dialogs ---

    if (showCreateUserDialog) {
        CreateUserDialog(
            onDismiss = { showCreateUserDialog = false },
            onConfirm = { name, email, docId, phone ->
                showCreateUserDialog = false
                onCreateUser(name, email, docId, phone)
            }
        )
    }

    if (showEditUserDialog && user != null) {
        EditUserDialog(
            currentUser = user,
            onDismiss = { showEditUserDialog = false },
            onConfirm = { name, email, phone ->
                showEditUserDialog = false
                onUpdateUser(name, email, phone)
            }
        )
    }

    if (showDeleteUserDialog && user != null) {
        AlertDialog(
            onDismissRequest = { showDeleteUserDialog = false },
            icon = { Icon(Icons.Default.WarningAmber, contentDescription = null, tint = NuError, modifier = Modifier.size(36.dp)) },
            title = { Text("Excluir Usuário", fontWeight = FontWeight.Bold, color = NuTextPrimary) },
            text = {
                Text(
                    "Tem certeza que deseja remover o usuário ${user.name}? O BACEN exige que todas as contas bancárias vinculadas sejam encerradas antes da exclusão.",
                    color = NuTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteUserDialog = false
                        onDeleteUser()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NuError)
                ) {
                    Text("Excluir", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteUserDialog = false }) {
                    Text("Cancelar", color = NuTextSecondary)
                }
            }
        )
    }

    if (showCreateAccountDialog) {
        AlertDialog(
            onDismissRequest = { showCreateAccountDialog = false },
            icon = { Icon(Icons.Default.AccountBalance, contentDescription = null, tint = NuPurple, modifier = Modifier.size(36.dp)) },
            title = { Text("Criar Nova Conta Bancária", fontWeight = FontWeight.Bold, color = NuTextPrimary) },
            text = {
                Text(
                    "Deseja abrir uma nova conta corrente vinculada ao seu usuário? A agência padrão será 0001 e o número da conta será gerado automaticamente.",
                    color = NuTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCreateAccountDialog = false
                        onCreateAccount()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NuPurple)
                ) {
                    Text("Criar Conta", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateAccountDialog = false }) {
                    Text("Cancelar", color = NuTextSecondary)
                }
            }
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            icon = { Icon(Icons.Default.WarningAmber, contentDescription = null, tint = NuError, modifier = Modifier.size(36.dp)) },
            title = { Text("Encerrar Conta Bancária", fontWeight = FontWeight.Bold, color = NuTextPrimary) },
            text = {
                Text(
                    "Deseja encerrar a conta nº ${currentAccount.account_number}?\n\nAtenção: O saldo atual deve ser exatamente R$ 0,00. Contas com saldo positivo não podem ser encerradas.",
                    color = NuTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountDialog = false
                        onDeleteAccount()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NuError)
                ) {
                    Text("Encerrar Conta", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Cancelar", color = NuTextSecondary)
                }
            }
        )
    }
}

@Composable
fun CreateUserDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, email: String, docId: String, phone: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var docId by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("+55") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cadastrar Usuário", fontWeight = FontWeight.Bold, color = NuTextPrimary) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMessage = null },
                    label = { Text("Nome Completo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMessage = null },
                    label = { Text("E-mail") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = docId,
                    onValueChange = { docId = it; errorMessage = null },
                    label = { Text("CPF (11 dígitos)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; errorMessage = null },
                    label = { Text("Telefone (ex: +5585999999999)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(text = errorMessage!!, color = NuError, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank() || email.isBlank() || docId.isBlank() || phone.isBlank()) {
                        errorMessage = "Preencha todos os campos obrigatórios"
                        return@Button
                    }
                    onConfirm(name, email, docId, phone)
                },
                colors = ButtonDefaults.buttonColors(containerColor = NuPurple)
            ) {
                Text("Cadastrar", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = NuTextSecondary)
            }
        }
    )
}

@Composable
fun EditUserDialog(
    currentUser: UserResponse,
    onDismiss: () -> Unit,
    onConfirm: (name: String, email: String, phone: String) -> Unit
) {
    var name by remember { mutableStateOf(currentUser.name) }
    var email by remember { mutableStateOf(currentUser.email) }
    var phone by remember { mutableStateOf(currentUser.phone) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Atualizar Dados do Usuário", fontWeight = FontWeight.Bold, color = NuTextPrimary) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMessage = null },
                    label = { Text("Nome Completo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMessage = null },
                    label = { Text("E-mail") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; errorMessage = null },
                    label = { Text("Telefone (ex: +5585999999999)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "CPF: ${currentUser.doc_id} (identificador único)",
                    color = NuTextSecondary,
                    fontSize = 12.sp
                )

                if (errorMessage != null) {
                    Text(text = errorMessage!!, color = NuError, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank() || email.isBlank() || phone.isBlank()) {
                        errorMessage = "Preencha todos os campos"
                        return@Button
                    }
                    onConfirm(name, email, phone)
                },
                colors = ButtonDefaults.buttonColors(containerColor = NuPurple)
            ) {
                Text("Salvar", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = NuTextSecondary)
            }
        }
    )
}

private fun formatCurrency(amount: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
    return formatter.format(amount)
}
