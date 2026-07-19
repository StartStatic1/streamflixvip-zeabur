package com.streamflixvip.app.ui.vip

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Seção VIP embutida na tela de Perfil — mostra status atual e, se não
 * for VIP, um formulário pra resgatar código. Mesma lógica de negócio do
 * modal VIP do site, só que como seção nativa em vez de modal HTML.
 */
@Composable
fun VipSection(viewModel: VipViewModel) {
    val state by viewModel.uiState.collectAsState()

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (state.isLoadingStatus) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                return@Column
            }

            if (state.isVip) {
                Text(
                    "👑 VIP ativo",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                state.expiresAt?.let { expiresAt ->
                    Spacer(Modifier.height(4.dp))
                    Text("Válido até ${formatVipExpiry(expiresAt)}", fontSize = 13.sp)

                    // Aviso de renovação — só aparece nos últimos dias
                    // antes de expirar, pra pessoa não ser pega de
                    // surpresa perdendo o acesso sem aviso nenhum.
                    val daysLeft = daysUntilExpiry(expiresAt)
                    if (daysLeft != null && daysLeft in 0..5) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = if (daysLeft == 0L) {
                                    "⚠️ Seu VIP expira hoje. Renove pra não perder o acesso."
                                } else {
                                    "⚠️ Seu VIP expira em $daysLeft ${if (daysLeft == 1L) "dia" else "dias"}. Renove pra não perder o acesso."
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                }
                state.planLabel?.let { plan ->
                    Spacer(Modifier.height(2.dp))
                    Text(plan, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text("Você ainda não é VIP", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.redeemCode,
                    onValueChange = viewModel::onCodeChange,
                    label = { Text("Código VIP") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = viewModel::redeem,
                    enabled = !state.isRedeeming,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isRedeeming) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Ativar código")
                    }
                }
            }

            state.redeemMessage?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Text(
                    msg,
                    fontSize = 13.sp,
                    color = if (state.redeemSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Formata a data de expiração no padrão dd/MM/yyyy, igual formatVipExpiry() do site. */
private fun formatVipExpiry(isoDate: String): String {
    return try {
        val date = OffsetDateTime.parse(isoDate)
        date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    } catch (_: DateTimeParseException) {
        isoDate
    }
}

/** Dias restantes até a expiração (arredondado pra baixo) — null se a data vier num formato inesperado. */
private fun daysUntilExpiry(isoDate: String): Long? {
    return try {
        val expiry = OffsetDateTime.parse(isoDate)
        val now = OffsetDateTime.now()
        java.time.Duration.between(now, expiry).toDays()
    } catch (_: DateTimeParseException) {
        null
    }
}
