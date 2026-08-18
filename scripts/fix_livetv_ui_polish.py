#!/usr/bin/env python3
"""TV Online: remove chip Todos, fix spinner mini-player, label Canais."""
from pathlib import Path
import re

screen = Path("android/app/src/main/java/com/streamflixvip/app/ui/livetv/LiveTvScreen.kt")
vm = Path("android/app/src/main/java/com/streamflixvip/app/ui/livetv/LiveTvViewModel.kt")

st = screen.read_text()

# Remove Todos brand chip block
st2, n = re.subn(
    r"\n\s*item \{\s*\n\s*val all = state\.brandFilter == null\s*\n\s*FilterChip\([\s\S]*?Text\(\"Todos\"[\s\S]*?\n\s*\}\s*\n",
    "\n",
    st,
    count=1,
)
if n:
    st = st2
    print("removed Todos brand chip")
else:
    if 'Text("Todos"' in st and "brandFilter" in st:
        print("WARN: Todos still present")
    else:
        print("Todos brand already gone")

if "onIsPlayingChanged" not in st:
    old = """    var streamIndex by remember(channel?.id) { mutableIntStateOf(0) }
    var loading by remember(channel?.id) { mutableStateOf(true) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = true }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    LaunchedEffect(channel?.id, streamIndex) {
        if (channel == null || streams.isEmpty()) {
            exoPlayer.stop()
            loading = false
            return@LaunchedEffect
        }
        loading = true
        val url = streams.getOrNull(streamIndex)?.url ?: return@LaunchedEffect
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) loading = false
            }
            override fun onPlayerError(error: PlaybackException) {
                val next = streamIndex + 1
                if (next < streams.size) streamIndex = next
                else loading = false
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }"""
    new = """    var streamIndex by remember(channel?.id) { mutableIntStateOf(0) }
    var loading by remember(channel?.id) { mutableStateOf(channel != null) }
    var isPlaying by remember { mutableStateOf(false) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = true }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    LaunchedEffect(channel?.id, streamIndex) {
        if (channel == null || streams.isEmpty()) {
            exoPlayer.stop()
            loading = false
            isPlaying = false
            return@LaunchedEffect
        }
        val url = streams.getOrNull(streamIndex)?.url ?: return@LaunchedEffect
        val currentUri = exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
        if (currentUri == url &&
            (exoPlayer.playbackState == Player.STATE_READY || exoPlayer.isPlaying)
        ) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        isPlaying = false
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> loading = false
                    Player.STATE_BUFFERING -> {
                        if (!isPlaying && !exoPlayer.isPlaying) loading = true
                    }
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) loading = false
            }
            override fun onPlayerError(error: PlaybackException) {
                val next = streamIndex + 1
                if (next < streams.size) {
                    streamIndex = next
                    loading = true
                } else {
                    loading = false
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }"""
    if old in st:
        st = st.replace(old, new, 1)
        print("player logic patched")
    else:
        print("WARN player logic pattern not found")
else:
    print("player isPlaying already")

if "loading && !isPlaying && channel != null" not in st:
    st = st.replace(
        "if (loading && channel != null)",
        "if (loading && !isPlaying && channel != null)",
        1,
    )
    print("spinner guard updated")
else:
    print("spinner guard ok")

screen.write_text(st)

vt = vm.read_text()
vt2 = vt.replace('"all" to "Todos"', '"all" to "Canais"', 1)
vt2 = vt2.replace('LiveCategory("all", "Todos")', 'LiveCategory("all", "Canais")', 1)
vm.write_text(vt2)
print("VM labels", "Canais" if "Canais" in vt2 else "miss")
print("ALL DONE")
