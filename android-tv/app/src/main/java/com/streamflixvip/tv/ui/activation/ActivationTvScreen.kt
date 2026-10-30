package com.streamflixvip.tv.ui.activation

import com.streamflixvip.tv.ui.theme.LocalPalette
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
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

private val Bg: Color @Composable get() = LocalPalette.current.bg
private val Accent: Color @Composable get() = LocalPalette.current.accent
private val AccentSoft: Color @Composable get() = LocalPalette.current.accent2
private val Cyan: Color @Composable get() = LocalPalette.current.accent2
private val Glass: Color @Composable get() = LocalPalette.current.glass
private val GlassBorder: Color @Composable get() = LocalPalette.current.glassBorder
private val TextMuted: Color @Composable get() = LocalPalette.current.textMuted

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
    val inUseMsg by activationManager.inUseMessage.collectAsState()
    // código digitado que bateu em "já em uso em outra TV" -> botão reenvia com replace
    var replaceCode by remember { mutableStateOf<String?>(null) }
    var replaceLimit by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val firstKeyFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { firstKeyFocusRequester.requestFocus() }
    }

    // Ativar sem digitar: QR + código de 6 números confirmados pelo celular logado.
    var pair by remember { mutableStateOf<com.streamflixvip.tv.data.TvPairClient.Session?>(null) }
    var paired by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            val s = runCatching { com.streamflixvip.tv.data.TvPairClient.start(activationManager.deviceId) }.getOrNull()
            pair = s
            if (s == null) { kotlinx.coroutines.delay(60_000); continue }
            val started = System.currentTimeMillis()
            var renew = false
            while (!renew) {
                kotlinx.coroutines.delay(3000)
                val r = runCatching { com.streamflixvip.tv.data.TvPairClient.poll(s, activationManager.deviceId) }.getOrNull()
                when (r?.status) {
                    "done" -> {
                        paired = true
                        // 1) validade que veio no poll; 2) confere no servidor (algumas tentativas)
                        var ok = runCatching { activationManager.applyPaired(r.expiresAt, r.planLabel) }.getOrDefault(false)
                        var tries = 0
                        while (!ok && tries < 5) {
                            ok = runCatching { activationManager.revalidate() }.getOrDefault(false)
                            if (!ok) { tries++; kotlinx.coroutines.delay(2000) }
                        }
                        if (ok) { onActivated(); return@LaunchedEffect }
                        paired = false
                        renew = true
                    }
                    "expired" -> renew = true
                }
                if (System.currentTimeMillis() - started > 9 * 60_000) renew = true
            }
        }
    }

    fun submit() {
        if (code.isBlank() || isLoading) return
        isLoading = true
        errorMessage = null
        scope.launch {
            val typed = code
            val result = activationManager.activate(typed)
            isLoading = false
            result.onSuccess { replaceCode = null; onActivated() }
                .onFailure { e ->
                    errorMessage = e.message ?: "Não foi possível ativar."
                    if (e is TvActivationManager.TvInUseException) { replaceCode = typed; replaceLimit = e.limit } else replaceCode = null
                }
        }
    }

    /** "Usar nesta TV": move a assinatura pra cá e desconecta a outra TV. */
    fun useHere() {
        if (isLoading) return
        isLoading = true
        errorMessage = null
        scope.launch {
            val rc = replaceCode
            val result = if (rc != null) activationManager.activate(rc, replace = true) else activationManager.claimHere()
            isLoading = false
            result.onSuccess { replaceCode = null; onActivated() }
                .onFailure { e ->
                    errorMessage = e.message ?: "Não foi possível ativar."
                    if (e is TvActivationManager.TvInUseException) replaceLimit = e.limit
                }
        }
    }

    Box(Modifier.fillMaxSize().background(BrandBlack)) {
        // brilho neon vermelho no topo
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(listOf(BrandRed.copy(alpha = 0.22f), Color.Transparent), radius = 1100f, center = androidx.compose.ui.geometry.Offset(300f, 0f)),
            ),
        )
        Row(
            Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(44.dp),
        ) {
            // Esquerda: marca + QR grande
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(com.streamflixvip.tv.R.drawable.ic_launcher_fg),
                        contentDescription = null,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Row {
                            Text("Stream", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                            Text("Flix", color = BrandRed, fontSize = 26.sp, fontWeight = FontWeight.Black)
                        }
                        Text("ATIVAR ESTA TV", color = BrandGold, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    }
                }
                Spacer(Modifier.height(26.dp))
                val pp = pair
                Row(
                    Modifier.clip(RoundedCornerShape(22.dp)).background(Color(0xFF121010))
                        .border(1.dp, BrandRed.copy(alpha = 0.45f), RoundedCornerShape(22.dp)).padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(210.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (pp != null) com.streamflixvip.tv.ui.components.QrCode(pp.url, Modifier.fillMaxSize())
                        else CircularProgressIndicator(Modifier.size(36.dp), color = BrandRed)
                    }
                    Spacer(Modifier.width(22.dp))
                    Column(Modifier.widthIn(max = 260.dp)) {
                        Text(if (paired) "Conectando…" else "Entrar com o celular", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "1. Leia o QR com a câmera do celular\n2. Ou no app: Perfil › Conectar TV e digite:",
                            color = Color(0xFFB9B1AB), fontSize = 13.sp, lineHeight = 19.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            pp?.pin?.chunked(3)?.joinToString(" ") ?: "··· ···",
                            color = BrandGold, fontSize = 40.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (paired) "Recebido! Liberando a TV…" else "A TV entra sozinha quando o celular confirmar.",
                            color = if (paired) Color(0xFF34D399) else Color(0xFF8F8882), fontSize = 12.sp,
                        )
                    }
                }
                if (pair == null) {
                    Spacer(Modifier.height(16.dp))
                    Text("Gerando QR… se demorar, confira a internet da TV.", color = Color(0xFF8F8882), fontSize = 12.sp)
                }
            }

            // Direita: código VIP no teclado (D-pad)
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.035f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp)).padding(22.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Ou digite seu código VIP", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("O mesmo código do app ou o enviado pelo suporte.", color = Color(0xFF8F8882), fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF121010))
                        .border(1.dp, if (code.isNotEmpty()) BrandRed.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    Text(
                        code.ifEmpty { "••••••••" },
                        fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp,
                        color = if (code.isEmpty()) Color.White.copy(alpha = 0.25f) else Color.White,
                    )
                }
                if (errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(errorMessage!!, color = Color(0xFFF87171), fontSize = 13.sp)
                }
                val showUseHere = (replaceCode != null && !replaceLimit) ||
                    (replaceCode == null && inUseMsg != null && !activationManager.swapLimitReached)
                if (inUseMsg != null && errorMessage == null) {
                    Spacer(Modifier.height(8.dp))
                    Text(inUseMsg!! + " Cada assinatura vale para 1 celular e 1 TV.", color = Color(0xFFFBBF24), fontSize = 13.sp)
                }
                if (showUseHere) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { useHere() },
                        enabled = !isLoading,
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFFFBBF24), focusedContainerColor = Color(0xFFFFD45C),
                            contentColor = Color.Black, focusedContentColor = Color.Black,
                        ),
                    ) { Text("Usar nesta TV (desconecta a outra)", fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(14.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(10),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
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
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { submit() },
                        enabled = code.isNotBlank() && !isLoading,
                        colors = ButtonDefaults.colors(
                            containerColor = BrandRed, focusedContainerColor = Color(0xFFFF2A36),
                            contentColor = Color.White, focusedContentColor = Color.White,
                            disabledContainerColor = BrandRed.copy(alpha = 0.3f), disabledContentColor = Color.White.copy(alpha = 0.5f),
                        ),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp)); Text("Ativando…")
                        } else {
                            Icon(Icons.Filled.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                            Text("Ativar", fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = { if (code.isNotEmpty()) code = code.dropLast(1) },
                        colors = ButtonDefaults.colors(containerColor = Color.White.copy(alpha = 0.07f), focusedContainerColor = Color.White.copy(alpha = 0.18f), contentColor = Color.White, focusedContentColor = Color.White),
                    ) { Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Apagar") }
                    Button(
                        onClick = { code = "" },
                        colors = ButtonDefaults.colors(containerColor = Color.White.copy(alpha = 0.07f), focusedContainerColor = Color.White.copy(alpha = 0.18f), contentColor = Color.White, focusedContentColor = Color.White),
                    ) { Text("Limpar", fontSize = 13.sp) }
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val waText = "Olá! Quero ativar o StreamFlix na minha TV. ID: ${activationManager.deviceId}"
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).padding(4.dp)) {
                        com.streamflixvip.tv.ui.components.QrCode(
                            "https://wa.me/558498334731?text=" + java.net.URLEncoder.encode(waText, "UTF-8"),
                            Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("Não tem código? Leia este QR para falar com o suporte no WhatsApp.", color = Color(0xFF8F8882), fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }
    }
}

private val BrandRed = Color(0xFFE50914)
private val BrandGold = Color(0xFFF5B942)
private val BrandBlack = Color(0xFF050505)

@Composable
private fun KeyButton(label: String, focusRequester: FocusRequester?, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = Modifier
            .aspectRatio(1.0f)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                1.5.dp,
                if (isFocused) BrandGold else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(12.dp),
            ),
        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
        scale = CardDefaults.scale(focusedScale = 1.08f),
        colors = CardDefaults.colors(
            containerColor = Color(0xFF151212),
            focusedContainerColor = BrandRed,
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
    }
}
