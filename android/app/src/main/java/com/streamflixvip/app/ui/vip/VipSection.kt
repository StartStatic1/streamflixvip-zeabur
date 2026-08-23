package com.streamflixvip.app.ui.vip

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Composable
fun VipSection(viewModel: VipViewModel) {
    val state by viewModel.uiState.collectAsState()

    if (state.showPaymentSheet) {
        PixPaymentSheet(
            userId = state.userId,
            amount = state.paymentAmount,
            planLabel = state.paymentLabel,
            durationHours = state.paymentDuration,
            onDismiss = {
                viewModel.dismissPayment()
                viewModel.refreshStatus()
            }
        )
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            if (state.isLoadingStatus) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp
                )
                return@Column
            }

            if (state.isVip) {
                Text(
                    text = "VIP ativo",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                state.expiresAt?.let { expiresAt ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Valido ate ${formatVipExpiry(expiresAt)}",
                        fontSize = 13.sp
                    )

                    val daysLeft = daysUntilExpiry(expiresAt)
                    if (daysLeft != null && daysLeft in 0..5) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (daysLeft == 0L) {
                                    "Seu VIP expira hoje. Renove pra nao perder o acesso."
                                } else {
                                    "Seu VIP expira em $daysLeft ${if (daysLeft == 1L) "dia" else "dias"}. Renove pra nao perder o acesso."
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
                state.planLabel?.let { plan ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = plan,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = "Assine o VIP",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Filmes, series e TV ao vivo sem anuncios — suporte ao app e catalogo sempre no ar.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                listOf(
                    "Sem anuncios na reproducao",
                    "Catalogo de filmes e series",
                    "Canais ao vivo e qualidade HD+"
                ).forEach { benefit ->
                    Text(
                        text = "  ·  $benefit",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                VipPlanCard(
                    title = "1 Mes",
                    price = "R$ 9,90",
                    priceSuffix = "",
                    badge = null,
                    highlighted = false,
                    onClick = { viewModel.startPayment(9.90, "VIP 30 Dias", 720) }
                )
                Spacer(modifier = Modifier.height(10.dp))

                VipPlanCard(
                    title = "3 Meses",
                    price = "R$ 24,90",
                    priceSuffix = "",
                    badge = "Popular",
                    highlighted = true,
                    onClick = { viewModel.startPayment(24.90, "VIP 90 Dias", 2160) }
                )
                Spacer(modifier = Modifier.height(10.dp))

                VipPlanCard(
                    title = "6 Meses",
                    price = "R$ 49,90",
                    priceSuffix = "",
                    badge = "Mais tempo",
                    highlighted = false,
                    onClick = { viewModel.startPayment(49.90, "VIP 180 Dias", 4320) }
                )
                Spacer(modifier = Modifier.height(10.dp))

                VipPlanCard(
                    title = "Vitalicio",
                    price = "R$ 149,90",
                    priceSuffix = "uma vez · para sempre",
                    badge = "Melhor valor",
                    highlighted = false,
                    accentOutline = true,
                    onClick = { viewModel.startPayment(149.90, "VIP Vitalicio", 876000) }
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Ao escolher o plano: InfinitePay (automatico) ou Pix direto na chave." +
                        " No Pix direto, envie o comprovante no Telegram e ative com o codigo.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))
                Divider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp
                )
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Ja tem um codigo?",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.redeemCode,
                    onValueChange = viewModel::onCodeChange,
                    label = { Text("Codigo VIP") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = viewModel::redeem,
                    enabled = !state.isRedeeming,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isRedeeming) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Ativar codigo")
                    }
                }
            }

            state.redeemMessage?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = msg,
                    fontSize = 13.sp,
                    color = if (state.redeemSuccess) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
        }
    }
}

@Composable
private fun VipPlanCard(
    title: String,
    price: String,
    priceSuffix: String,
    badge: String?,
    highlighted: Boolean,
    onClick: () -> Unit,
    accentOutline: Boolean = false
) {
    val accent = Color(0xFF00E5FF)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (highlighted) {
            accent.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = when {
            highlighted -> BorderStroke(1.5.dp, accent)
            accentOutline -> BorderStroke(1.dp, accent.copy(alpha = 0.5f))
            else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (badge != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = accent.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = badge,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = accent,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                if (priceSuffix.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = priceSuffix,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = price,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (highlighted || accentOutline) {
                    accent
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

private fun formatVipExpiry(isoDate: String): String {
    return try {
        val date = OffsetDateTime.parse(isoDate)
        date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    } catch (_: DateTimeParseException) {
        isoDate
    }
}

private fun daysUntilExpiry(isoDate: String): Long? {
    return try {
        val expiry = OffsetDateTime.parse(isoDate)
        val now = OffsetDateTime.now()
        java.time.Duration.between(now, expiry).toDays()
    } catch (_: DateTimeParseException) {
        null
    }
}
