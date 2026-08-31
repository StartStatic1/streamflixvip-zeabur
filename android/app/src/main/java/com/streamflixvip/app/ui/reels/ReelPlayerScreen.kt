package com.streamflixvip.app.ui.reels

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

private fun reelPrefs(context: Context) =
    context.getSharedPreferences("sfv_reels", Context.MODE_PRIVATE)

@Composable
fun ReelPlayerScreen(
    session: PendingReelSession,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val prefs = remember { reelPrefs(context) }
    val storyId = session.story.id
    val episodes = session.episodes.filter { !it.video_url.isNullOrBlank() }
    var index by remember {
        val saved = prefs.getInt("idx_$storyId", -1)
        val start = if (saved >= 0 && !prefs.getBoolean("done_$storyId", false)) saved else 0
        mutableIntStateOf(start.coerceIn(0, (episodes.size - 1).coerceAtLeast(0)))
    }
    var showList by remember { mutableStateOf(false) }
    var liked by remember { mutableStateOf(prefs.getBoolean("like_$storyId", false)) }
    var error by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var paused by remember { mutableStateOf(false) }
    val current = episodes.getOrNull(index)

    BackHandler { if (showList) showList = false else onBack() }

    DisposableEffect(Unit) {
        val activity = context as? Activity
        val prev = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val insets = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        insets?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            insets?.show(WindowInsetsCompat.Type.systemBars())
            if (prev != null) activity?.requestedOrientation = prev
        }
    }

    val exo = remember {
        ExoPlayer.Builder(context)
            .setRenderersFactory(DefaultRenderersFactory(context).setEnableDecoderFallback(true))
            .build()
            .apply { playWhenReady = true }
    }
    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = "Nao foi possivel reproduzir."
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                paused = !isPlaying
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state != Player.STATE_ENDED) return
                if (index < episodes.lastIndex) {
                    index += 1
                } else {
                    prefs.edit().putBoolean("done_$storyId", true).remove("idx_$storyId").apply()
                }
            }
        }
        exo.addListener(listener)
        onDispose {
            val finished = prefs.getBoolean("done_$storyId", false) ||
                (exo.playbackState == Player.STATE_ENDED && index >= episodes.lastIndex) ||
                (exo.duration > 0 && index >= episodes.lastIndex && exo.currentPosition > exo.duration * 0.92)
            val ed = prefs.edit()
            if (finished) {
                ed.putBoolean("done_$storyId", true).remove("idx_$storyId")
            } else {
                ed.putBoolean("done_$storyId", false)
                    .putLong("pos_${storyId}_$index", exo.currentPosition)
                    .putInt("idx_$storyId", index)
            }
            ed.apply()
            exo.removeListener(listener)
            exo.release()
        }
    }

    LaunchedEffect(current?.video_url, index) {
        error = null
        val url = current?.video_url.orEmpty()
        if (url.isBlank()) {
            error = "Este episodio nao tem fonte."
            exo.stop()
        } else {
            exo.setMediaItem(MediaItem.fromUri(url))
            exo.prepare()
            val resume = if (prefs.getBoolean("done_$storyId", false)) 0L else prefs.getLong("pos_${storyId}_$index", 0L)
            if (resume > 3_000L) exo.seekTo(resume)
            exo.playWhenReady = true
        }
    }

    LaunchedEffect(exo) {
        while (true) {
            val dur = exo.duration
            progress = if (dur > 0) (exo.currentPosition.toFloat() / dur).coerceIn(0f, 1f) else 0f
            delay(400)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { if (exo.isPlaying) exo.pause() else exo.play() })
            },
    ) {
        if (current != null && error == null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    PlayerView(context).apply {
                        player = exo
                        useController = false
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

        if (paused) {
            Box(
                Modifier.align(Alignment.Center).clip(CircleShape).background(Color(0x66000000)).padding(16.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopStart).padding(top = 8.dp, start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(session.story.title ?: "Historia", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
                Text(
                    if (episodes.size <= 1) "Historia completa" else "EP ${(current?.episode ?: index + 1)} / ${episodes.size}",
                    color = Color(0xCCFFFFFF),
                    fontSize = 11.sp,
                )
            }
            IconButton(onClick = {
                liked = !liked
                prefs.edit().putBoolean("like_$storyId", liked).apply()
            }) {
                Icon(
                    if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Favoritar",
                    tint = if (liked) Color(0xFFFF2D55) else Color.White,
                )
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

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
            color = Color(0xFFFF2D55),
            trackColor = Color(0x33FFFFFF),
        )

        if (error != null) {
            Text(error ?: "", color = Color.White, modifier = Modifier.align(Alignment.Center).padding(24.dp))
        }
        if (episodes.isEmpty()) {
            Text("Essa historia ainda nao tem episodio com URL.", color = Color.White, modifier = Modifier.align(Alignment.Center).padding(24.dp))
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
                        val selected = i == index
                        Box(
                            modifier = Modifier
                                .background(if (selected) Color(0xFFFF2D55) else Color(0xFF2A2A36), RoundedCornerShape(8.dp))
                                .clickable { index = i; showList = false }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${episodes[i].episode ?: i + 1}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
