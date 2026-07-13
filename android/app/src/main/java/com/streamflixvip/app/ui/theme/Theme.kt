package com.streamflixvip.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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

private val LightColors = lightColorScheme(
    primary = Gold,
)

@Composable
fun StreamFlixTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
