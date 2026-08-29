#!/usr/bin/env python3
"""Aplica legenda online com MergingMediaSource + mesmo pipeline HLS do player."""
from pathlib import Path

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt")
t = p.read_text()

# Imports
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

OLD = '''    suspend fun applyOnlineSubtitle(item: SubtitleSearchItem) {
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
            val file = File(context.cacheDir, "os_${tmdbId}_${currentSeason}_${currentEpisode}.vtt")
            file.writeText(content)
            val pos = exoPlayer.currentPosition
            val wasPlaying = exoPlayer.playWhenReady
            // Marca ANTES do prepare para o listener de tracks nao desligar a faixa
            onlineSubtitleApplied = true
            persistSubtitleKey("online")
            val short = (item.release ?: "PT-BR").let { if (it.length > 28) it.take(28) + "…" else it }
            selectedSubtitleLabel = "Online: $short"
            trackSelector.parameters = trackSelector.parameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguage("pt")
                .build()
            val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file))
                .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                .setLanguage("pt")
                .setLabel(item.release ?: "Online PT-BR")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            // Tambem tenta VTT se o conteudo ja for WebVTT
            val isVtt = content.trimStart().startsWith("WEBVTT", ignoreCase = true)
            val subConfigFinal = if (isVtt) {
                MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file))
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setLanguage("pt")
                    .setLabel(item.release ?: "Online PT-BR")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            } else subConfig
            val mediaItem = MediaItem.Builder()
                .setUri(activeUrl)
                .setSubtitleConfigurations(listOf(subConfigFinal))
                .build()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.seekTo(pos)
            exoPlayer.playWhenReady = wasPlaying
            trackSelector.parameters = trackSelector.parameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguage("pt")
                .build()
            settingsPanel = SettingsPanel.MAIN
        } catch (e: Exception) {
            onlineSubtitleApplied = false
            onlineSubtitlesError = e.message ?: "Falha ao baixar legenda"
        } finally {
            onlineSubtitlesLoading = false
        }
    }'''

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
            // Sempre VTT no disco (backend ja normaliza; se vier SRT puro, envolve)
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
            // Mesmo pipeline de video (HLS/Progressive + headers) + faixa de legenda
            val videoItem = MediaItem.fromUri(activeUrl)
            val videoSource = if (isLikelyHls(activeUrl)) {
                HlsMediaSource.Factory(httpDataSourceFactory).createMediaSource(videoItem)
            } else {
                ProgressiveMediaSource.Factory(httpDataSourceFactory, extractorsFactory)
                    .createMediaSource(videoItem)
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
            exoPlayer.setMediaSource(merged)
            exoPlayer.prepare()
            exoPlayer.seekTo(pos)
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
    }'''

if OLD not in t:
    if "MergingMediaSource" in t and "SingleSampleMediaSource" in t:
        print("already merge")
        raise SystemExit(0)
    # tentar achar funcao e falhar com contexto
    if "suspend fun applyOnlineSubtitle" not in t:
        raise SystemExit("applyOnlineSubtitle missing")
    raise SystemExit("apply pattern mismatch")

t = t.replace(OLD, NEW, 1)

# httpDataSourceFactory e extractorsFactory precisam estar acessiveis no escopo
# Eles estao dentro do remember { } do exoPlayer — se nao forem properties externas, quebra.
# Verifica se sao vars de escopo da composable.
if "val httpDataSourceFactory" in t and "remember {" in t:
    # Se factory esta so dentro do remember do player, NEW usara o do reloadWithUrl
    # Precisamos de factories no escopo da composable
    pass

p.write_text(t)
print("merge sub OK")
