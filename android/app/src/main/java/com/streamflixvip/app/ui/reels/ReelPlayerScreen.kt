package com.streamflixvip.app.ui.reels

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.streamflixvip.app.network.ReelEpisode

@Composable
fun ReelPlayerScreen(
    session: PendingReelSession,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val episodes = session.episodes.filter { !it.video_url.isNullOrBlank() }
    var index by remember {
        mutableIntStateOf(
            episodes.indexOfFirst { (it.episode ?: 1) == session.startEpisode }.coerceAtLeast(0),
        )
    }
    var showList by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val current: ReelEpisode? = episodes.getOrNull(index)

    BackHandler { if (showList) showList = false else onBack() }

    DisposableEffect(Unit) {
        val activity = context as? Activity
        val prev = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val window = activity?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, view) }
        insets?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            insets?.show(WindowInsetsCompat.Type.systemBars())
            if (prev != null) activity?.requestedOrientation = prev
        }
    }

    val exo = remember {
        val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        ExoPlayer.Builder(context).setRenderersFactory(renderers).build().apply {
            playWhenReady = true
            repeatMode = if (episodes.size <= 1) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_OFF
        }
    }
    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = "Nao foi possivel reproduzir este episodio."
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && index < episodes.lastIndex) {
                    index += 1
                }
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }

    LaunchedEffect(current?.video_url) {
        error = null
        val url = current?.video_url.orEmpty()
        if (url.isBlank()) {
            error = "Este episodio nao tem fonte."
            exo.stop()
        } else {
            exo.setMediaItem(MediaItem.fromUri(url))
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (current != null && error == null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    PlayerView(context).apply {
                        player = exo
                        useController = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
                update = { it.player = exo },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .background(Color(0x66000000))
                .padding(top = 10.dp, start = 4.dp, end = 8.dp, bottom = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        session.story.title ?: "Historia",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                    )
                    val epLabel = if (episodes.size <= 1) "Historia completa" else "EP ${(current?.episode ?: index + 1)} / ${episodes.size}"
                    Text(epLabel, color = Color(0xFFB0B0C8), fontSize = 12.sp)
                }
                if (episodes.size > 1) {
                    IconButton(onClick = { showList = !showList }) {
                        Icon(Icons.Filled.List, contentDescription = "Episodios", tint = Color.White)
                    }
                    if (index < episodes.lastIndex) {
                        IconButton(onClick = { index += 1 }) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "Proximo", tint = Color.White)
                        }
                    }
                }
            }
        }

        if (error != null) {
            Text(
                error ?: "",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }

        if (episodes.isEmpty()) {
            Text(
                "Essa historia ainda nao tem episodio com URL.",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }

        if (showList && episodes.size > 1) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xF214141C), RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .padding(16.dp),
            ) {
                Text("Episodios", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(episodes.size) { i ->
                        val ep = episodes[i]
                        val selected = i == index
                        Box(
                            modifier = Modifier
                                .background(
                                    if (selected) Color(0xFFFF2D55) else Color(0xFF2A2A36),
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable { index = i; showList = false }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${ep.episode ?: i + 1}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
