package com.streamflixvip.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta espelhando o dark theme que o site já usa (fundo escuro, dourado
// como cor de destaque) — mantém identidade visual consistente entre
// site e app.
private val Gold = Color(0xFFD4AF37)
private val DarkBackground = Color(0xFF0A0A10)
private val DarkSurface = Color(0xFF15151C)

private val DarkColors = darkColorScheme(
    primary = Gold,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = Color(0xFF232330),
)

// ── Tema sempre escuro ──
// O app StreamFlixVIP usa exclusivamente o esquema escuro, independente
// do modo do sistema operacional. Isso evita telas brancas/em branco
// quando o usuário tem o modo claro ativado no celular, e garante
// identidade visual consistente em qualquer dispositivo.
// Não há seletor de tema nem perfil de usuário para isso — é uma decisão
// de produto: o app é dark-only, igual ao site.
@Composable
fun StreamFlixTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
