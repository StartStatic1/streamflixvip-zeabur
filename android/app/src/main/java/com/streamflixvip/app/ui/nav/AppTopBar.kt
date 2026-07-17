package com.streamflixvip.app.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Barra de topo comum às abas principais (Início, Gêneros, Favoritos,
 * Perfil) — avatar com inicial + nome de quem está logado à esquerda,
 * lupa à direita levando pra Explorar (onde a busca/filtro de verdade
 * mora). NÃO aparece em Explorar (redundante — já está lá) nem nas telas
 * de player/detalhe (que têm sua própria barra de voltar/ações).
 */
@Composable
fun AppTopBar(userDisplayName: String?, onSearchClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primary) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val initial = userDisplayName?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFF6C4FE0), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(initial, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                if (userDisplayName != null) {
                    Text(
                        userDisplayName.uppercase(),
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Icon(
                Icons.Filled.Search,
                contentDescription = "Explorar e buscar",
                tint = Color.White,
                modifier = Modifier
                    .size(26.dp)
                    .clickable(onClick = onSearchClick),
            )
        }
    }
}
