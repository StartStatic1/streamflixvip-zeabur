package com.streamflixvip.app.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Cabeçalho comum das abas principais. A ação de pesquisa ganha uma área de
 * toque confortável, contraste alto e aparência translúcida, sem confundi-la
 * com a experiência de descoberta da aba Explorar.
 */
@Composable
fun AppTopBar(userDisplayName: String?, onSearchClick: () -> Unit) {
    val displayName = userDisplayName?.takeIf { it.isNotBlank() } ?: "Você"
    val initial = displayName.firstOrNull()?.uppercase() ?: "U"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initial,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "Olá, $displayName",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Encontre algo para assistir",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        IconButton(
            onClick = onSearchClick,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)),
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Abrir pesquisa geral",
                modifier = Modifier.size(25.dp),
                tint = Color.White,
            )
        }
    }
}
