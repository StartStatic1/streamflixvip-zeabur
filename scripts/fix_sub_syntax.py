#!/usr/bin/env python3
"""Corrige sintaxe quebrada em applyOnlineSubtitle (WEBVTT string + $1)."""
from pathlib import Path

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt")
t = p.read_text()

start = t.find("    suspend fun applyOnlineSubtitle(item: SubtitleSearchItem) {")
if start < 0:
    raise SystemExit("applyOnlineSubtitle missing")

end = -1
for m in ("\n    fun selectAudio(", "\n    fun selectQuality(", "\n    fun openInExternalPlayer("):
    i = t.find(m, start + 20)
    if i > 0 and (end < 0 or i < end):
        end = i
if end < 0:
    raise SystemExit("end not found")

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
                val srt = content.replace("\\r", "")
                val sb = StringBuilder("WEBVTT\\n\\n")
                for (line in srt.lineSequence()) {
                    if (line.contains("-->")) {
                        sb.append(line.replace(',', '.')).append('\\n')
                    } else {
                        sb.append(line).append('\\n')
                    }
                }
                sb.toString()
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

# Fix: NEW is Python string - we need actual \n and \r in the generated Kotlin.
# Rebuild NEW carefully without double-escaping issues.
NEW = (
"    suspend fun applyOnlineSubtitle(item: SubtitleSearchItem) {\n"
"        val fileId = item.file_id\n"
"        val directUrl = item.url\n"
"        if (fileId == null && directUrl.isNullOrBlank()) {\n"
"            onlineSubtitlesError = \"Legenda sem arquivo\"\n"
"            return\n"
"        }\n"
"        onlineSubtitlesLoading = true\n"
"        onlineSubtitlesError = null\n"
"        try {\n"
"            val seasonArg = if (mediaType == \"tv\" && currentSeason > 0) currentSeason else null\n"
"            val episodeArg = if (mediaType == \"tv\" && currentEpisode > 0) currentEpisode else null\n"
"            val resp = NetworkModule.subtitlesApi.download(\n"
"                fileId = fileId,\n"
"                url = directUrl,\n"
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
"            val body = if (content.trimStart().startsWith(\"WEBVTT\", ignoreCase = true)) {\n"
"                content\n"
"            } else {\n"
"                val srt = content.replace(\"\\r\", \"\")\n"
"                val sb = StringBuilder()\n"
"                sb.append(\"WEBVTT\\n\\n\")\n"
"                for (line in srt.lineSequence()) {\n"
"                    if (line.contains(\"-->\")) {\n"
"                        sb.append(line.replace(',', '.')).append('\\n')\n"
"                    } else {\n"
"                        sb.append(line).append('\\n')\n"
"                    }\n"
"                }\n"
"                sb.toString()\n"
"            }\n"
"            val file = File(context.cacheDir, \"os_${tmdbId}_${currentSeason}_${currentEpisode}.vtt\")\n"
"            file.writeText(body)\n"
"            val pos = exoPlayer.currentPosition\n"
"            val wasPlaying = exoPlayer.playWhenReady\n"
"            onlineSubtitleApplied = true\n"
"            persistSubtitleKey(\"online\")\n"
"            val short = (item.release ?: \"PT-BR\").let { if (it.length > 28) it.take(28) + \"…\" else it }\n"
"            selectedSubtitleLabel = \"Online: $short\"\n"
"            trackSelector.parameters = trackSelector.parameters.buildUpon()\n"
"                .clearOverridesOfType(C.TRACK_TYPE_TEXT)\n"
"                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)\n"
"                .setPreferredTextLanguage(\"pt\")\n"
"                .setSelectUndeterminedTextLanguage(true)\n"
"                .build()\n"
"            val httpDs = DefaultHttpDataSource.Factory()\n"
"                .setUserAgent(\"VLC/3.0.4 LibVLC/3.0.4\")\n"
"                .setDefaultRequestProperties(\n"
"                    mapOf(\n"
"                        \"Referer\" to activeUrl,\n"
"                        \"Connection\" to \"keep-alive\",\n"
"                        \"Icy-MetaData\" to \"1\",\n"
"                    ),\n"
"                )\n"
"            val extractors = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)\n"
"            val videoItem = MediaItem.fromUri(activeUrl)\n"
"            val videoSource = if (isLikelyHls(activeUrl)) {\n"
"                HlsMediaSource.Factory(httpDs).createMediaSource(videoItem)\n"
"            } else {\n"
"                ProgressiveMediaSource.Factory(httpDs, extractors).createMediaSource(videoItem)\n"
"            }\n"
"            val subCfg = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file))\n"
"                .setMimeType(MimeTypes.TEXT_VTT)\n"
"                .setLanguage(\"pt\")\n"
"                .setLabel(item.release ?: \"Online PT-BR\")\n"
"                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)\n"
"                .build()\n"
"            val subSource = SingleSampleMediaSource.Factory(DefaultDataSource.Factory(context))\n"
"                .createMediaSource(subCfg, C.TIME_UNSET)\n"
"            val merged = MergingMediaSource(videoSource, subSource)\n"
"            pendingReapplyTracks = true\n"
"            exoPlayer.setMediaSource(merged)\n"
"            exoPlayer.prepare()\n"
"            if (pos > 1000L) exoPlayer.seekTo(pos)\n"
"            exoPlayer.playWhenReady = wasPlaying\n"
"            trackSelector.parameters = trackSelector.parameters.buildUpon()\n"
"                .clearOverridesOfType(C.TRACK_TYPE_TEXT)\n"
"                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)\n"
"                .setPreferredTextLanguage(\"pt\")\n"
"                .setSelectUndeterminedTextLanguage(true)\n"
"                .build()\n"
"            settingsPanel = SettingsPanel.MAIN\n"
"        } catch (e: Exception) {\n"
"            onlineSubtitleApplied = false\n"
"            onlineSubtitlesError = e.message ?: \"Falha ao baixar legenda\"\n"
"        } finally {\n"
"            onlineSubtitlesLoading = false\n"
"        }\n"
"    }\n"
)

for imp, anchor in [
    ("import androidx.media3.exoplayer.source.MergingMediaSource", "import androidx.media3.exoplayer.source.ProgressiveMediaSource"),
    ("import androidx.media3.exoplayer.source.SingleSampleMediaSource", "import androidx.media3.exoplayer.source.ProgressiveMediaSource"),
    ("import androidx.media3.datasource.DefaultDataSource", "import androidx.media3.datasource.DefaultHttpDataSource"),
]:
    if imp not in t and anchor in t:
        t = t.replace(anchor, anchor + "\n" + imp, 1)

t = t[:start] + NEW + t[end:]
p.write_text(t)
assert "$1.$2" not in t
print("syntax fix OK")
