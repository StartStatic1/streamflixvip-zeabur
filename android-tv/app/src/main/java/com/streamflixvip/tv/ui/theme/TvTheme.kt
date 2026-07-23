package com.streamflixvip.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

// Mesma paleta do app de celular (ver android/.../ui/theme/Theme.kt) —
// identidade visual idêntica entre os dois apps e o site: fundo escuro,
// dourado como cor de destaque/exclusividade VIP.
private val Gold = Color(0xFFD4AF37)
private val DarkBackground = Color(0xFF0A0A10)
private val DarkSurface = Color(0xFF15151C)

private val TvDarkColors = darkColorScheme(
    primary = Gold,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = Color(0xFF232330),
)

/**
 * Tema raiz do app de TV — sempre escuro, igual ao app de celular (ver
 * StreamFlixTheme lá). Usa androidx.tv.material3 em vez de
 * androidx.compose.material3: mesma API de superfície (MaterialTheme,
 * colorScheme), mas os componentes (Card, Button, etc.) vêm de um
 * pacote separado com comportamento de foco/D-pad nativo.
 */
@Composable
fun StreamFlixTvTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = TvDarkColors,
        content = content,
    )
}
