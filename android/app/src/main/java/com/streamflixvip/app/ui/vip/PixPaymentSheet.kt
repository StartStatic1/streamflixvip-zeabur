package com.streamflixvip.app.ui.vip

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LocalActivity
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Telegram
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamflixvip.app.network.InfinitePayResponse
import com.streamflixvip.app.network.NetworkModule
import com.streamflixvip.app.network.PixRequest
import kotlinx.coroutines.launch

private val InfiniteGreen = Color(0xFF00C853)
private val PixTeal = Color(0xFF32BCAD)
private val SheetBg = Color(0xFF0F0F14)

/** Chave Pix Next (telefone) para pagamento manual */
private const val MANUAL_PIX_KEY = "84999585659"
private const val MANUAL_PIX_DISPLAY = "(84) 99958-5659"
private const val TELEGRAM_URL = "https://t.me/streamflixofc"

private enum class PayStep { Choose, InfiniteLoading, InfiniteReady, InfiniteError, Manual }

/**
 * Bottom sheet de pagamento VIP.
 * 1) Escolha: InfinitePay (automatico) ou Pix direto (manual).
 * 2a) Infinite: gera link e abre checkout.
 * 2b) Manual: mostra chave, valor, copia e instrui Telegram + codigo VIP.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixPaymentSheet(
    userId: String,
    amount: Double,
    planLabel: String,
    durationHours: Int,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(PayStep.Choose) }
    var paymentData by remember { mutableStateOf<InfinitePayResponse?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val cinemaPhrase = remember(planLabel) {
        when {
            planLabel.contains("30") -> "Prepare a pipoca! Sua maratona de um mes esta prestes a comecar."
            planLabel.contains("90") -> "Uma trilogia de meses sem anuncios! O papel principal e seu."
            planLabel.contains("180") -> "Seis meses de sessao continua. Luz, camera, VIP."
            planLabel.contains("Vitalicio", ignoreCase = true) ->
                "O final feliz: acesso ilimitado para sempre."
            else -> "Luz, Camera, Acao! Garanta seu acesso VIP agora."
        }
    }

    fun startInfinite() {
        step = PayStep.InfiniteLoading
        errorMessage = null
        scope.launch {
            try {
                val response = NetworkModule.vipApi.createInfinitePayLink(
                    PixRequest(
                        userId = userId,
                        amount = amount,
                        planLabel = planLabel,
                        durationHours = durationHours,
                    ),
                )
                paymentData = response
                step = PayStep.InfiniteReady
            } catch (e: Exception) {
                errorMessage = "O projetor falhou! Tente de novo ou use Pix direto."
                step = PayStep.InfiniteError
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SheetBg,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.LocalActivity,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Seu Ingresso VIP",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = cinemaPhrase,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            Spacer(Modifier.height(20.dp))

            // Resumo do plano
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.05f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Movie, null, tint = Color.White.copy(alpha = 0.6f))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(planLabel, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            "Acesso Premium",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.5f),
                        )
                    }
                    Text(
                        text = "R$ ${String.format("%.2f", amount)}",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            when (step) {
                PayStep.Choose -> {
                    Text(
                        "Como deseja pagar?",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(14.dp))

                    // Botao InfinitePay
                    Button(
                        onClick = { startInfinite() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = InfiniteGreen,
                            contentColor = Color.Black,
                        ),
                    ) {
                        Icon(Icons.Default.Payment, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("InfinitePay", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                "PIX ou cartao · libera sozinho",
                                fontSize = 11.sp,
                                color = Color.Black.copy(alpha = 0.7f),
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Botao Pix direto (manual)
                    OutlinedButton(
                        onClick = { step = PayStep.Manual },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, PixTeal),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = PixTeal,
                        ),
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = null, tint = PixTeal)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Pix direto",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White,
                            )
                            Text(
                                "Pague na chave e envie o comprovante",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.55f),
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = Color.White.copy(alpha = 0.4f))
                    }
                }

                PayStep.InfiniteLoading -> {
                    CircularProgressIndicator(
                        color = InfiniteGreen,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Preparando checkout InfinitePay...",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                    )
                }

                PayStep.InfiniteError -> {
                    Text(
                        text = errorMessage ?: "Erro ao gerar link.",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { startInfinite() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = InfiniteGreen),
                    ) {
                        Text("Tentar de novo", color = Color.Black)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { step = PayStep.Manual },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, PixTeal),
                    ) {
                        Text("Usar Pix direto", color = PixTeal)
                    }
                    TextButton(onClick = { step = PayStep.Choose }) {
                        Text("Voltar", color = Color.White.copy(alpha = 0.5f))
                    }
                }

                PayStep.InfiniteReady -> {
                    Button(
                        onClick = {
                            val url = paymentData?.url ?: return@Button
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = InfiniteGreen,
                            contentColor = Color.Black,
                        ),
                    ) {
                        Icon(Icons.Default.Payment, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text("Abrir InfinitePay", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.alpha(0.65f),
                    ) {
                        Icon(
                            Icons.Default.Star,
                            null,
                            tint = Color(0xFFD4AF37),
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Ativacao automatica apos o pagamento",
                            fontSize = 11.sp,
                            color = Color.White,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { step = PayStep.Choose }) {
                        Text("Outra forma de pagar", color = Color.White.copy(alpha = 0.45f))
                    }
                }

                PayStep.Manual -> {
                    ManualPixBlock(
                        amount = amount,
                        planLabel = planLabel,
                        context = context,
                        onBack = { step = PayStep.Choose },
                        onOpenTelegram = {
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL)),
                                )
                            } catch (_: Exception) {
                                Toast.makeText(
                                    context,
                                    "Abra o Telegram: @streamflixofc",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ManualPixBlock(
    amount: Double,
    planLabel: String,
    context: Context,
    onBack: () -> Unit,
    onOpenTelegram: () -> Unit,
) {
    Text(
        "Pague direto na chave Pix",
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(6.dp))
    Text(
        "Envie exatamente R$ ${String.format("%.2f", amount)} · $planLabel",
        fontSize = 13.sp,
        color = Color.White.copy(alpha = 0.65f),
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(16.dp))

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = PixTeal.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, PixTeal.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Chave Pix (celular · Next)",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    MANUAL_PIX_DISPLAY,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        val cm =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("pix", MANUAL_PIX_KEY))
                        Toast.makeText(context, "Chave Pix copiada!", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copiar",
                        tint = PixTeal,
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    // Passos
    listOf(
        "1. Copie a chave e pague no app do seu banco",
        "2. Envie o comprovante no Telegram",
        "3. Receba o codigo VIP e ative no app",
    ).forEach { line ->
        Text(
            line,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.75f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
        )
    }

    Spacer(Modifier.height(20.dp))

    Button(
        onClick = {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("pix", MANUAL_PIX_KEY))
            Toast.makeText(context, "Chave Pix copiada!", Toast.LENGTH_SHORT).show()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PixTeal,
            contentColor = Color.Black,
        ),
    ) {
        Icon(Icons.Default.ContentCopy, contentDescription = null)
        Spacer(Modifier.width(10.dp))
        Text("Copiar chave Pix", fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }

    Spacer(Modifier.height(10.dp))

    OutlinedButton(
        onClick = onOpenTelegram,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFF29B6F6)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF29B6F6)),
    ) {
        Icon(Icons.Default.Telegram, contentDescription = null)
        Spacer(Modifier.width(10.dp))
        Text("Abrir Telegram @streamflixofc", fontWeight = FontWeight.SemiBold)
    }

    Spacer(Modifier.height(12.dp))

    Text(
        "Depois do pagamento, use o codigo que enviarmos na area " +
            "\"Ja tem um codigo?\" na tela VIP.",
        fontSize = 11.sp,
        color = Color.White.copy(alpha = 0.45f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onBack) {
        Text("Voltar", color = Color.White.copy(alpha = 0.45f))
    }
}
