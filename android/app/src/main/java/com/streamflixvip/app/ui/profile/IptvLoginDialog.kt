package com.streamflixvip.app.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamflixvip.app.data.IptvStore

private val Accent = Color(0xFF00E5FF)

@Composable
fun IptvLoginDialog(
    iptvStore: IptvStore,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var user by remember { mutableStateOf(iptvStore.xtreamUser ?: "") }
    var pass by remember { mutableStateOf(iptvStore.xtreamPass ?: "") }
    var host by remember { mutableStateOf(iptvStore.xtreamHost ?: "http://tvclubmais.com:80") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurar IPTV Nativo", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(
                    "Insira suas credenciais Xtream para habilitar o leitor nativo com suporte a canais ao vivo.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Host (Servidor)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("Usuário") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Senha") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    iptvStore.xtreamHost = host
                    iptvStore.xtreamUser = user
                    iptvStore.xtreamPass = pass
                    onSuccess()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF001820))
            ) {
                Text("Salvar e Ativar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
