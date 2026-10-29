package com.streamflixvip.app.ui.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Tempo legível: 1:02:03 / 4:05. */
internal fun chromeTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val t = (ms / 1000).toInt()
    val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Fração segura 0..1 (duração desconhecida = 0). */
internal fun safeFraction(pos: Long, dur: Long): Float =
    if (dur <= 0L) 0f else (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)

/**
 * Linha do tempo: trilho, parte já carregada, parte assistida e bolinha grande.
 * Arrastar mostra o tempo acima da bolinha; tocar pula direto. No controle remoto,
 * com foco, ← → andam 10 s.
 */
@Composable
internal fun PlayerTimeline(
    position: Long,
    buffered: Long,
    duration: Long,
    accent: Color,
    onScrub: (scrubbing: Boolean, targetMs: Long) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onInteract: () -> Unit = {},
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableFloatStateOf(0f) }
    var focused by remember { mutableStateOf(false) }
    val dur by rememberUpdatedState(duration)
    val pos by rememberUpdatedState(position)
    val frac = if (dragging) dragFrac else safeFraction(position, duration)
    val bufFrac = safeFraction(buffered, duration)
    val track by animateDpAsState(if (dragging || focused) 6.dp else 4.dp, label = "track")
    val thumb by animateDpAsState(if (dragging || focused) 22.dp else 16.dp, label = "thumb")
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown || dur <= 0) return@onKeyEvent false
                when (ev.key) {
                    Key.DirectionLeft -> { onInteract(); onSeek((pos - 10_000).coerceAtLeast(0)); true }
                    Key.DirectionRight -> { onInteract(); onSeek((pos + 10_000).coerceAtMost(dur)); true }
                    else -> false
                }
            }
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    if (dur > 0) { onInteract(); onSeek((o.x / size.width * dur).toLong()) }
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        if (dur > 0) {
                            dragging = true
                            dragFrac = (o.x / size.width).coerceIn(0f, 1f)
                            onScrub(true, (dragFrac * dur).toLong())
                        }
                    },
                    onHorizontalDrag = { ch, _ ->
                        if (dragging) {
                            dragFrac = (ch.position.x / size.width).coerceIn(0f, 1f)
                            onScrub(true, (dragFrac * dur).toLong())
                            onInteract()
                        }
                    },
                    onDragEnd = {
                        if (dragging) { onSeek((dragFrac * dur).toLong()); onScrub(false, (dragFrac * dur).toLong()) }
                        dragging = false
                    },
                    onDragCancel = { dragging = false; onScrub(false, pos) },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        Canvas(Modifier.fillMaxWidth().height(thumb)) {
            val h = track.toPx()
            val y = size.height / 2f - h / 2f
            val r = CornerRadius(h / 2f, h / 2f)
            drawRoundRect(Color.White.copy(alpha = 0.22f), Offset(0f, y), Size(size.width, h), r)
            drawRoundRect(Color.White.copy(alpha = 0.45f), Offset(0f, y), Size(size.width * bufFrac, h), r)
            drawRoundRect(accent, Offset(0f, y), Size(size.width * frac, h), r)
            val cx = (size.width * frac).coerceIn(thumb.toPx() / 2f, size.width - thumb.toPx() / 2f)
            drawCircle(Color.Black.copy(alpha = 0.35f), thumb.toPx() / 2f + 2f, Offset(cx, size.height / 2f))
            drawCircle(accent, thumb.toPx() / 2f, Offset(cx, size.height / 2f))
            drawCircle(Color.White, thumb.toPx() / 5f, Offset(cx, size.height / 2f))
        }
        if (dragging && dur > 0) {
            val xDp = with(density) { (widthPx * frac).toDp() }
            Box(
                Modifier
                    .offset(x = (xDp - 34.dp).coerceIn(0.dp, maxWidth - 68.dp), y = (-34).dp)
                    .width(68.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(chromeTime((frac * dur).toLong()), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Botão redondo do player (toque e controle remoto, com anel de foco). */
@Composable
internal fun ChromeIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 24.dp,
    filled: Boolean = false,
    accent: Color = Color.White,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .size(size)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clip(CircleShape)
            .background(
                when {
                    filled -> accent
                    focused -> Color.White.copy(alpha = 0.25f)
                    else -> Color.Black.copy(alpha = 0.28f)
                },
                CircleShape,
            )
            .border(if (focused) 2.dp else 0.dp, if (focused) Color.White else Color.Transparent, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = if (filled) Color.Black else Color.White, modifier = Modifier.size(iconSize))
    }
}

/** Atalho em pílula na barra de baixo (Episódios, Áudio e legenda, Servidor…). */
@Composable
internal fun ChromeChip(
    label: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    accent: Color = Color.White,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(
                when {
                    focused -> Color.White.copy(alpha = 0.28f)
                    highlighted -> accent.copy(alpha = 0.22f)
                    else -> Color.White.copy(alpha = 0.10f)
                },
                shape,
            )
            .border(1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.12f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (highlighted) accent else Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/** Abas do painel lateral. */
@Composable
internal fun <T> PanelTabs(
    tabs: List<Pair<T, String>>,
    selected: T?,
    accent: Color,
    onSelect: (T) -> Unit,
    firstFocus: FocusRequester? = null,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tabs.forEachIndexed { i, (key, label) ->
            var focused by remember { mutableStateOf(false) }
            val sel = key == selected
            val shape = RoundedCornerShape(50)
            Box(
                Modifier
                    .then(if (i == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier)
                    .onFocusChanged { focused = it.isFocused }
                    .clip(shape)
                    .background(if (sel) accent else if (focused) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f), shape)
                    .border(if (focused) 2.dp else 0.dp, if (focused) Color.White else Color.Transparent, shape)
                    .clickable { onSelect(key) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(label, color = if (sel) Color.Black else Color.White, fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

/** Cartão "Próximo episódio em N s" com barra que esvazia. */
@Composable
internal fun NextEpisodeCard(
    label: String,
    secondsLeft: Int,
    totalSeconds: Int,
    accent: Color,
    onPlay: () -> Unit,
    onCancel: () -> Unit,
    playFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(300.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xF0101114))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text("Próximo episódio", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.15f))) {
            Box(Modifier.fillMaxWidth(if (totalSeconds <= 0) 0f else secondsLeft / totalSeconds.toFloat()).height(3.dp).background(accent))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChromeChip("Assistir em ${secondsLeft}s", null, onPlay, highlighted = true, accent = accent, focusRequester = playFocus)
            ChromeChip("Cancelar", null, onCancel)
        }
    }
}

/** Opções do timer para dormir. */
internal enum class SleepTimer(val label: String, val minutes: Int) {
    OFF("Desligado", 0), M15("15 min", 15), M30("30 min", 30), M45("45 min", 45), M60("1 hora", 60), END("Fim do episódio", -1),
}

/** Fundo de clique "invisível" (sem efeito de toque). */
@Composable
internal fun Modifier.noRippleClick(onClick: () -> Unit): Modifier =
    this.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
