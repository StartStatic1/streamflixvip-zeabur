package com.streamflixvip.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Cinema Flutuante + Deep Ocean
// Accent ciano elétrico no lugar do dourado — sensação glass / tech
private val OceanCyan = Color(0xFF00E5FF)
private val DarkBackground = Color(0xFF05050A)
private val DarkSurface = Color(0xFF0F0F16)

private val DarkColors = darkColorScheme(
    primary = OceanCyan,
    onPrimary = Color(0xFF001820),
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = Color(0xFF1A1A24),
    onSurface = Color.White,
    onSurfaceVariant = Color.White.copy(alpha = 0.58f),
    outline = OceanCyan.copy(alpha = 0.22f),
)

@Composable
fun StreamFlixTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
