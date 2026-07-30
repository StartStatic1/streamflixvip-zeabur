package com.streamflixvip.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streamflixvip.app.ads.AdsHelper
import kotlinx.coroutines.delay

private const val TMDB_POSTER_BASE = "https://image.tmdb.org/t/p/w342"

/**
 * Gate pra usuários não-VIP antes do player.
 *
 * Fluxo novo (substitui a espera vazia de 8s):
 *  1. Botão principal: "Assistir anúncio e liberar" → Rewarded Video
 *     Start.io (geralmente promo de jogo/app, ~15–30s). Ao terminar,
 *     libera o filme.
 *  2. Se o rewarded falhar (sem fill / rede): cai num contador curto
 *     de fallback (3s) pra não travar o usuário pra sempre.
 *  3. CTA VIP continua visível pra conversão.
 *
 * VIP nunca chega aqui (MainActivity já pula).
 */
@Composable
fun VipWaitScreen(
    title: String,
    posterPath: String?,
    waitSeconds: Int = 3,
    onWaitFinished: () -> Unit,
    onUpgradeClick: () -> Unit,
) {
    val context = LocalContext.current
    var phase by remember { mutableStateOf(Phase.Ready) }
    var remaining by remember { mutableIntStateOf(waitSeconds) }

    // Fallback automático: se o rewarded falhar, conta e libera.
    LaunchedEffect(phase) {
        if (phase != Phase.FallbackCountdown) return@LaunchedEffect
        remaining = waitSeconds
        while (remaining > 0) {
            delay(1_000L)
            remaining -= 1
        }
        onWaitFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (posterPath != null) {
                AsyncImage(
                    model = "$TMDB_POSTER_BASE$posterPath",
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(120.dp)
                        .aspectRatio(2f / 3f)
                        .clip(MaterialTheme.shapes.medium),
                )
                Spacer(Modifier.height(20.dp))
            }

            Text(
                "Preparando \"$title\"…",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                when (phase) {
                    Phase.Ready -> "Assista um anúncio curto para liberar grátis, ou vire VIP e pule tudo."
                    Phase.LoadingAd -> "Carregando anúncio…"
                    Phase.FallbackCountdown -> "Anúncio indisponível — liberando em instantes"
                    Phase.ShowingAd -> "Assista até o fim para liberar o vídeo"
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(28.dp))

            when (phase) {
                Phase.LoadingAd, Phase.ShowingAd -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                }
                Phase.FallbackCountdown -> {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$remaining",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
                Phase.Ready -> Unit
            }

            // CTA principal: rewarded video (o que realmente monetiza)
            if (phase == Phase.Ready) {
                Button(
                    onClick = {
                        phase = Phase.LoadingAd
                        AdsHelper.showRewardedVideo(
                            context = context,
                            onRewarded = {
                                onWaitFinished()
                            },
                            onDismissedWithoutReward = {
                                // Fechou no meio — volta pro estado inicial, não libera.
                                phase = Phase.Ready
                            },
                            onFailed = {
                                // Sem fill (comum com pouco tráfego): fallback curto.
                                phase = Phase.FallbackCountdown
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(0.88f),
                ) {
                    Text("▶ Assistir anúncio e liberar grátis")
                }
                Spacer(Modifier.height(10.dp))
            }

            Button(
                onClick = onUpgradeClick,
                modifier = Modifier.fillMaxWidth(0.88f),
                enabled = phase == Phase.Ready || phase == Phase.FallbackCountdown,
            ) {
                Text("⚡ Pular tudo — seja VIP")
            }

            // Atalho opcional: liberar sem anúncio só no fallback (já contando).
            // No estado Ready NÃO oferecemos "pular sem ver anúncio" — senão
            // ninguém assiste o rewarded e a receita some.
        }
    }
}

private enum class Phase {
    Ready,
    LoadingAd,
    ShowingAd,
    FallbackCountdown,
}
