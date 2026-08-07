#!/usr/bin/env python3
from pathlib import Path
import re

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt")
t = p.read_text()
assert "suspend fun applyOnlineSubtitle" in t

if "import androidx.media3.exoplayer.source.MergingMediaSource" not in t:
    t = t.replace(
        "import androidx.media3.exoplayer.source.ProgressiveMediaSource\n",
        "import androidx.media3.exoplayer.source.ProgressiveMediaSource\n"
        "import androidx.media3.exoplayer.source.MergingMediaSource\n"
        "import androidx.media3.exoplayer.source.SingleSampleMediaSource\n",
    )
if "import androidx.media3.common.Player" not in t:
    t = t.replace(
        "import androidx.media3.common.MediaItem\n",
        "import androidx.media3.common.MediaItem\nimport androidx.media3.common.Player\nimport androidx.media3.common.Tracks\n",
    )

m = re.search(r"    suspend fun applyOnlineSubtitle\(item: SubtitleSearchItem\) \{.*?\n    \}\n\n    fun selectAudio", t, re.S)
if not m:
    m = re.search(r"    suspend fun applyOnlineSubtitle\(item: SubtitleSearchItem\) \{.*?\n    \}\n", t, re.S)
    if not m: raise SystemExit("applyOnlineSubtitle not found")
    end = m.end()
else:
    end = m.end() - len("    fun selectAudio")
new_fn = """    suspend fun applyOnlineSubtitle(item: SubtitleSearchItem) {
        val fileId = item.file_id ?: return
        onlineSubtitlesLoading = true
        onlineSubtitlesError = null
        try {
            val seasonArg = if (mediaType == "tv" && currentSeason > 0) currentSeason else null
            val episodeArg = if (mediaType == "tv" && currentEpisode > 0) currentEpisode else null
            val resp = NetworkModule.subtitlesApi.download(
                fileId = fileId,
                tmdbId = tmdbId,
                mediaType = if (mediaType == "tv") "tv" else "movie",
                season = seasonArg,
                episode = episodeArg,
            )
            val content = resp.content
            if (content.isNullOrBlank()) {
                onlineSubtitlesError = resp.error ?: "Legenda vazia"
                return
            }
            // Garante header WEBVTT valido
            val vtt = if (content.trimStart().startsWith("WEBVTT", ignoreCase = true)) {
                content
            } else {
                "WEBVTT\n\n" + content
                    .replace("\r\n", "\n")
                    .replace(Regex("""(\\d{2}:\\d{2}:\\d{2}),(\\d{3})"""), "$1.$2")
            }
            val file = File(context.cacheDir, "os_${tmdbId}_${currentSeason}_${currentEpisode}.vtt")
            file.writeText(vtt, Charsets.UTF_8)

            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val wasPlaying = exoPlayer.playWhenReady

            val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file))
                .setMimeType(MimeTypes.TEXT_VTT)
                .setLanguage("pt")
                .setLabel(item.release ?: "Online PT-BR")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
                .build()

            val videoItem = MediaItem.fromUri(activeUrl)
            val videoSource = if (isLikelyHls(activeUrl)) {
                HlsMediaSource.Factory(httpDataSourceFactory).createMediaSource(videoItem)
            } else {
                ProgressiveMediaSource.Factory(httpDataSourceFactory, extractorsFactory).createMediaSource(videoItem)
            }
            val subtitleSource = SingleSampleMediaSource.Factory(httpDataSourceFactory)
                .createMediaSource(subConfig, C.TIME_UNSET)
            val merged = MergingMediaSource(videoSource, subtitleSource)

            // Forca legenda de texto ligada e preferencia pt
            trackSelector.parameters = trackSelector.parameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguage("pt")
                .setSelectUndeterminedTextLanguage(true)
                .setIgnoredTextSelectionFlags(0)
                .build()

            exoPlayer.setMediaSource(merged)
            exoPlayer.prepare()
            exoPlayer.seekTo(pos)
            exoPlayer.playWhenReady = wasPlaying

            // Quando as faixas carregarem, seleciona a de texto explicitamente
            exoPlayer.addListener(object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    val groups = tracks.groups
                    for (gi in 0 until groups.size) {
                        val group = groups[gi]
                        if (group.type != C.TRACK_TYPE_TEXT) continue
                        for (ti in 0 until group.length) {
                            if (!group.isTrackSupported(ti)) continue
                            trackSelector.parameters = trackSelector.parameters.buildUpon()
                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, ti))
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .build()
                            exoPlayer.removeListener(this)
                            return
                        }
                    }
                }
            })

            val short = (item.release ?: "PT-BR").let { if (it.length > 28) it.take(28) + "\u2026" else it }
            selectedSubtitleLabel = "Online: $short"
            onlineSubtitleApplied = true
            settingsPanel = SettingsPanel.MAIN
        } catch (e: Exception) {
            onlineSubtitlesError = e.message ?: "Falha ao baixar legenda"
        } finally {
            onlineSubtitlesLoading = false
        }
    }

"""
t = t[:m.start()] + new_fn + t[end:]

p.write_text(t)
print("patched", len(t))
assert "MergingMediaSource" in t
assert "SingleSampleMediaSource" in t
assert "setPreferredTextLanguage" in t
print("ok")
