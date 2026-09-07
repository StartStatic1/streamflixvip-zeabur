package com.streamflixvip.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * StreamFlix — Cinema Noir (diretriz).
 * Ação = âmbar. Turquesa só para ao vivo.
 * Cyan permanece como alias do âmbar para telas antigas.
 */
object StreamFlixColors {
    val Background = Color(0xFF08090D)
    val Surface = Color(0xFF101218)
    val SurfaceRaised = Color(0xFF151820)
    val SurfaceHigh = Color(0xFF1B1F29)
    val Amber = Color(0xFFFFB547)
    val AmberDeep = Color(0xFFFF8A3D)
    val Teal = Color(0xFF25D0C7)
    val Text = Color(0xFFF5F7FA)
    val TextMuted = Color(0xFFA7ADB8)
    val TextDim = Color(0xFF6F7683)
    val BadgeNew = Color(0xFFF27667)
    val BadgeVip = Color(0xFFFFB547)
    val BadgeOk = Color(0xFF65C98A)
    val Gold = Amber
    val Cyan = Amber
    val CyanDim = AmberDeep
    val BadgeHd = BadgeOk
}

private val DarkColors = darkColorScheme(
    primary = StreamFlixColors.Amber,
    onPrimary = Color(0xFF1A1204),
    secondary = StreamFlixColors.Teal,
    tertiary = StreamFlixColors.BadgeNew,
    background = StreamFlixColors.Background,
    surface = StreamFlixColors.Surface,
    surfaceVariant = StreamFlixColors.SurfaceRaised,
    onBackground = StreamFlixColors.Text,
    onSurface = StreamFlixColors.Text,
    onSurfaceVariant = StreamFlixColors.TextMuted,
    outline = StreamFlixColors.Amber.copy(alpha = 0.22f),
    error = Color(0xFFFF6B6B),
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
