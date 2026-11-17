package com.streamflixvip.app.ui.referral

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamflixvip.app.data.ReferralInfo
import com.streamflixvip.app.data.ReferralRepository
import com.streamflixvip.app.ui.theme.StreamFlixColors
import kotlinx.coroutines.launch

private val Gold = StreamFlixColors.Amber

fun shareReferral(context: Context, info: ReferralInfo) {
    val text = info.shareText.ifBlank {
        "🎬 Vem pro StreamFlix VIP! Filmes, séries e TV ao vivo no celular e na TV.\n\n" +
            "Baixe grátis: https://www.streamflixvip.online\n\nNo app, use meu código: ${info.code}"
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(send, "Compartilhar convite")) }
}

/** Cartão "Indique e ganhe" do Perfil (só conta principal logada). */
@Composable
fun ReferralSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<ReferralInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var codeInput by remember { mutableStateOf("") }
    var applying by remember { mutableStateOf(false) }
    var applyMsg by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    LaunchedEffect(Unit) {
        info = ReferralRepository.load()
        loading = false
    }

    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2A1F05), Color(0xFF121722))))
            .border(1.dp, Gold.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Gold.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.CardGiftcard, null, tint = Gold) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Indique e ganhe", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    "Seu amigo assina o VIP e você ganha ${info?.rewardDays ?: 7} dias grátis.",
                    fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        val i = info
        when {
            loading -> Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Gold, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            }
            i == null -> Text(
                "Não foi possível carregar seu código agora. Tente de novo mais tarde.",
                fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f),
            )
            else -> {
                Text("Seu código", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        i.code, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Gold,
                        fontFamily = FontFamily.Monospace, letterSpacing = 3.sp, modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Código StreamFlix", i.code))
                        Toast.makeText(context, "Código copiado", Toast.LENGTH_SHORT).show()
                    }) { Icon(Icons.Filled.ContentCopy, "Copiar código", tint = Color.White) }
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { shareReferral(context, i) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black),
                ) {
                    Icon(Icons.Filled.Share, null, modifier = Modifier.size(18.dp), tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text("Compartilhar convite", fontWeight = FontWeight.Bold, color = Color.Black)
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("Entraram", i.invited.toString(), Modifier.weight(1f))
                    Stat("Assinaram", i.paid.toString(), Modifier.weight(1f))
                    Stat("Dias ganhos", i.daysEarned.toString(), Modifier.weight(1f))
                }

                Spacer(Modifier.height(14.dp))
                if (i.usedCode != null) {
                    Text(
                        "Você entrou com o código ${i.usedCode}.",
                        fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f),
                    )
                } else {
                    Text("Recebeu um código de um amigo?", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = codeInput,
                            onValueChange = { v -> codeInput = v.uppercase().filter { it.isLetterOrDigit() }.take(12); applyMsg = null },
                            placeholder = { Text("Código do amigo") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Gold, unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Gold,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            enabled = !applying && codeInput.length >= 4,
                            onClick = {
                                applying = true
                                scope.launch {
                                    val r = ReferralRepository.apply(codeInput)
                                    applyMsg = r
                                    applying = false
                                    if (r.first) {
                                        ReferralRepository.clearPending(context)
                                        info = ReferralRepository.load() ?: info
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f), contentColor = Color.White),
                        ) { Text(if (applying) "..." else "Usar", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                    applyMsg?.let { (ok, msg) ->
                        Spacer(Modifier.height(6.dp))
                        Text(msg, fontSize = 12.sp, color = if (ok) Color(0xFF66BB6A) else MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Vale para contas novas, uma vez só, antes da primeira assinatura.",
                        fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f),
                    )
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f)).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
    }
}
