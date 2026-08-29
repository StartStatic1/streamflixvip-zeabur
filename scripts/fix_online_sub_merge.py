#!/usr/bin/env python3
"""Aplica legenda online com MergingMediaSource + factories locais (como reloadWithUrl)."""
from pathlib import Path

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt")
t = p.read_text()

for imp, anchor in [
    (
        "import androidx.media3.exoplayer.source.MergingMediaSource",
        "import androidx.media3.exoplayer.source.ProgressiveMediaSource",
    ),
    (
        "import androidx.media3.exoplayer.source.SingleSampleMediaSource",
        "import androidx.media3.exoplayer.source.ProgressiveMediaSource",
    ),
    (
        "import androidx.media3.datasource.DefaultDataSource",
        "import androidx.media3.datasource.DefaultHttpDataSource",
    ),
]:
    if imp not in t and anchor in t:
        t = t.replace(anchor, anchor + "\n" + imp, 1)

# Localizar funcao applyOnlineSubtitle e substituir corpo inteiro
start = t.find("    suspend fun applyOnlineSubtitle(item: SubtitleSearchItem) {")
if start < 0:
    raise SystemExit("applyOnlineSubtitle missing")
# fim: proxima fun no mesmo nivel
end_markers = [
    "\n    fun selectAudio(",
    "\n    fun selectQuality(",
    "\n    fun openInExternalPlayer(",
]
end = -1
for m in end_markers:
    i = t.find(m, start + 10)
    if i > 0 and (end < 0 or i < end):
        end = i
if end < 0:
    raise SystemExit("end of applyOnlineSubtitle not found")

NEW = '''    suspend fun applyOnlineSubtitle(item: SubtitleSearchItem) {
        val fileId = item.file_id
        val directUrl = item.url
        if (fileId == null && directUrl.isNullOrBlank()) {
            onlineSubtitlesError = "Legenda sem arquivo"
            return
        }
        onlineSubtitlesLoading = true
        onlineSubtitlesError = null
        try {
            val seasonArg = if (mediaType == "tv" && currentSeason > 0) currentSeason else null
            val episodeArg = if (mediaType == "tv" && currentEpisode > 0) currentEpisode else null
            val resp = NetworkModule.subtitlesApi.download(
                fileId = fileId,
                url = directUrl,
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
            val body = if (content.trimStart().startsWith("WEBVTT", ignoreCase = true)) {
                content
            } else {
                "WEBVTT\n\n" + content
                    .replace("\r", "")
                    .replace(Regex("(\\d{2}:\\d{2}:\\d{2}),(\\d{3})"), "$1.$2")
            }
            val file = File(context.cacheDir, "os_${tmdbId}_${currentSeason}_${currentEpisode}.vtt")
            file.writeText(body)
            val pos = exoPlayer.currentPosition
            val wasPlaying = exoPlayer.playWhenReady
            onlineSubtitleApplied = true
            persistSubtitleKey("online")
            val short = (item.release ?: "PT-BR").let { if (it.length > 28) it.take(28) + "…" else it }
            selectedSubtitleLabel = "Online: $short"
            trackSelector.parameters = trackSelector.parameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguage("pt")
                .setSelectUndeterminedTextLanguage(true)
                .build()
            val httpDs = DefaultHttpDataSource.Factory()
                .setUserAgent("VLC/3.0.4 LibVLC/3.0.4")
                .setDefaultRequestProperties(
                    mapOf(
                        "Referer" to activeUrl,
                        "Connection" to "keep-alive",
                        "Icy-MetaData" to "1",
                    ),
                )
            val extractors = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
            val videoItem = MediaItem.fromUri(activeUrl)
            val videoSource = if (isLikelyHls(activeUrl)) {
                HlsMediaSource.Factory(httpDs).createMediaSource(videoItem)
            } else {
                ProgressiveMediaSource.Factory(httpDs, extractors).createMediaSource(videoItem)
            }
            val subCfg = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file))
                .setMimeType(MimeTypes.TEXT_VTT)
                .setLanguage("pt")
                .setLabel(item.release ?: "Online PT-BR")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            val subSource = SingleSampleMediaSource.Factory(DefaultDataSource.Factory(context))
                .createMediaSource(subCfg, C.TIME_UNSET)
            val merged = MergingMediaSource(videoSource, subSource)
            pendingReapplyTracks = true
            exoPlayer.setMediaSource(merged)
            exoPlayer.prepare()
            if (pos > 1000L) exoPlayer.seekTo(pos)
            exoPlayer.playWhenReady = wasPlaying
            trackSelector.parameters = trackSelector.parameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguage("pt")
                .setSelectUndeterminedTextLanguage(true)
                .build()
            settingsPanel = SettingsPanel.MAIN
        } catch (e: Exception) {
            onlineSubtitleApplied = false
            onlineSubtitlesError = e.message ?: "Falha ao baixar legenda"
        } finally {
            onlineSubtitlesLoading = false
        }
    }
'''

t = t[:start] + NEW + t[end:]
p.write_text(t)
print("merge sub OK", "MergingMediaSource" in t)
