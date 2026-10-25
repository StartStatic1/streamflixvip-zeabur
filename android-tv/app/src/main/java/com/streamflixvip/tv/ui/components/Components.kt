package com.streamflixvip.tv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.streamflixvip.tv.ui.theme.LocalPalette

object TmdbImg {
    const val POSTER = "https://image.tmdb.org/t/p/w342"
    const val BACKDROP = "https://image.tmdb.org/t/p/w780"
    const val BACKDROP_LG = "https://image.tmdb.org/t/p/w1280"
    const val LOGO = "https://image.tmdb.org/t/p/w500"
    const val STILL = "https://image.tmdb.org/t/p/w300"
    const val PROFILE = "https://image.tmdb.org/t/p/w185"

    fun poster(path: String?) = path?.takeIf { it.isNotBlank() }?.let { POSTER + it }
    fun backdrop(path: String?) = path?.takeIf { it.isNotBlank() }?.let { BACKDROP + it }
    fun backdropLg(path: String?) = path?.takeIf { it.isNotBlank() }?.let { BACKDROP_LG + it }
}

/** Imagem com fade suave (coil crossfade). */
@Composable
fun FadeImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val request = remember(url) {
        ImageRequest.Builder(context).data(url).crossfade(250).build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}

/** Pôster 2:3 com anel de foco na cor do tema, brilho e leve zoom. */
@Composable
fun PosterCard(
    title: String,
    posterPath: String?,
    modifier: Modifier = Modifier,
    width: Dp = 128.dp,
    badge: String? = null,
    progress: Float? = null,
    onFocus: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(10.dp)
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .width(width)
            .aspectRatio(2f / 3f)
            .onFocusChanged { if (it.isFocused) onFocus() },
        shape = CardDefaults.shape(shape = shape),
        scale = CardDefaults.scale(focusedScale = 1.08f),
        colors = CardDefaults.colors(containerColor = p.surface, focusedContainerColor = p.surface),
        border = CardDefaults.border(
            focusedBorder = Border(border = BorderStroke(3.dp, p.accent), shape = shape),
        ),
        glow = CardDefaults.glow(focusedGlow = Glow(elevationColor = p.accent.copy(alpha = 0.55f), elevation = 14.dp)),
    ) {
        Box(Modifier.fillMaxSize()) {
            val url = TmdbImg.poster(posterPath)
            if (url != null) {
                FadeImage(url, Modifier.fillMaxSize(), contentDescription = title)
            } else {
                Box(Modifier.fillMaxSize().background(p.surfaceHi).padding(10.dp), contentAlignment = Alignment.Center) {
                    Text(title, color = p.text, fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
            if (badge != null) {
                Text(
                    badge,
                    color = p.onAccent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(p.accent)
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
            if (progress != null && progress > 0f) {
                ProgressLine(progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(6.dp))
            }
        }
    }
}

/** Cartao 16:9 (backdrop) com titulo por cima — destaques, continuar assistindo, episodios. */
@Composable
fun LandscapeCard(
    title: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    width: Dp = 248.dp,
    subtitle: String? = null,
    progress: Float? = null,
    showPlayIcon: Boolean = false,
    topBadge: String? = null,
    onFocus: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(12.dp)
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .width(width)
            .aspectRatio(16f / 9f)
            .onFocusChanged { if (it.isFocused) onFocus() },
        shape = CardDefaults.shape(shape = shape),
        scale = CardDefaults.scale(focusedScale = 1.06f),
        colors = CardDefaults.colors(containerColor = p.surface, focusedContainerColor = p.surface),
        border = CardDefaults.border(
            focusedBorder = Border(border = BorderStroke(3.dp, p.accent), shape = shape),
        ),
        glow = CardDefaults.glow(focusedGlow = Glow(elevationColor = p.accent.copy(alpha = 0.5f), elevation = 14.dp)),
    ) {
        Box(Modifier.fillMaxSize()) {
            FadeImage(imageUrl, Modifier.fillMaxSize(), contentDescription = title)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.9f),
                    ),
                ),
            )
            if (showPlayIcon) {
                Box(
                    Modifier.align(Alignment.Center).size(38.dp).clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
            if (topBadge != null) {
                Text(
                    topBadge,
                    color = p.onAccent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(p.accent)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (progress != null && progress > 0f) {
                    Spacer(Modifier.height(5.dp))
                    ProgressLine(progress, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, height: Dp = 3.dp) {
    val p = LocalPalette.current
    Box(modifier.height(height).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.25f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(p.accent))
    }
}

/**
 * Botao padrao da TV: em foco fica BRANCO com texto preto (leitura facil de longe),
 * fora de foco: destaque (primario) ou vidro (secundario).
 */
@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    loading: Boolean = false,
    compact: Boolean = false,
    enabled: Boolean = true,
) {
    val p = LocalPalette.current
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(if (compact) 10.dp else 12.dp)),
        colors = ButtonDefaults.colors(
            containerColor = if (primary) p.accent else p.glassStrong,
            contentColor = if (primary) p.onAccent else p.text,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
            disabledContainerColor = p.glass,
            disabledContentColor = p.textDim,
        ),
        scale = ButtonDefaults.scale(focusedScale = 1.06f),
        glow = ButtonDefaults.glow(focusedGlow = Glow(elevationColor = p.accent.copy(alpha = 0.6f), elevation = 10.dp)),
        contentPadding = if (compact) PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        else PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(16.dp), color = if (primary) p.onAccent else p.accent, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(icon, null, Modifier.size(if (compact) 16.dp else 20.dp))
            Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
        }
        Text(text, fontSize = if (compact) 12.sp else 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Chip selecionavel (filtros, temporadas, categorias). */
@Composable
fun TvChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    onFocus: () -> Unit = {},
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) p.accent.copy(alpha = 0.22f) else p.glass,
            contentColor = if (selected) p.accent else p.text,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, if (selected) p.accent.copy(alpha = 0.7f) else p.glassBorder), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
            if (count != null && count > 0) {
                Spacer(Modifier.width(6.dp))
                Text("$count", fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun MetaTag(text: String, highlight: Boolean = false) {
    val p = LocalPalette.current
    Text(
        text,
        color = if (highlight) p.onAccent else p.text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(if (highlight) p.accent else p.glassStrong)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    val p = LocalPalette.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(p.accent))
        Spacer(Modifier.width(8.dp))
        Text(text, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            Text(trailing, color = p.textMuted, fontSize = 12.sp)
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier, text: String? = null) {
    val p = LocalPalette.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = p.accent, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
            if (text != null) {
                Spacer(Modifier.height(12.dp))
                Text(text, color = p.textMuted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun MessageBox(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    actionFocus: FocusRequester? = null,
    onAction: (() -> Unit)? = null,
) {
    val p = LocalPalette.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text(title, color = p.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            if (message != null) {
                Spacer(Modifier.height(6.dp))
                Text(message, color = p.textMuted, fontSize = 13.sp)
            }
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(16.dp))
                TvButton(
                    actionLabel,
                    onClick = onAction,
                    primary = true,
                    modifier = if (actionFocus != null) Modifier.focusRequester(actionFocus) else Modifier,
                )
            }
        }
    }
}

/**
 * Painel lateral (direita) para escolhas: servidores, legendas, audio...
 * O foco fica preso dentro dele enquanto aberto; Voltar fecha.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SidePanel(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    width: Dp = 380.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    BackHandler(enabled = visible) { onDismiss() }
    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Column(
                Modifier
                    .width(width)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(listOf(p.bgElevated.copy(alpha = 0.97f), p.bg)),
                    )
                    .border(BorderStroke(1.dp, p.glassBorder))
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .focusProperties { exit = { FocusRequester.Cancel } }
                    .focusGroup(),
            ) {
                Text(title, color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (subtitle != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(subtitle, color = p.textMuted, fontSize = 12.sp, maxLines = 2)
                }
                Spacer(Modifier.height(16.dp))
                content()
            }
        }
    }
}

/** Linha clicavel usada nos paineis e em Ajustes. */
@Composable
fun PanelItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    selected: Boolean = false,
    leading: ImageVector? = null,
    trailingText: String? = null,
    onFocus: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(10.dp)
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) p.accent.copy(alpha = 0.16f) else Color.Transparent,
            contentColor = p.text,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                Icon(leading, null, Modifier.size(20.dp), tint = if (focused) Color.Black else if (selected) p.accent else p.textMuted)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        fontSize = 11.sp,
                        color = if (focused) Color.Black.copy(alpha = 0.65f) else p.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailingText != null) {
                Spacer(Modifier.width(8.dp))
                Text(trailingText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (focused) Color.Black else p.accent, maxLines = 1)
            }
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.Check, null, Modifier.size(18.dp), tint = if (focused) Color.Black else p.accent)
            }
        }
    }
}
