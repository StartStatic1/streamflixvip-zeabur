#!/usr/bin/env python3
from pathlib import Path
p = Path("android-tv/app/src/main/java/com/streamflixvip/tv/ui/livetv/LivePlayerTvScreen.kt")
t = p.read_text()
if "setConnectTimeoutMs" in t:
    print("already"); raise SystemExit(0)
if len(t) < 1000 or "LivePlayerTvScreen" not in t:
    raise SystemExit("LivePlayer corrupted")
old_a = "    val activeStreams = activeChannel?.streams?.takeIf { it.isNotEmpty() } ?: streams\n"
new_a = "    val activeStreams = (activeChannel?.streams?.takeIf { it.isNotEmpty() } ?: streams)\n        .sortedBy { it.priority ?: 100 }\n"
if old_a in t:
    t = t.replace(old_a, new_a, 1)
old = (
"        val httpFactory = DefaultHttpDataSource.Factory()\n"
"            .setUserAgent(\"VLC/3.0.4 LibVLC/3.0.4\")\n"
"            .setAllowCrossProtocolRedirects(true)\n"
"            .setDefaultRequestProperties(mapOf(\"Referer\" to url, \"Connection\" to \"keep-alive\", \"Icy-MetaData\" to \"1\"))\n"
"        val mediaItem = MediaItem.fromUri(url)\n"
"        val source = if (url.contains(\".m3u8\") || url.contains(\"/live/\")) {\n"
"            HlsMediaSource.Factory(httpFactory).createMediaSource(mediaItem)\n"
"        } else {\n"
"            ProgressiveMediaSource.Factory(httpFactory, DefaultExtractorsFactory()).createMediaSource(mediaItem)\n"
"        }\n"
"        exoPlayer.setMediaSource(source); exoPlayer.prepare(); exoPlayer.playWhenReady = true\n"
"    }\n"
)
new = (
"        val httpFactory = DefaultHttpDataSource.Factory()\n"
"            .setUserAgent(\"VLC/3.0.4 LibVLC/3.0.4\")\n"
"            .setAllowCrossProtocolRedirects(true)\n"
"            .setConnectTimeoutMs(10000)\n"
"            .setReadTimeoutMs(12000)\n"
"            .setDefaultRequestProperties(mapOf(\"Referer\" to url, \"Connection\" to \"keep-alive\", \"Icy-MetaData\" to \"1\"))\n"
"        val mediaItem = MediaItem.fromUri(url)\n"
"        val source = if (url.contains(\".m3u8\") || url.contains(\"/live/\")) {\n"
"            HlsMediaSource.Factory(httpFactory).createMediaSource(mediaItem)\n"
"        } else {\n"
"            ProgressiveMediaSource.Factory(httpFactory, DefaultExtractorsFactory()).createMediaSource(mediaItem)\n"
"        }\n"
"        exoPlayer.setMediaSource(source); exoPlayer.prepare(); exoPlayer.playWhenReady = true\n"
"\n"
"        // Timeout: se nao ficar READY em 14s, tenta proxima fonte\n"
"        delay(14000)\n"
"        if (isLoading && streamIndex < activeStreams.size - 1) {\n"
"            streamIndex += 1\n"
"        } else if (isLoading && streamIndex >= activeStreams.size - 1) {\n"
"            isLoading = false\n"
"            errorMessage = \"Nao foi possivel abrir este canal em nenhuma fonte.\"\n"
"        }\n"
"    }\n"
)
if old not in t:
    raise SystemExit("player anchor not found")
p.write_text(t.replace(old, new, 1))
print("ok fallback")
