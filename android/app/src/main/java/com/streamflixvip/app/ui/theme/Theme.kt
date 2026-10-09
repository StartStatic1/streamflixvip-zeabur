package com.streamflixvip.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * StreamFlix — sala neutra.
 * Botão branco, letra preta. Cor só no pôster.
 * Live é o único vermelho.
 * Amber/Gold/Cyan continuam como alias do branco para telas antigas.
 */
object StreamFlixColors {
    val Background = Color(0xFF0A0A0A)
    val Surface = Color(0xFF141414)
    val SurfaceRaised = Color(0xFF1A1A1A)
    val SurfaceHigh = Color(0xFF222222)
    val Amber = Color(0xFFFFFFFF)
    val AmberDeep = Color(0xFFE5E5E5)
    val Teal = Color(0xFFE50914)
    val Text = Color(0xFFF4F4F4)
    val TextMuted = Color(0xFFA3A3A3)
    val TextDim = Color(0xFF737373)
    val BadgeNew = Color(0xFFE50914)
    val BadgeVip = Color(0xFFFFFFFF)
    val BadgeOk = Color(0xFFA3A3A3)
    val Gold = Color(0xFFFFFFFF)
    val Cyan = Color(0xFFFFFFFF)
    val CyanDim = Color(0xFFE5E5E5)
    val BadgeHd = Color(0xFFA3A3A3)
    val Ink = Color(0xFF0A0A0A)
}

private val DarkColors = darkColorScheme(
    primary = StreamFlixColors.Amber,
    onPrimary = StreamFlixColors.Ink,
    secondary = Color(0xFFFFFFFF),
    tertiary = StreamFlixColors.Teal,
    background = StreamFlixColors.Background,
    surface = StreamFlixColors.Surface,
    surfaceVariant = StreamFlixColors.SurfaceRaised,
    onBackground = StreamFlixColors.Text,
    onSurface = StreamFlixColors.Text,
    onSurfaceVariant = StreamFlixColors.TextMuted,
    outline = Color.White.copy(alpha = 0.16f),
    error = Color(0xFFFF4D4D),
)

@Composable
fun StreamFlixTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = StreamFlixTypography,
        content = content,
    )
}
