package com.streamflixvip.tv.ui.shell

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.streamflixvip.tv.data.LocalWatchProgress
import com.streamflixvip.tv.data.StartScreen
import com.streamflixvip.tv.data.TvActivationManager
import com.streamflixvip.tv.data.TvSettingsStore
import com.streamflixvip.tv.network.LiveChannel
import com.streamflixvip.tv.ui.account.MyListTvScreen
import com.streamflixvip.tv.ui.components.LocalToaster
import com.streamflixvip.tv.ui.explore.ExploreTvScreen
import com.streamflixvip.tv.ui.home.BrowseSection
import com.streamflixvip.tv.ui.home.HomeTvScreen
import com.streamflixvip.tv.ui.livetv.LiveTvScreen
import com.streamflixvip.tv.ui.search.SearchTvScreen
import com.streamflixvip.tv.ui.settings.SettingsTvScreen
import com.streamflixvip.tv.ui.theme.LocalPalette
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ShellSection(val label: String, val icon: ImageVector) {
    SEARCH("Buscar", Icons.Filled.Search),
    HOME("Início", Icons.Filled.Home),
    MOVIES("Filmes", Icons.Filled.Movie),
    SERIES("Séries", Icons.Filled.Tv),
    EXPLORE("Explorar", Icons.Filled.FilterList),
    LIVE("TV ao vivo", Icons.Filled.LiveTv),
    MYLIST("Minha lista", Icons.Filled.Bookmark),
    SETTINGS("Ajustes", Icons.Filled.Settings),
}

private val RAIL_COLLAPSED = 76.dp
private val RAIL_EXPANDED = 224.dp

/**
 * Casco principal: menu lateral que abre ao receber foco (estilo Google TV)
 * + a secao escolhida. Voltar: conteudo -> menu -> Inicio -> sair (2x).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MainShell(
    activationManager: TvActivationManager,
    onSwitchProfile: () -> Unit = {},
    onItemClick: (tmdbId: Int, mediaType: String) -> Unit,
    onContinueClick: (LocalWatchProgress) -> Unit,
    onSeeAll: (rowKey: String) -> Unit,
    onChannelClick: (channel: LiveChannel, list: List<LiveChannel>, index: Int, epgNow: Map<String, String>, epgNext: Map<String, String>) -> Unit,
    onDeactivated: () -> Unit,
) {
    val settings by TvSettingsStore.state.collectAsState()
    val p = LocalPalette.current
    val toaster = LocalToaster.current
    val context = LocalContext.current

    val initial = remember {
        when (settings.startScreen) {
            StartScreen.HOME -> ShellSection.HOME
            StartScreen.LIVE -> ShellSection.LIVE
            StartScreen.LAST -> ShellSection.entries.firstOrNull { it.name.equals(settings.lastSection, true) } ?: ShellSection.HOME
        }
    }
    var sectionName by rememberSaveable { mutableStateOf(initial.name) }
    val visibleSections = remember(settings.kidsMode) {
        if (settings.kidsMode) ShellSection.entries.filter { it != ShellSection.MOVIES && it != ShellSection.SERIES && it != ShellSection.EXPLORE }
        else ShellSection.entries.toList()
    }
    val section = ShellSection.entries.firstOrNull { it.name == sectionName }
        ?.takeIf { it in visibleSections } ?: ShellSection.HOME
    LaunchedEffect(section) { com.streamflixvip.tv.util.CrashLog.screen("main/${section.name}") }

    val focusSignal = remember { FocusSignal() }
    val railRequesters = remember { ShellSection.entries.associateWith { FocusRequester() } }
    var railFocused by remember { mutableStateOf(false) }
    var lastBack by remember { mutableLongStateOf(0L) }
    val stateHolder = rememberSaveableStateHolder()
    val contentRequester = remember { FocusRequester() }
    var contentFocusNonce by remember { mutableIntStateOf(0) }

    /** Leva o foco para a secao aberta (ultimo cartao / primeiro item). */
    fun goToContent() {
        focusSignal.request()
        contentFocusNonce++
    }

    fun select(s: ShellSection) {
        sectionName = s.name
        TvSettingsStore.update { it.copy(lastSection = s.name) }
        goToContent()
    }

    // Rede de seguranca: se a secao nao pegou o foco (ainda carregando, cartao
    // lembrado fora da tela...), entra no primeiro item focavel do conteudo.
    LaunchedEffect(contentFocusNonce) {
        if (contentFocusNonce == 0) return@LaunchedEffect
        repeat(4) {
            delay(if (it == 0) 450 else 600)
            if (!railFocused) return@LaunchedEffect
            runCatching { contentRequester.requestFocus() }
        }
    }

    var showExit by remember { mutableStateOf(false) }
    BackHandler {
        when {
            railFocused && section != ShellSection.HOME -> select(ShellSection.HOME)
            railFocused -> goToContent()
            section != ShellSection.HOME -> runCatching { railRequesters.getValue(section).requestFocus() }
            else -> showExit = true
        }
    }

    if (showExit) {
        ExitConfirmDialog(
            onExit = { showExit = false; (context as? Activity)?.finish() },
            onCancel = { showExit = false },
        )
    }

    Box(Modifier.fillMaxSize().background(p.bg)) {
        // Conteudo da secao (abaixo do menu)
        Box(
            Modifier.fillMaxSize().padding(start = RAIL_COLLAPSED)
                .focusRequester(contentRequester)
                .focusGroup(),
        ) {
            stateHolder.SaveableStateProvider(section.name + if (settings.kidsMode) "_kids" else "") {
                when (section) {
                    ShellSection.HOME -> HomeTvScreen(
                        section = if (settings.kidsMode) BrowseSection.KIDS else BrowseSection.HOME,
                        focusSignal = focusSignal,
                        onItemClick = onItemClick,
                        onContinueClick = onContinueClick,
                        onSeeAll = onSeeAll,
                        onToast = { toaster.show(it) },
                    )
                    ShellSection.MOVIES -> HomeTvScreen(
                        section = BrowseSection.MOVIES,
                        focusSignal = focusSignal,
                        onItemClick = onItemClick,
                        onContinueClick = onContinueClick,
                        onSeeAll = onSeeAll,
                        onToast = { toaster.show(it) },
                    )
                    ShellSection.SERIES -> HomeTvScreen(
                        section = BrowseSection.SERIES,
                        focusSignal = focusSignal,
                        onItemClick = onItemClick,
                        onContinueClick = onContinueClick,
                        onSeeAll = onSeeAll,
                        onToast = { toaster.show(it) },
                    )
                    ShellSection.EXPLORE -> ExploreTvScreen(
                        focusSignal = focusSignal,
                        kidsMode = settings.kidsMode,
                        onItemClick = onItemClick,
                    )
                    ShellSection.SEARCH -> SearchTvScreen(
                        focusSignal = focusSignal,
                        kidsMode = settings.kidsMode,
                        onItemClick = onItemClick,
                    )
                    ShellSection.LIVE -> LiveTvScreen(
                        focusSignal = focusSignal,
                        kidsMode = settings.kidsMode,
                        onChannelClick = onChannelClick,
                    )
                    ShellSection.MYLIST -> MyListTvScreen(
                        focusSignal = focusSignal,
                        onItemClick = onItemClick,
                    )
                    ShellSection.SETTINGS -> SettingsTvScreen(
                        focusSignal = focusSignal,
                        activationManager = activationManager,
                        onSwitchProfile = onSwitchProfile,
                        onDeactivated = onDeactivated,
                    )
                }
            }
            Clock(Modifier.align(Alignment.TopEnd).padding(top = 18.dp, end = 28.dp))
        }

        // Menu aberto: escurece TODO o conteudo (nunca texto do menu sobre texto da tela)
        AnimatedVisibility(visible = railFocused, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)))
        }

        val railWidth by animateDpAsState(if (railFocused) RAIL_EXPANDED else RAIL_COLLAPSED, label = "rail")
        Column(
            Modifier
                .fillMaxHeight()
                .width(railWidth)
                // coluna propria e opaca: o conteudo comeca depois dela (padding start = RAIL_COLLAPSED)
                .background(if (railFocused) p.bgElevated else p.bg)
                .drawBehind {
                    drawRect(
                        color = p.surfaceHi,
                        topLeft = Offset(size.width - 1.dp.toPx(), 0f),
                        size = Size(1.dp.toPx(), size.height),
                    )
                }
                .clipToBounds()
                .padding(vertical = 22.dp, horizontal = 12.dp)
                .onFocusChanged { railFocused = it.hasFocus }
                .focusProperties {
                    enter = { railRequesters.getValue(section) }
                    // So bloqueia as setas. Pedidos de foco do codigo (troca de aba,
                    // voltar do player) PRECISAM passar, senao o foco fica preso aqui.
                    exit = { dir ->
                        when (dir) {
                            FocusDirection.Right -> {
                                goToContent()
                                FocusRequester.Cancel
                            }
                            FocusDirection.Left, FocusDirection.Up, FocusDirection.Down -> FocusRequester.Cancel
                            else -> FocusRequester.Default
                        }
                    }
                }
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BrandMark(expanded = railFocused, kids = settings.kidsMode)
            Spacer(Modifier.height(18.dp))
            visibleSections.forEach { s ->
                if (s == ShellSection.SETTINGS) Spacer(Modifier.weight(1f))
                RailItem(
                    section = s,
                    selected = s == section,
                    expanded = railFocused,
                    modifier = Modifier.focusRequester(railRequesters.getValue(s)),
                    onClick = { select(s) },
                )
            }
        }
    }
}

@Composable
private fun BrandMark(expanded: Boolean, kids: Boolean) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp)) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                .background(if (kids) Brush.linearGradient(listOf(p.accent, p.accent2)) else Brush.linearGradient(listOf(Color.Black, Color.Black))),
            contentAlignment = Alignment.Center,
        ) {
            if (kids) Icon(Icons.Filled.ChildCare, null, tint = p.onAccent, modifier = Modifier.size(22.dp))
            else androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(com.streamflixvip.tv.R.drawable.ic_launcher_fg),
                contentDescription = "StreamFlix",
                modifier = Modifier.size(40.dp),
            )
        }
        if (expanded) {
            Spacer(Modifier.width(12.dp))
            Column {
                Text("StreamFlix", color = p.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(if (kids) "MODO INFANTIL" else "VIP · TV", color = p.accent, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.4.sp)
            }
        }
    }
}

@Composable
private fun RailItem(
    section: ShellSection,
    selected: Boolean,
    expanded: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    // Pilula compacta: so do tamanho do icone + nome (nao a largura toda do menu)
    Surface(
        onClick = onClick,
        modifier = modifier.wrapContentWidth(Alignment.Start).height(44.dp).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) p.accent.copy(alpha = 0.16f) else Color.Transparent,
            contentColor = if (selected) p.accent else p.textMuted,
            focusedContainerColor = p.accent,
            focusedContentColor = p.onAccent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(Modifier.fillMaxHeight().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(section.icon, section.label, Modifier.size(22.dp))
            if (expanded) {
                Spacer(Modifier.width(14.dp))
                Text(
                    section.label,
                    fontSize = 15.sp,
                    fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                )
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}

@Composable
private fun Clock(modifier: Modifier) {
    val p = LocalPalette.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(15_000)
        }
    }
    val fmt = remember { SimpleDateFormat("HH:mm", Locale("pt", "BR")) }
    Text(fmt.format(now), color = p.text.copy(alpha = 0.85f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = modifier)
}


/** "Sair do StreamFlix?" — o foco começa em Cancelar para evitar saída sem querer. */
@Composable
private fun ExitConfirmDialog(onExit: () -> Unit, onCancel: () -> Unit) {
    val p = com.streamflixvip.tv.ui.theme.LocalPalette.current
    val cancelFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.ui.window.Dialog(onDismissRequest = onCancel) {
        androidx.compose.foundation.layout.Column(
            Modifier
                .width(420.dp)
                .background(p.bgElevated, androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                .padding(28.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            androidx.tv.material3.Text("Sair do StreamFlix?", color = p.text, fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 20.dp))
            androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
                ExitButton("Cancelar", Modifier.focusRequester(cancelFocus), onCancel)
                ExitButton("Sair", Modifier, onExit)
            }
        }
    }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { cancelFocus.requestFocus() }
    }
}

@Composable
private fun ExitButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    val p = com.streamflixvip.tv.ui.theme.LocalPalette.current
    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = modifier,
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = p.glass,
            contentColor = p.text,
            focusedContainerColor = p.accent,
            focusedContentColor = p.onAccent,
        ),
    ) {
        androidx.tv.material3.Text(label, modifier = Modifier.padding(horizontal = 30.dp, vertical = 12.dp), fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    }
}
