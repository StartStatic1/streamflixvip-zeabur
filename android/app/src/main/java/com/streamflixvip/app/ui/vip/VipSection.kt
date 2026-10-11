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
                state.expiresAt?.let { expires ->
                    Text(
                        text = "Valido ate ${formatExpiry(expires)}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            } else {
                Text(
                    text = "Seja VIP",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Assista sem anuncios, desbloqueie servidores premium e tenha a melhor experiencia.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                    title = "Anual",
                    price = "R$ 89,90",
                    priceSuffix = "12 meses",
                    badge = "Melhor valor",
                    highlighted = false,
                    accentOutline = true,
                    onClick = { viewModel.startPayment(89.90, "VIP Anual", 8760) }
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Ao escolher o plano: InfinitePay (automatico) ou Pix direto na chave." +
                        " No Pix direto, envie o comprovante no Telegram e ative com o codigo.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Divider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp
            )
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Ja tem um codigo?",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = state.codeInput,
                onValueChange = viewModel::onCodeChange,
                label = { Text("Codigo VIP") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = state.redeemError != null
            )

            state.redeemError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            state.redeemSuccess?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = viewModel::redeemCode,
                enabled = !state.isRedeeming && state.codeInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isRedeeming) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp).width(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Ativar codigo")
                }
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
    accentOutline: Boolean = false,
    onClick: () -> Unit,
) {
    val borderColor = when {
        highlighted -> MaterialTheme.colorScheme.primary
        accentOutline -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    if (badge != null) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        ) {
                            Text(
                                badge,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                if (priceSuffix.isNotBlank()) {
                    Text(
                        priceSuffix,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Text(
                price,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun formatExpiry(iso: String): String {
    return try {
        val odt = OffsetDateTime.parse(iso)
        odt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
    } catch (_: DateTimeParseException) {
        iso
    }
}
