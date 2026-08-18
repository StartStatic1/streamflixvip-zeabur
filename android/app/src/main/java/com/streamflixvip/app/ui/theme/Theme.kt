package com.streamflixvip.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * StreamFlix — cinema escuro + ciano eletrico.
 * Hierarquia: fundo quase preto → surface → surfaceVariant → accent.
 */
object StreamFlixColors {
    val Cyan = Color(0xFF00E5FF)
    val CyanDim = Color(0xFF00B8D4)
    val Gold = Color(0xFFFFC107)
    val BadgeNew = Color(0xFFFF3D71)
    val BadgeVip = Color(0xFFFFD54F)
    val BadgeHd = Color(0xFF69F0AE)
    val Background = Color(0xFF05050A)
    val Surface = Color(0xFF0F0F16)
    val SurfaceRaised = Color(0xFF1A1A24)
    val TextMuted = Color.White.copy(alpha = 0.58f)
}

private val DarkColors = darkColorScheme(
    primary = StreamFlixColors.Cyan,
    onPrimary = Color(0xFF001820),
    secondary = StreamFlixColors.CyanDim,
    tertiary = StreamFlixColors.BadgeNew,
    background = StreamFlixColors.Background,
    surface = StreamFlixColors.Surface,
    surfaceVariant = StreamFlixColors.SurfaceRaised,
    onSurface = Color.White,
    onSurfaceVariant = StreamFlixColors.TextMuted,
    outline = StreamFlixColors.Cyan.copy(alpha = 0.22f),
    error = Color(0xFFFF5252),
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
