package com.streamflixvip.tv.ui.activation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import com.streamflixvip.tv.data.TvActivationManager
import kotlinx.coroutines.launch

private const val GOLD = 0xFFD4AF37
private const val BG = 0xFF0A0A10

private val KEYS = listOf(
    "1", "2", "3", "4", "5",
    "6", "7", "8", "9", "0",
    "Q", "W", "E", "R", "T",
    "Y", "U", "I", "O", "P",
    "A", "S", "D", "F", "G",
    "H", "J", "K", "L", "-",
    "Z", "X", "C", "V", "B",
    "N", "M",
)

/**
 * Tela de ativação obrigatória: some só quando `activationManager.activate()`
 * retorna sucesso. Nenhuma rota de navegação existe pra sair daqui sem
 * ativar — quem chama essa composable (MainTvActivity) não dá acesso a
 * home/detail/player enquanto isActivatedLocally == false.
 */
@Composable
fun ActivationTvScreen(activationManager: TvActivationManager, onActivated: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val firstKeyFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { firstKeyFocusRequester.requestFocus() }
    }

    fun submit() {
        if (code.isBlank() || isLoading) return
        isLoading = true
        errorMessage = null
        scope.launch {
            val result = activationManager.activate(code)
            isLoading = false
            result.onSuccess {
                onActivated()
            }.onFailure { e ->
                errorMessage = e.message ?: "Não foi possível ativar."
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxSize().background(Color(BG)).padding(48.dp),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
    ) {
        // ── Coluna esquerda: instruções + código digitado ──
        Column(modifier = Modifier.weight(0.85f), verticalArrangement = Arrangement.Center) {
            Text("Ative seu StreamFlixVIP", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                "Digite o código VIP usando o controle. O mesmo código que ativou no " +
                    "aplicativo mobile também funciona aqui — ou use um código enviado " +
                    "manualmente pelo suporte.",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.65f),
                lineHeight = 22.sp,
            )
            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                Text(
                    text = code.ifEmpty { "Selecione as letras/números ao lado \u2192" },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (code.isEmpty()) Color.White.copy(alpha = 0.35f) else Color(GOLD),
                    letterSpacing = 3.sp,
                )
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(errorMessage!!, color = Color(0xFFE05252), fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(28.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = { submit() },
                    enabled = code.isNotBlank() && !isLoading,
                    colors = ButtonDefaults.colors(containerColor = Color(GOLD), contentColor = Color(BG)),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(BG), strokeWidth = 2.dp)
                    } else {
                        Text("ATIVAR", fontWeight = FontWeight.Bold)
                    }
                }
                Button(
                    onClick = { if (code.isNotEmpty()) code = code.dropLast(1) },
                    colors = ButtonDefaults.colors(containerColor = Color.White.copy(alpha = 0.1f), contentColor = Color.White),
                ) {
                    Icon(Icons.Filled.Backspace, contentDescription = "Apagar")
                }
            }
        }

        // ── Coluna direita: teclado virtual navegável pelo D-pad ──
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(KEYS.size) { index ->
                val key = KEYS[index]
                KeyButton(
                    label = key,
                    focusRequester = if (index == 0) firstKeyFocusRequester else null,
                    onClick = { if (code.length < 24) code += key },
                )
            }
        }
    }
}

@Composable
private fun KeyButton(label: String, focusRequester: FocusRequester?, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = Modifier
            .size(56.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(Modifier.onFocusChanged { isFocused = it.isFocused }),
        colors = CardDefaults.colors(
            containerColor = if (isFocused) Color(GOLD) else Color.White.copy(alpha = 0.08f),
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                label,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (isFocused) Color(BG) else Color.White,
            )
        }
    }
}
