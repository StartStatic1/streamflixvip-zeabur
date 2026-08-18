#!/usr/bin/env python3
from pathlib import Path

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt")
t = p.read_text()

# Helper functions to inject after selectSubtitle function or before searchOnlineSubtitles
if "fun subtitleCacheFile" not in t:
    helper = '''
    fun subtitleCacheFile(): File {
        val dir = File(context.filesDir, "subtitles")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "os_${tmdbId}_${currentSeason}_${currentEpisode}.vtt")
    }

    fun subtitlePrefsKey(): String = "sub_label_${tmdbId}_${currentSeason}_${currentEpisode}"

    fun loadSavedSubtitleLabel(): String? {
        val prefs = context.getSharedPreferences("streamflix_subs", android.content.Context.MODE_PRIVATE)
        return prefs.getString(subtitlePrefsKey(), null)
    }

    fun saveSubtitleLabel(label: String?) {
        val prefs = context.getSharedPreferences("streamflix_subs", android.content.Context.MODE_PRIVATE)
        if (label.isNullOrBlank()) {
            prefs.edit().remove(subtitlePrefsKey()).apply()
        } else {
            prefs.edit().putString(subtitlePrefsKey(), label).apply()
        }
    }

    suspend fun applySubtitleFromFile(file: File, label: String) {
        if (!file.exists() || file.length() < 10L) return
        val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
        val wasPlaying = exoPlayer.playWhenReady
        val durationUs = exoPlayer.duration.takeIf { it > 0L } ?: (5L * 60 * 60 * 1_000_000L)

        val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file))
            .setMimeType(MimeTypes.TEXT_VTT)
            .setLanguage("pt")
            .setLabel(label)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

        val dsFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("VLC/3.0.4 LibVLC/3.0.4")
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to activeUrl,
                    "Connection" to "keep-alive",
                    "Icy-MetaData" to "1",
                ),
            )
        val localFactory = DefaultDataSource.Factory(context, dsFactory)
        val extFactory = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
        val videoItem = MediaItem.fromUri(activeUrl)
        val videoSource = if (isLikelyHls(activeUrl)) {
            HlsMediaSource.Factory(dsFactory).createMediaSource(videoItem)
        } else {
            ProgressiveMediaSource.Factory(dsFactory, extFactory).createMediaSource(videoItem)
        }
        val subtitleSource = SingleSampleMediaSource.Factory(localFactory)
            .createMediaSource(subConfig, durationUs)
        val merged = MergingMediaSource(videoSource, subtitleSource)

        trackSelector.parameters = trackSelector.parameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setPreferredTextLanguage("pt")
            .setSelectUndeterminedTextLanguage(true)
            .build()

        exoPlayer.setMediaSource(merged)
        exoPlayer.prepare()
        exoPlayer.seekTo(pos)
        exoPlayer.playWhenReady = wasPlaying

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

        selectedSubtitleLabel = label
        onlineSubtitleApplied = true
        saveSubtitleLabel(label)
    }

'''
    anchor = "    suspend fun searchOnlineSubtitles()"
    if anchor not in t:
        raise SystemExit("searchOnlineSubtitles not found")
    t = t.replace(anchor, helper + anchor, 1)
    print("helpers injected")

# Change cacheDir to filesDir via helper
t = t.replace(
    'val file = File(context.cacheDir, "os_${tmdbId}_${currentSeason}_${currentEpisode}.vtt")',
    "val file = subtitleCacheFile()",
)

# After writing file and before building subConfig, we can simplify applyOnlineSubtitle
# to call applySubtitleFromFile - replace the big block after writeText

old_mid = '''            val file = subtitleCacheFile()
            file.writeText(vtt, Charsets.UTF_8)

            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val wasPlaying = exoPlayer.playWhenReady
            val durationUs = exoPlayer.duration.takeIf { it > 0L } ?: (5L * 60 * 60 * 1_000_000L)

            val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file))
                .setMimeType(MimeTypes.TEXT_VTT)
                .setLanguage("pt")
                .setLabel(item.release ?: "Online PT-BR")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()

            val dsFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("VLC/3.0.4 LibVLC/3.0.4")
                .setDefaultRequestProperties(
                    mapOf(
                        "Referer" to activeUrl,
                        "Connection" to "keep-alive",
                        "Icy-MetaData" to "1",
                    ),
                )
            val localFactory = DefaultDataSource.Factory(context, dsFactory)
            val extFactory = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
            val videoItem = MediaItem.fromUri(activeUrl)
            val videoSource = if (isLikelyHls(activeUrl)) {
                HlsMediaSource.Factory(dsFactory).createMediaSource(videoItem)
            } else {
                ProgressiveMediaSource.Factory(dsFactory, extFactory).createMediaSource(videoItem)
            }
            val subtitleSource = SingleSampleMediaSource.Factory(localFactory)
                .createMediaSource(subConfig, durationUs)
            val merged = MergingMediaSource(videoSource, subtitleSource)

            trackSelector.parameters = trackSelector.parameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguage("pt")
                .setSelectUndeterminedTextLanguage(true)
                .build()

            exoPlayer.setMediaSource(merged)
            exoPlayer.prepare()
            exoPlayer.seekTo(pos)
            exoPlayer.playWhenReady = wasPlaying

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

            val short = (item.release ?: "PT-BR").let { if (it.length > 28) it.take(28) + "..." else it }
            selectedSubtitleLabel = "Online: $short"
            onlineSubtitleApplied = true
            settingsPanel = SettingsPanel.MAIN'''

new_mid = '''            val file = subtitleCacheFile()
            file.writeText(vtt, Charsets.UTF_8)

            val short = (item.release ?: "PT-BR").let { if (it.length > 28) it.take(28) + "..." else it }
            val label = "Online: $short"
            applySubtitleFromFile(file, label)
            settingsPanel = SettingsPanel.MAIN'''

if old_mid in t:
    t = t.replace(old_mid, new_mid, 1)
    print("applyOnlineSubtitle simplified")
else:
    # maybe cacheDir still there
    if "context.cacheDir" in t and "os_${tmdbId}" in t:
        print("WARN still cacheDir")
    print("WARN mid block not exact - trying partial")
    if "file.writeText(vtt, Charsets.UTF_8)" in t and "applySubtitleFromFile" not in t:
        # insert after writeText a call and leave old code - messy
        # force replace writeText section start
        pass

# Clear saved label when desligar
if "saveSubtitleLabel(null)" not in t:
    t = t.replace(
        '''    fun selectSubtitle(option: TrackOption?) {
        onlineSubtitleApplied = false
        if (option == null) {
            selectedSubtitleLabel = "Desligada"''',
        '''    fun selectSubtitle(option: TrackOption?) {
        onlineSubtitleApplied = false
        if (option == null) {
            selectedSubtitleLabel = "Desligada"
            saveSubtitleLabel(null)''',
        1,
    )
    print("clear on desligada")

# Auto-load saved subtitle when player is ready
if "loadSavedSubtitleLabel" in t and "LaunchedEffect(activeUrl, tmdbId)" not in t:
    # Find a good place - after exoPlayer is created and media prepared
    # Look for first prepare or DisposableEffect of player
    marker = "    var activeUrl by remember { mutableStateOf(url) }"
    if marker in t:
        inject = marker + '''

    // Reaplica legenda online salva (filesDir) sem baixar de novo
    LaunchedEffect(activeUrl, tmdbId, currentSeason, currentEpisode) {
        val file = subtitleCacheFile()
        val label = loadSavedSubtitleLabel()
        if (file.exists() && file.length() > 10L && !label.isNullOrBlank()) {
            // espera player ter fonte
            kotlinx.coroutines.delay(600)
            try {
                applySubtitleFromFile(file, label)
            } catch (_: Exception) {
            }
        }
    }'''
        t = t.replace(marker, inject, 1)
        print("autoload ok")
    else:
        print("WARN no activeUrl marker")

p.write_text(t)
print("DONE")
