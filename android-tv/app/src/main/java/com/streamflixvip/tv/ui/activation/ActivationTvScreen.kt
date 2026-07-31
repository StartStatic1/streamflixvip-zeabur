package com.streamflixvip.tv.ui.activation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import com.streamflixvip.tv.data.TvActivationManager
import kotlinx.coroutines.launch

private val Bg = Color(0xFF0B0B14)
private val Accent = Color(0xFF6366F1)
private val AccentSoft = Color(0xFF818CF8)
private val Cyan = Color(0xFF22D3EE)
private val Glass = Color.White.copy(alpha = 0.08f)
private val GlassBorder = Color.White.copy(alpha = 0.14f)
private val TextMuted = Color(0xFFA1A1B5)

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
            result.onSuccess { onActivated() }
                .onFailure { e -> errorMessage = e.message ?: "Não foi possível ativar." }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF12122A), Bg, Color(0xFF0A0A12)),
                ),
            ),
    ) {
        // vinheta cinema
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(Accent.copy(alpha = 0.12f), Color.Transparent),
                    radius = 900f,
                ),
            ),
        )

        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            // Esquerda — marca + código
            Column(
                Modifier.weight(0.9f).fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(Accent, Cyan.copy(alpha = 0.8f)))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("StreamFlix VIP", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Ativação do aparelho", color = TextMuted, fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.height(28.dp))

                Text(
                    "Digite seu código VIP",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Use o controle remoto no teclado ao lado. O mesmo código do app mobile ou o enviado pelo suporte.",
                    fontSize = 14.sp,
                    color = TextMuted,
                    lineHeight = 20.sp,
                )

                Spacer(Modifier.height(28.dp))

                // Display do código
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Glass)
                        .border(1.dp, if (code.isNotEmpty()) AccentSoft.copy(alpha = 0.5f) else GlassBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 22.dp, vertical = 20.dp),
                ) {
                    Text(
                        text = code.ifEmpty { "••••••••" },
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (code.isEmpty()) Color.White.copy(alpha = 0.25f) else Color.White,
                        letterSpacing = 4.sp,
                    )
                }

                if (errorMessage != null) {
                    Spacer(Modifier.height(14.dp))
                    Text(errorMessage!!, color = Color(0xFFF87171), fontSize = 14.sp)
                }

                Spacer(Modifier.height(24.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { submit() },
                        enabled = code.isNotBlank() && !isLoading,
                        colors = ButtonDefaults.colors(
                            containerColor = Accent,
                            focusedContainerColor = AccentSoft,
                            contentColor = Color.White,
                            focusedContentColor = Color.White,
                            disabledContainerColor = Accent.copy(alpha = 0.35f),
                            disabledContentColor = Color.White.copy(alpha = 0.5f),
                        ),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Ativando…")
                        } else {
                            Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Ativar", fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = { if (code.isNotEmpty()) code = code.dropLast(1) },
                        colors = ButtonDefaults.colors(
                            containerColor = Glass,
                            focusedContainerColor = Color.White.copy(alpha = 0.16f),
                            contentColor = Color.White,
                            focusedContentColor = Color.White,
                        ),
                    ) {
                        Icon(Icons.Filled.Backspace, contentDescription = "Apagar")
                    }
                    Button(
                        onClick = { code = "" },
                        colors = ButtonDefaults.colors(
                            containerColor = Glass,
                            focusedContainerColor = Color.White.copy(alpha = 0.16f),
                            contentColor = Color.White,
                            focusedContentColor = Color.White,
                        ),
                    ) {
                        Text("Limpar", fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    "Este aparelho fica vinculado automaticamente. Após ativar, não precisa digitar de novo ao reinstalar.",
                    color = TextMuted.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }

            // Direita — teclado D-pad
            Column(
                Modifier
                    .weight(1.1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Teclado",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
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
    }
}

@Composable
private fun KeyButton(label: String, focusRequester: FocusRequester?, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = Modifier
            .aspectRatio(1.15f)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                1.5.dp,
                if (isFocused) AccentSoft else GlassBorder,
                RoundedCornerShape(12.dp),
            ),
        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
        scale = CardDefaults.scale(focusedScale = 1.08f),
        colors = CardDefaults.colors(
            containerColor = if (isFocused) Accent.copy(alpha = 0.45f) else Glass,
            focusedContainerColor = Accent.copy(alpha = 0.55f),
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                label,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
    }
}
