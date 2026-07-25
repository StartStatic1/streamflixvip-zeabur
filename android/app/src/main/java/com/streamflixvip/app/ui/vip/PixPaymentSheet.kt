package com.streamflixvip.app.ui.vip

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamflixvip.app.network.NetworkModule
import com.streamflixvip.app.network.PixRequest
import com.streamflixvip.app.network.PixResponse
import kotlinx.coroutines.launch

/**
 * Bottom Sheet que gerencia o fluxo de pagamento via PIX.
 * 1. Chama a API do Mercado Pago no servidor.
 * 2. Mostra o QR Code (decodificado do Base64) e o botão Copia e Cola.
 * 3. Permite que o usuário finalize o processo.
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
    var isLoading by remember { mutableStateOf(true) }
    var pixData by remember { mutableStateOf<PixResponse?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Carrega o PIX assim que o modal abre
    LaunchedEffect(Unit) {
        try {
            val response = NetworkModule.vipApi.createPix(
                PixRequest(
                    userId = userId,
                    amount = amount,
                    planLabel = planLabel,
                    durationHours = durationHours
                )
            )
            pixData = response
            isLoading = false
        } catch (e: Exception) {
            errorMessage = "Erro ao gerar PIX. Tente novamente."
            isLoading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF15151C),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Pagamento via PIX",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = planLabel,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(24.dp))

            if (isLoading) {
                Box(Modifier.height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (errorMessage != null) {
                Text(errorMessage!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onDismiss) { Text("Fechar") }
            } else if (pixData != null) {
                // QR Code
                pixData?.qrCodeBase64?.let { base64 ->
                    val bitmap = remember(base64) {
                        val decodedString = Base64.decode(base64, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                    }
                    if (bitmap != null) {
                        Surface(
                            modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            color = Color.White
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "QR Code PIX",
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Botão Copia e Cola
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("PIX Copia e Cola", pixData?.qrCode)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Código copiado!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f))
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("PIX Copia e Cola", color = Color.White)
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Já paguei / Fechar")
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Após o pagamento, seu VIP será ativado automaticamente em instantes.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
