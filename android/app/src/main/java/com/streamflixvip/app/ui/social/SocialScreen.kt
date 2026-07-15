package com.streamflixvip.app.ui.social

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Placeholder da aba Social — existe pra a rota nunca ficar "solta" (era
 * isso que causava o crash: BottomNavBar linkava pra "social", mas o
 * NavHost não tinha esse composable registrado, então o NavController
 * tentava navegar pra um destino inexistente e derrubava a Activity).
 *
 * Posts/comentários de verdade (como no print do CineVerse) exigem uma
 * tabela nova no Supabase (ex: social_posts) — isso é decisão de escopo
 * pra próxima rodada, não algo pra inventar aqui sem confirmar contigo.
 */
@Composable
fun SocialScreen() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("💬", fontSize = 40.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "Social chega em breve",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Posts e comentários da comunidade vão aparecer aqui.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
