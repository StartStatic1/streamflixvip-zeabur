#!/usr/bin/env python3
"""Corrige legenda online no PlayerScreen:
- aceita resultados com url (fallback Stremio) alem de file_id
- marca onlineSubtitleApplied ANTES do prepare (evita race que desliga faixa)
- preferencia de idioma pt no TrackSelector
"""
from pathlib import Path

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt")
if not p.exists():
    raise SystemExit("PlayerScreen.kt missing")
t = p.read_text()

# 1) reapply: se pref online, mantem texto ligado
OLD_REAPPLY = '''        if (!onlineSubtitleApplied) {
            val subPref = preferredSubtitleKey
            if (subPref.isBlank() || subPref == "off") {
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                selectedSubtitleLabel = "Desligada"
            } else {
                val match = subs.firstOrNull { trackMatchesPref(it.label, subPref) }
                if (match != null) {
                    builder.setOverrideForType(TrackSelectionOverride(match.group, match.trackIndex))
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    selectedSubtitleLabel = "Stream: ${match.label}"
                }
            }
        }'''

NEW_REAPPLY = '''        if (!onlineSubtitleApplied) {
            val subPref = preferredSubtitleKey
            when {
                subPref.isBlank() || subPref == "off" -> {
                    builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    selectedSubtitleLabel = "Desligada"
                }
                subPref == "online" -> {
                    // Legenda externa em carga / re-prepare — nao desligar texto
                    builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setPreferredTextLanguage("pt")
                }
                else -> {
                    val match = subs.firstOrNull { trackMatchesPref(it.label, subPref) }
                    if (match != null) {
                        builder.setOverrideForType(TrackSelectionOverride(match.group, match.trackIndex))
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        selectedSubtitleLabel = "Stream: ${match.label}"
                    }
                }
            }
        } else {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguage("pt")
        }'''

if OLD_REAPPLY in t:
    t = t.replace(OLD_REAPPLY, NEW_REAPPLY, 1)
    print("reapply OK")
else:
    print("WARN reapply pattern not found")

# 2) search: aceitar file_id OU url
OLD_SEARCH_FILTER = (
    "onlineSubtitleResults = resp.results.filter { it.file_id != null }.take(12)"
)
NEW_SEARCH_FILTER = (
    "onlineSubtitleResults = resp.results.filter { it.file_id != null || !it.url.isNullOrBlank() }.take(12)"
)
if OLD_SEARCH_FILTER in t:
    t = t.replace(OLD_SEARCH_FILTER, NEW_SEARCH_FILTER, 1)
    print("search filter OK")
else:
    print("WARN search filter not found")

# 3) applyOnlineSubtitle completo
OLD_APPLY = '''    suspend fun applyOnlineSubtitle(item: SubtitleSearchItem) {
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
            val file = File(context.cacheDir, "os_${tmdbId}_${currentSeason}_${currentEpisode}.vtt")
            file.writeText(content)
            val pos = exoPlayer.currentPosition
            val wasPlaying = exoPlayer.playWhenReady
            val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file))
                .setMimeType(MimeTypes.TEXT_VTT)
                .setLanguage("pt")
                .setLabel(item.release ?: "Online PT-BR")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            val mediaItem = MediaItem.Builder()
                .setUri(activeUrl)
                .setSubtitleConfigurations(listOf(subConfig))
                .build()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.seekTo(pos)
            exoPlayer.playWhenReady = wasPlaying
            trackSelector.parameters = trackSelector.parameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build()
            val short = (item.release ?: "PT-BR").let { if (it.length > 28) it.take(28) + "…" else it }
            selectedSubtitleLabel = "Online: $short"
            onlineSubtitleApplied = true
            persistSubtitleKey("online")
            settingsPanel = SettingsPanel.MAIN
        } catch (e: Exception) {
            onlineSubtitlesError = e.message ?: "Falha ao baixar legenda"
        } finally {
            onlineSubtitlesLoading = false
        }
    }'''

NEW_APPLY = '''    suspend fun applyOnlineSubtitle(item: SubtitleSearchItem) {
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

if OLD_APPLY in t:
    t = t.replace(OLD_APPLY, NEW_APPLY, 1)
    print("apply OK")
else:
    print("WARN apply pattern not found")
    if "suspend fun applyOnlineSubtitle" in t:
        print("apply function exists but text differs")

p.write_text(t)
print("bytes", len(t))
assert "file_id != null || !it.url.isNullOrBlank()" in t or "WARN search" in open(__file__).read()
print("done")
