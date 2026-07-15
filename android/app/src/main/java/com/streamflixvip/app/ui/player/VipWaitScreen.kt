package com.streamflixvip.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

private const val TMDB_POSTER_BASE = "https://image.tmdb.org/t/p/w342"

/**
 * Tela de espera exibida ANTES do player abrir, só para usuários
 * não-VIP — mesma ideia da fricção que o site já usa (Rewarded
 * Interstitial no primeiro play). Dá alguns segundos de contagem
 * regressiva com CTA "seja VIP" ao lado; ao terminar (ou se a pessoa
 * virar VIP durante a espera), segue pro player normalmente.
 *
 * VIP pula direto pra este composable nem é chamado — a checagem de
 * "pular espera" acontece antes de navegar pra cá (ver MainActivity).
 */
@Composable
fun VipWaitScreen(
    title: String,
    posterPath: String?,
    waitSeconds: Int = 8,
    onWaitFinished: () -> Unit,
    onUpgradeClick: () -> Unit,
) {
    var remaining by remember { mutableIntStateOf(waitSeconds) }

    LaunchedEffect(Unit) {
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
            Spacer(Modifier.height(20.dp))

            // Contador circular simples — não precisa de animação
            // sofisticada, só comunicar "está contando, vai começar já".
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
            Spacer(Modifier.height(8.dp))
            Text(
                "Seu vídeo começa em instantes",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(32.dp))

            // CTA de conversão: a espera em si é o gatilho, o botão é a saída
            // imediata — VIP assiste sem esperar isso de novo, nunca.
            Button(onClick = onUpgradeClick, modifier = Modifier.fillMaxWidth(0.8f)) {
                Text("⚡ Pular espera — seja VIP")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onWaitFinished, modifier = Modifier.fillMaxWidth(0.8f)) {
                Text("Aguardar e assistir grátis")
            }
        }
    }
}
