package com.streamflixvip.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

// Paleta StreamFlixVIP — fundo escuro + dourado premium
private val Gold = Color(0xFFD4AF37)
private val DarkBackground = Color(0xFF0A0A10)
private val DarkSurface = Color(0xFF15151C)

private val TvDarkColors = darkColorScheme(
    primary = Gold,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = Color(0xFF232330),
)

@Composable
fun StreamFlixTvTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = TvDarkColors,
        content = content,
    )
}
