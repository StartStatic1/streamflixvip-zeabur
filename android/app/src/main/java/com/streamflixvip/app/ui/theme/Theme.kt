package com.streamflixvip.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Cinema Flutuante — preto mais profundo + dourado VIP
private val Gold = Color(0xFFD4AF37)
private val DarkBackground = Color(0xFF05050A)
private val DarkSurface = Color(0xFF0F0F16)

private val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = Color.Black,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = Color(0xFF1A1A24),
    onSurface = Color.White,
    onSurfaceVariant = Color.White.copy(alpha = 0.58f),
    outline = Gold.copy(alpha = 0.18f),
)

// Tema sempre escuro — identidade visual do StreamFlixVIP
@Composable
fun StreamFlixTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
