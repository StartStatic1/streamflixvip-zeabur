#!/usr/bin/env python3
from pathlib import Path
import re

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt")
t = p.read_text()
assert "suspend fun applyOnlineSubtitle" in t

m = re.search(r"    suspend fun applyOnlineSubtitle\(item: SubtitleSearchItem\) \{.*?\n    \}\n\n    fun selectAudio", t, re.S)
if not m:
    m = re.search(r"    suspend fun applyOnlineSubtitle\(item: SubtitleSearchItem\) \{.*?\n    \}\n", t, re.S)
    if not m:
        raise SystemExit("applyOnlineSubtitle not found")
    end = m.end()
else:
    end = m.end() - len("    fun selectAudio")

new_fn = (
    "    suspend fun applyOnlineSubtitle(item: SubtitleSearchItem) {\n"
    "        val fileId = item.file_id ?: return\n"
    "        onlineSubtitlesLoading = true\n"
    "        onlineSubtitlesError = null\n"
    "        try {\n"
    "            val seasonArg = if (mediaType == \"tv\" && currentSeason > 0) currentSeason else null\n"
    "            val episodeArg = if (mediaType == \"tv\" && currentEpisode > 0) currentEpisode else null\n"
    "            val resp = NetworkModule.subtitlesApi.download(\n"
    "                fileId = fileId,\n"
    "                tmdbId = tmdbId,\n"
    "                mediaType = if (mediaType == \"tv\") \"tv\" else \"movie\",\n"
    "                season = seasonArg,\n"
    "                episode = episodeArg,\n"
    "            )\n"
    "            val content = resp.content\n"
    "            if (content.isNullOrBlank()) {\n"
    "                onlineSubtitlesError = resp.error ?: \"Legenda vazia\"\n"
    "                return\n"
    "            }\n"
    "            var vtt = content.replace(\"\\r\\n\", \"\\n\").replace(\"\\r\", \"\\n\")\n"
    "            if (!vtt.trimStart().startsWith(\"WEBVTT\", ignoreCase = true)) {\n"
    "                vtt = \"WEBVTT\\n\\n\" + vtt.replace(\",\", \".\")\n"
    "            }\n"
    "            val file = File(context.cacheDir, \"os_${tmdbId}_${currentSeason}_${currentEpisode}.vtt\")\n"
    "            file.writeText(vtt, Charsets.UTF_8)\n"
    "\n"
    "            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)\n"
    "            val wasPlaying = exoPlayer.playWhenReady\n"
    "\n"
    "            val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file))\n"
    "                .setMimeType(MimeTypes.TEXT_VTT)\n"
    "                .setLanguage(\"pt\")\n"
    "                .setLabel(item.release ?: \"Online PT-BR\")\n"
    "                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)\n"
    "                .build()\n"
    "\n"
    "            val dsFactory = DefaultHttpDataSource.Factory()\n"
    "                .setUserAgent(\"VLC/3.0.4 LibVLC/3.0.4\")\n"
    "                .setDefaultRequestProperties(\n"
    "                    mapOf(\n"
    "                        \"Referer\" to activeUrl,\n"
    "                        \"Connection\" to \"keep-alive\",\n"
    "                        \"Icy-MetaData\" to \"1\",\n"
    "                    ),\n"
    "                )\n"
    "            val extFactory = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)\n"
    "            val videoItem = MediaItem.fromUri(activeUrl)\n"
    "            val videoSource = if (isLikelyHls(activeUrl)) {\n"
    "                HlsMediaSource.Factory(dsFactory).createMediaSource(videoItem)\n"
    "            } else {\n"
    "                ProgressiveMediaSource.Factory(dsFactory, extFactory).createMediaSource(videoItem)\n"
    "            }\n"
    "            val subtitleSource = SingleSampleMediaSource.Factory(dsFactory)\n"
    "                .createMediaSource(subConfig, C.TIME_UNSET)\n"
    "            val merged = MergingMediaSource(videoSource, subtitleSource)\n"
    "\n"
    "            trackSelector.parameters = trackSelector.parameters.buildUpon()\n"
    "                .clearOverridesOfType(C.TRACK_TYPE_TEXT)\n"
    "                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)\n"
    "                .setPreferredTextLanguage(\"pt\")\n"
    "                .setSelectUndeterminedTextLanguage(true)\n"
    "                .build()\n"
    "\n"
    "            exoPlayer.setMediaSource(merged)\n"
    "            exoPlayer.prepare()\n"
    "            exoPlayer.seekTo(pos)\n"
    "            exoPlayer.playWhenReady = wasPlaying\n"
    "\n"
    "            exoPlayer.addListener(object : Player.Listener {\n"
    "                override fun onTracksChanged(tracks: Tracks) {\n"
    "                    val groups = tracks.groups\n"
    "                    for (gi in 0 until groups.size) {\n"
    "                        val group = groups[gi]\n"
    "                        if (group.type != C.TRACK_TYPE_TEXT) continue\n"
    "                        for (ti in 0 until group.length) {\n"
    "                            if (!group.isTrackSupported(ti)) continue\n"
    "                            trackSelector.parameters = trackSelector.parameters.buildUpon()\n"
    "                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, ti))\n"
    "                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)\n"
    "                                .build()\n"
    "                            exoPlayer.removeListener(this)\n"
    "                            return\n"
    "                        }\n"
    "                    }\n"
    "                }\n"
    "            })\n"
    "\n"
    "            val short = (item.release ?: \"PT-BR\").let { if (it.length > 28) it.take(28) + \"...\" else it }\n"
    "            selectedSubtitleLabel = \"Online: $short\"\n"
    "            onlineSubtitleApplied = true\n"
    "            settingsPanel = SettingsPanel.MAIN\n"
    "        } catch (e: Exception) {\n"
    "            onlineSubtitlesError = e.message ?: \"Falha ao baixar legenda\"\n"
    "        } finally {\n"
    "            onlineSubtitlesLoading = false\n"
    "        }\n"
    "    }\n\n"
)
t = t[:m.start()] + new_fn + t[end:]

if "import androidx.media3.exoplayer.source.MergingMediaSource" not in t:
    t = t.replace(
        "import androidx.media3.exoplayer.source.ProgressiveMediaSource\n",
        "import androidx.media3.exoplayer.source.ProgressiveMediaSource\n"
        "import androidx.media3.exoplayer.source.MergingMediaSource\n"
        "import androidx.media3.exoplayer.source.SingleSampleMediaSource\n",
    )

p.write_text(t)
print("patched", len(t))
assert "dsFactory" in t
assert "MergingMediaSource" in t
assert "httpDataSourceFactory).createMediaSource(subConfig" not in t
print("ok")
