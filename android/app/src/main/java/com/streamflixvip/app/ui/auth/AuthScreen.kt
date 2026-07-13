package com.streamflixvip.app.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tela de login por e-mail + código de 6 dígitos — mesmo fluxo de duas
 * etapas que o site já usa (signInWithOtp -> verifyOtp), só que em UI
 * nativa Compose em vez de modal HTML.
 */
@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onLoggedIn: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    if (state.step is AuthStep.LoggedIn) {
        onLoggedIn()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "StreamFlixVIP",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))

        when (state.step) {
            AuthStep.EnterEmail -> EmailStep(state, viewModel)
            AuthStep.EnterCode -> CodeStep(state, viewModel)
            AuthStep.LoggedIn -> Unit // tratado acima
        }

        state.infoMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
        }
        state.errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
    }
}

@Composable
private fun EmailStep(state: AuthUiState, viewModel: AuthViewModel) {
    Text("Entre com seu e-mail para continuar", fontSize = 14.sp)
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = state.email,
        onValueChange = viewModel::onEmailChange,
        label = { Text("E-mail") },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = viewModel::sendCode,
        enabled = !state.isLoading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
        } else {
            Text("Continuar")
        }
    }
}

@Composable
private fun CodeStep(state: AuthUiState, viewModel: AuthViewModel) {
    Text("Digite o código de 6 dígitos enviado para ${state.email}", fontSize = 14.sp)
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = state.code,
        onValueChange = { if (it.length <= 6) viewModel.onCodeChange(it) },
        label = { Text("Código") },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = viewModel::confirmCode,
        enabled = !state.isLoading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
        } else {
            Text("Entrar")
        }
    }
}
