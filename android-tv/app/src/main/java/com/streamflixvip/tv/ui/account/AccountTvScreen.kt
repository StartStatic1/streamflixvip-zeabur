package com.streamflixvip.tv.ui.account

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.streamflixvip.tv.BuildConfig
import com.streamflixvip.tv.data.LocalLibraryStore
import com.streamflixvip.tv.data.TvActivationManager

/**
 * Tela única de Conta: status VIP, limpar histórico local e desativar o aparelho.
 * Substitui os ícones mortos de Perfil e Engrenagem na sidebar.
 */
@Composable
fun AccountTvScreen(
    activationManager: TvActivationManager,
    onBack: () -> Unit = {},
    onDeactivated: () -> Unit = {},
) {
    val context = LocalContext.current
    val libraryStore = remember { LocalLibraryStore(context) }
    var continueCount by remember { mutableIntStateOf(libraryStore.getContinueWatching().size) }
    var favoritesCount by remember { mutableIntStateOf(libraryStore.getFavorites().size) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { firstFocus.requestFocus() }
    }

    val plan = activationManager.planLabel ?: "VIP"
    val active = activationManager.isActivatedLocally

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A10))
            .padding(48.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccountActionChip(
                    label = "Voltar",
                    icon = Icons.Filled.ArrowBack,
                    focusRequester = firstFocus,
                    onClick = onBack,
                )
                Spacer(modifier = Modifier.width(24.dp))
                Text("Conta", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF16161F), RoundedCornerShape(16.dp))
                    .padding(28.dp),
            ) {
                Column {
                    Text(
                        if (active) "Aparelho ativado" else "Aparelho não ativado",
                        color = if (active) Color(0xFFD4AF37) else Color.White.copy(alpha = 0.6f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Plano: $plan", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "ID do aparelho: ${activationManager.deviceId.take(12)}…",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 13.sp,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Continuar assistindo: $continueCount  ·  Minha lista: $favoritesCount",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 14.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text("Biblioteca neste aparelho", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            Spacer(modifier = Modifier.height(12.dp))

            AccountActionChip(
                label = "Limpar Continuar assistindo",
                icon = Icons.Filled.Delete,
                onClick = {
                    libraryStore.clearAllProgress()
                    continueCount = 0
                    statusMessage = "Histórico de progresso limpo neste aparelho."
                },
            )
            Spacer(modifier = Modifier.height(12.dp))
            AccountActionChip(
                label = "Limpar Minha lista",
                icon = Icons.Filled.Delete,
                onClick = {
                    libraryStore.clearAllFavorites()
                    favoritesCount = 0
                    statusMessage = "Minha lista limpa neste aparelho."
                },
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text("Sessão", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            Spacer(modifier = Modifier.height(12.dp))
            AccountActionChip(
                label = "Desativar este aparelho",
                icon = Icons.Filled.ExitToApp,
                destructive = true,
                onClick = {
                    activationManager.clearLocalActivation()
                    onDeactivated()
                },
            )

            Spacer(modifier = Modifier.height(32.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "StreamFlixVIP TV  v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 13.sp,
                )
            }

            statusMessage?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(it, color = Color(0xFFD4AF37), fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun AccountActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    destructive: Boolean = false,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.04f else 1f, label = "acc_scale")
    val bg = when {
        isFocused && destructive -> Color(0xFFB71C1C)
        isFocused -> Color(0xFFD4AF37)
        destructive -> Color(0xFF2A1515)
        else -> Color(0xFF1A1A24)
    }
    val fg = if (isFocused) Color.Black else Color.White

    Card(
        onClick = onClick,
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .height(52.dp)
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = bg,
            focusedContainerColor = bg,
        ),
        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
            Text(label, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}
