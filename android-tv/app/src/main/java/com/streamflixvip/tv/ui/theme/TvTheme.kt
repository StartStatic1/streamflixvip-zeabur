package com.streamflixvip.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

// Paleta StreamFlixVIP — glass + indigo (sem amarelo)
private val Accent = Color(0xFF6366F1)
private val DarkBackground = Color(0xFF0B0B14)
private val DarkSurface = Color(0xFF10101A)

private val TvDarkColors = darkColorScheme(
    primary = Accent,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = Color(0xFF1A1A28),
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
