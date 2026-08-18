package com.streamflixvip.app.ui.vip

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalActivity
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamflixvip.app.network.InfinitePayResponse
import com.streamflixvip.app.network.NetworkModule
import com.streamflixvip.app.network.PixRequest

/**
 * Bottom Sheet que gerencia o fluxo de pagamento via InfinitePay.
 * 1. Chama a API do InfinitePay no servidor.
 * 2. Redireciona o usuário para o checkout seguro da InfinitePay.
 * 3. Exibe frases temáticas de cinema para engajamento.
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
    var isLoading by remember { mutableStateOf(true) }
    var paymentData by remember { mutableStateOf<InfinitePayResponse?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Frases de efeito baseadas no plano
    val cinemaPhrase = remember(planLabel) {
        when {
            planLabel.contains("30 Dias") -> "Prepare a pipoca! Sua maratona de um mês está prestes a começar."
            planLabel.contains("90 Dias") -> "Uma trilogia de meses sem anúncios! O papel principal é seu."
            planLabel.contains("Vitalício") -> "O Final Feliz que você merece: acesso ilimitado para sempre!"
            else -> "Luz, Câmera, Ação! Garanta seu acesso VIP agora."
        }
    }

    // Carrega o link de pagamento assim que o modal abre
    LaunchedEffect(Unit) {
        try {
            val response = NetworkModule.vipApi.createInfinitePayLink(
                PixRequest(
                    userId = userId,
                    amount = amount,
                    planLabel = planLabel,
                    durationHours = durationHours
                )
            )
            paymentData = response
            isLoading = false
        } catch (e: Exception) {
            errorMessage = "O projetor falhou! Tente gerar o link novamente."
            isLoading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F0F14),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Ícone temático no topo
            Surface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.LocalActivity,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Seu Ingresso VIP",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            
            Spacer(Modifier.height(8.dp))

            Text(
                text = cinemaPhrase,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(32.dp))

            if (isLoading) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Preparando o cenário...",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
            } else if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f))
                ) {
                    Text("Voltar ao catálogo")
                }
            } else if (paymentData != null) {
                // Card de Resumo do Pedido
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Movie, null, tint = Color.White.copy(alpha = 0.6f))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(planLabel, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Acesso Premium Ilimitado", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "R$ ${String.format("%.2f", amount)}",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))

                // Botão de Ação Principal
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(paymentData?.url))
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(Icons.Default.Payment, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text("Pagar com PIX ou Cartão", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Spacer(Modifier.height(16.dp))

                TextButton(onClick = onDismiss) {
                    Text("Cancelar e voltar", color = Color.White.copy(alpha = 0.4f))
                }

                Spacer(Modifier.height(16.dp))
                
                // Footer com garantia
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.alpha(0.6f)
                ) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFD4AF37), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Ativação automática após o pagamento",
                        fontSize = 11.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
