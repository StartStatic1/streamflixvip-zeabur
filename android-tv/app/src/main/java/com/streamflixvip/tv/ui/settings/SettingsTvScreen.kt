package com.streamflixvip.tv.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.streamflixvip.tv.BuildConfig
import com.streamflixvip.tv.data.AccentTheme
import com.streamflixvip.tv.data.LocalLibraryStore
import com.streamflixvip.tv.data.StartScreen
import com.streamflixvip.tv.data.SubBackground
import com.streamflixvip.tv.data.SubColor
import com.streamflixvip.tv.data.SubSize
import com.streamflixvip.tv.data.TvActivationManager
import com.streamflixvip.tv.data.TvSettingsStore
import com.streamflixvip.tv.data.nextOf
import com.streamflixvip.tv.ui.components.LocalToaster
import com.streamflixvip.tv.ui.components.PanelItem
import com.streamflixvip.tv.ui.shell.FocusSignal
import com.streamflixvip.tv.ui.theme.LocalPalette
import com.streamflixvip.tv.ui.theme.paletteFor
import kotlinx.coroutines.delay

/** Ajustes: aparencia (cor do tema), modos, legendas, conta e sobre. */
@Composable
fun SettingsTvScreen(
    focusSignal: FocusSignal,
    activationManager: TvActivationManager,
    onDeactivated: () -> Unit,
) {
    val p = LocalPalette.current
    val s by TvSettingsStore.state.collectAsState()
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val library = remember { LocalLibraryStore(context) }
    val firstFocus = remember { FocusRequester() }
    var handledToken by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var crashSummary by remember { mutableStateOf(com.streamflixvip.tv.util.CrashLog.lastSummary(context)) }

    LaunchedEffect(focusSignal.token) {
        if (handledToken == focusSignal.token) return@LaunchedEffect
        delay(60)
        if (runCatching { firstFocus.requestFocus() }.isSuccess) handledToken = focusSignal.token
    }
    LaunchedEffect(confirm) {
        if (confirm != null) {
            delay(4000)
            confirm = null
        }
    }

    fun onOff(v: Boolean) = if (v) "Ligado" else "Desligado"

    TvLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 36.dp, end = 120.dp, top = 28.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Text("Ajustes", color = p.text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(18.dp))
        }

        item { GroupTitle("Aparência", "Escolha a cor do app — muda na hora") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                AccentTheme.entries.forEachIndexed { i, theme ->
                    ThemeSwatch(
                        theme = theme,
                        selected = s.accent == theme,
                        modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                        onClick = { TvSettingsStore.update { it.copy(accent = theme) } },
                    )
                }
            }
        }

        item { GroupTitle("Modos") }
        item {
            PanelItem(
                title = "Modo Infantil",
                subtitle = "Só desenhos e filmes para crianças, sem Filmes/Séries adultos e canais infantis na TV ao vivo",
                leading = Icons.Filled.ChildCare,
                trailingText = onOff(s.kidsMode),
                onClick = {
                    TvSettingsStore.update { it.copy(kidsMode = !it.kidsMode) }
                    toaster.show(if (!s.kidsMode) "Modo Infantil ligado" else "Modo Infantil desligado")
                },
            )
        }
        item {
            PanelItem(
                title = "Abrir o app em",
                subtitle = "Tela que aparece depois da abertura",
                leading = if (s.startScreen == StartScreen.LIVE) Icons.Filled.LiveTv else Icons.Filled.Home,
                trailingText = s.startScreen.label,
                onClick = { TvSettingsStore.update { it.copy(startScreen = nextOf(it.startScreen, StartScreen.entries)) } },
            )
        }
        item {
            PanelItem(
                title = "Próximo episódio automático",
                subtitle = "No fim do episódio, começa o próximo depois de 10 segundos",
                leading = Icons.Filled.SkipNext,
                trailingText = onOff(s.autoplayNext),
                onClick = { TvSettingsStore.update { it.copy(autoplayNext = !it.autoplayNext) } },
            )
        }
        item {
            PanelItem(
                title = "Prévia ao vivo na TV",
                subtitle = if (com.streamflixvip.tv.util.DeviceProfile.isLowRam) "Este aparelho tem pouca memória: deixe desligado para o app não fechar"
                else "Toca o canal em uma janelinha ao passar pela lista (desligue se a TV box for fraca)",
                leading = Icons.Filled.LiveTv,
                trailingText = onOff(s.livePreview),
                onClick = { TvSettingsStore.update { it.copy(livePreview = !it.livePreview) } },
            )
        }

        item { GroupTitle("Legendas") }
        item {
            PanelItem(
                title = "Legenda automática em português",
                subtitle = "Se o áudio não for em português, procura e liga uma legenda PT-BR sozinho",
                leading = Icons.Filled.Subtitles,
                trailingText = onOff(s.autoSubtitles),
                onClick = { TvSettingsStore.update { it.copy(autoSubtitles = !it.autoSubtitles) } },
            )
        }
        item {
            PanelItem(
                title = "Tamanho",
                leading = Icons.Filled.FormatSize,
                trailingText = s.subSize.label,
                onClick = { TvSettingsStore.update { it.copy(subSize = nextOf(it.subSize, SubSize.entries)) } },
            )
        }
        item {
            PanelItem(
                title = "Cor",
                leading = Icons.Filled.FormatColorText,
                trailingText = s.subColor.label,
                onClick = { TvSettingsStore.update { it.copy(subColor = nextOf(it.subColor, SubColor.entries)) } },
            )
        }
        item {
            PanelItem(
                title = "Fundo",
                leading = Icons.Filled.ClosedCaption,
                trailingText = s.subBackground.label,
                onClick = { TvSettingsStore.update { it.copy(subBackground = nextOf(it.subBackground, SubBackground.entries)) } },
            )
        }
        item { SubtitlePreview(s.subSize, s.subColor, s.subBackground) }

        item { GroupTitle("Conta e aparelho") }
        item {
            val active = activationManager.isActivatedLocally
            Column(
                Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(p.accent.copy(alpha = 0.18f), p.surface)))
                    .padding(18.dp),
            ) {
                Text(if (active) "Aparelho ativado" else "Aparelho não ativado", color = if (active) p.success else p.textMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("Plano: ${activationManager.planLabel ?: "VIP"}", color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("ID do aparelho: ${activationManager.deviceId.take(12)}…", color = p.textMuted, fontSize = 12.sp)
            }
        }
        item {
            PanelItem(
                title = if (confirm == "progress") "Confirmar: apagar histórico?" else "Limpar Continuar assistindo",
                subtitle = "Apaga o progresso salvo nesta TV",
                leading = Icons.Filled.DeleteSweep,
                onClick = {
                    if (confirm == "progress") {
                        library.clearAllProgress(); confirm = null
                        toaster.show("Histórico apagado")
                    } else confirm = "progress"
                },
            )
        }
        item {
            PanelItem(
                title = if (confirm == "list") "Confirmar: esvaziar Minha lista?" else "Limpar Minha lista",
                leading = Icons.Filled.PlaylistRemove,
                onClick = {
                    if (confirm == "list") {
                        library.clearAllFavorites(); confirm = null
                        toaster.show("Minha lista esvaziada")
                    } else confirm = "list"
                },
            )
        }
        item {
            PanelItem(
                title = if (confirm == "logout") "Confirmar: desativar esta TV?" else "Desativar este aparelho",
                subtitle = "Vai pedir o código de ativação de novo",
                leading = Icons.AutoMirrored.Filled.Logout,
                onClick = {
                    if (confirm == "logout") {
                        activationManager.clearLocalActivation()
                        onDeactivated()
                    } else confirm = "logout"
                },
            )
        }

        item { GroupTitle("Sobre") }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)) {
                Icon(Icons.Filled.Info, null, tint = p.textMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "StreamFlixVIP TV  v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    color = p.textMuted,
                    fontSize = 13.sp,
                )
            }
        }
        // ultimo fechamento inesperado (fica so no aparelho) - ajuda a achar bug no Fire TV
        val crash = crashSummary
        if (crash != null) {
            item {
                PanelItem(
                    title = "Último erro do app",
                    subtitle = crash,
                    leading = Icons.Filled.Info,
                    trailingText = "Limpar",
                    onClick = {
                        com.streamflixvip.tv.util.CrashLog.clear(context)
                        crashSummary = null
                    },
                )
            }
        }
    }
}

@Composable
private fun GroupTitle(title: String, subtitle: String? = null) {
    val p = LocalPalette.current
    Column(Modifier.padding(top = 18.dp, bottom = 6.dp)) {
        Text(title.uppercase(), color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        if (subtitle != null) Text(subtitle, color = p.textMuted, fontSize = 12.sp)
    }
}

@Composable
private fun ThemeSwatch(theme: AccentTheme, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val tp = paletteFor(theme)
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.width(112.dp).height(78.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = p.surface, focusedContainerColor = p.surfaceHi),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) tp.accent else p.glassBorder), shape = shape),
            focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(Brush.linearGradient(listOf(tp.accent, tp.accent2))), contentAlignment = Alignment.Center) {
                    if (selected) Icon(Icons.Filled.Check, null, tint = tp.onAccent, modifier = Modifier.size(16.dp))
                }
            }
            Text(theme.label, color = p.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SubtitlePreview(size: SubSize, color: SubColor, bg: SubBackground) {
    val p = LocalPalette.current
    Box(
        Modifier.fillMaxWidth().height(96.dp).padding(vertical = 8.dp).clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2B3A55), Color(0xFF15202F)))),
        contentAlignment = Alignment.Center,
    ) {
        SubtitleText("Assim fica a sua legenda", size, color, bg)
        Text("Prévia", color = p.textMuted, fontSize = 10.sp, modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
    }
}

/** Texto de legenda com o estilo escolhido (usado tambem no player). */
@Composable
fun SubtitleText(text: String, size: SubSize, color: SubColor, bg: SubBackground, modifier: Modifier = Modifier) {
    SubtitleText(androidx.compose.ui.text.AnnotatedString(text), size, color, bg, modifier)
}

/** Mesma legenda, aceitando italico/negrito (falas da legenda online). */
@Composable
fun SubtitleText(text: androidx.compose.ui.text.AnnotatedString, size: SubSize, color: SubColor, bg: SubBackground, modifier: Modifier = Modifier) {
    val shadow = when (bg) {
        SubBackground.OUTLINE -> Shadow(Color.Black, blurRadius = 6f)
        SubBackground.SHADOW -> Shadow(Color.Black, offset = androidx.compose.ui.geometry.Offset(3f, 3f), blurRadius = 4f)
        else -> null
    }
    Text(
        text,
        style = TextStyle(
            color = Color(color.argb),
            fontSize = size.sp.sp,
            fontWeight = FontWeight.SemiBold,
            shadow = shadow,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = (size.sp * 1.25f).sp,
        ),
        modifier = modifier.then(
            if (bg == SubBackground.BOX) Modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 10.dp, vertical = 4.dp)
            else Modifier,
        ),
    )
}
