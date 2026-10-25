package com.streamflixvip.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.streamflixvip.tv.data.AccentTheme
import com.streamflixvip.tv.data.TvSettingsStore

/**
 * Paleta "StreamFlix Noir": fundo quase preto azulado + uma cor de destaque
 * escolhida pelo usuario (Ajustes > Aparencia). Todas as telas leem daqui.
 */
@Immutable
data class TvPalette(
    val accent: Color,
    val accent2: Color,
    val onAccent: Color,
    val bg: Color = Color(0xFF07090F),
    val bgElevated: Color = Color(0xFF0E121B),
    val surface: Color = Color(0xFF141925),
    val surfaceHi: Color = Color(0xFF1C2232),
    val text: Color = Color(0xFFF4F6FB),
    val textMuted: Color = Color(0xFF9AA3B5),
    val textDim: Color = Color(0xFF636B7D),
    val glass: Color = Color.White.copy(alpha = 0.07f),
    val glassStrong: Color = Color.White.copy(alpha = 0.13f),
    val glassBorder: Color = Color.White.copy(alpha = 0.12f),
    val star: Color = Color(0xFFFBBF24),
    val live: Color = Color(0xFFEF4444),
    val success: Color = Color(0xFF34D399),
)

fun paletteFor(theme: AccentTheme): TvPalette = when (theme) {
    AccentTheme.AURORA -> TvPalette(Color(0xFF22D3EE), Color(0xFF6366F1), Color(0xFF041016))
    AccentTheme.RUBI -> TvPalette(Color(0xFFF43F5E), Color(0xFFFB923C), Color.White)
    AccentTheme.ESMERALDA -> TvPalette(Color(0xFF34D399), Color(0xFF22D3EE), Color(0xFF03140D))
    AccentTheme.OURO -> TvPalette(Color(0xFFFBBF24), Color(0xFFF97316), Color(0xFF1A1000))
    AccentTheme.VIOLETA -> TvPalette(Color(0xFFA78BFA), Color(0xFFEC4899), Color(0xFF12052A))
}

val LocalPalette = staticCompositionLocalOf { paletteFor(AccentTheme.AURORA) }

@Composable
fun StreamFlixTvTheme(
    content: @Composable () -> Unit,
) {
    val settings by TvSettingsStore.state.collectAsState()
    val palette = remember(settings.accent) { paletteFor(settings.accent) }
    val scheme = remember(palette) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            secondary = palette.accent2,
            background = palette.bg,
            surface = palette.surface,
            surfaceVariant = palette.surfaceHi,
            onSurface = palette.text,
            onBackground = palette.text,
            border = palette.accent,
        )
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
