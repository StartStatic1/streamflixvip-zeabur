#!/usr/bin/env python3
"""Aplica preferencias de LEG/audio/fonte entre episodios no PlayerScreen.kt."""
from pathlib import Path
import re
import subprocess

OUT = Path("android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt")

# Restaura ultima versao completa conhecida se estiver PLACEHOLDER/TEMP
text = OUT.read_text() if OUT.exists() else ""
if "fun NativePlayer" not in text or "PLACEHOLDER" in text or "TEMP - will replace" in text:
    # tenta varios commits bons
    for sha in [
        "056cd13ff277829fa10b0b3af49ab50390da9f47",
        "8d981bf2875d7efa7ee9e04830da58c36fcda442",
        "0e78c849",
    ]:
        try:
            data = subprocess.check_output(
                ["git", "show", f"{sha}:android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt"],
                stderr=subprocess.DEVNULL,
            )
            if b"fun NativePlayer" in data and len(data) > 5000:
                OUT.write_bytes(data)
                text = data.decode()
                print("restored from", sha, "bytes", len(data))
                break
        except Exception as e:
            print("skip", sha, e)
    else:
        raise SystemExit("Nao foi possivel restaurar PlayerScreen completo do git")

if "streamflix_series_prefs" in text and "sourceModeScore" in text and "reapplyTrackPreferences" in text:
    print("ja tem preferencias de serie")
else:
    # Injeta helpers apos isLikelyHls
    helpers = '''
private const val SERIES_PREFS = "streamflix_series_prefs"

private fun detectSourceMode(label: String?): String {
    val n = (label ?: "").lowercase()
    val leg = listOf("leg", "legend", "legendado", "legendada", "sub ", "subs", "subtitle").any { n.contains(it) }
    val dub = listOf("dub", "dublado", "dublada", "dual").any { n.contains(it) }
    return when {
        leg && !dub -> "leg"
        dub && !leg -> "dub"
        else -> "any"
    }
}

private fun sourceModeScore(label: String?, preferred: String): Int {
    if (preferred == "any") return 0
    val m = detectSourceMode(label)
    return when {
        m == preferred -> 100
        m == "any" -> 40
        else -> 0
    }
}

private fun normKey(s: String?): String =
    (s ?: "").lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

private fun trackMatchesPref(label: String, pref: String): Boolean {
    if (pref.isBlank() || pref == "off") return false
    val a = normKey(label)
    val b = normKey(pref)
    if (a == b || a.contains(b) || b.contains(a)) return true
    val ptHints = listOf("pt", "por", "portugues", "portuguese", "brazil", "br")
    val enHints = listOf("en", "eng", "english", "ingles")
    if (ptHints.any { b.contains(it) } && ptHints.any { a.contains(it) }) return true
    if (enHints.any { b.contains(it) } && enHints.any { a.contains(it) }) return true
    return false
}

private fun seriesPrefs(context: android.content.Context) =
    context.getSharedPreferences(SERIES_PREFS, android.content.Context.MODE_PRIVATE)

private fun loadSeriesPref(context: android.content.Context, tmdbId: Int, key: String, default: String = ""): String =
    seriesPrefs(context).getString("${tmdbId}_$key", default) ?: default

private fun saveSeriesPref(context: android.content.Context, tmdbId: Int, key: String, value: String) {
    seriesPrefs(context).edit().putString("${tmdbId}_$key", value).apply()
}

'''
    if "SERIES_PREFS" not in text:
        anchor = "private data class TrackOption"
        if anchor not in text:
            raise SystemExit("TrackOption nao encontrado")
        text = text.replace(anchor, helpers + anchor, 1)
        print("helpers ok")

    # Estado de preferencias no NativePlayer
    if "preferredSourceMode" not in text:
        marker = "    var showNextPrompt by remember { mutableStateOf(false) }"
        inject = marker + '''

    var preferredSourceMode by remember {
        mutableStateOf(loadSeriesPref(context, tmdbId, "source_mode", "any"))
    }
    var preferredAudioKey by remember {
        mutableStateOf(loadSeriesPref(context, tmdbId, "audio", ""))
    }
    var preferredSubtitleKey by remember {
        mutableStateOf(loadSeriesPref(context, tmdbId, "subtitle", ""))
    }
    var pendingReapplyTracks by remember { mutableStateOf(false) }

    fun persistSourceMode(mode: String) {
        preferredSourceMode = mode
        saveSeriesPref(context, tmdbId, "source_mode", mode)
    }
    fun persistAudioKey(key: String) {
        preferredAudioKey = key
        saveSeriesPref(context, tmdbId, "audio", key)
    }
    fun persistSubtitleKey(key: String) {
        preferredSubtitleKey = key
        saveSeriesPref(context, tmdbId, "subtitle", key)
    }

    fun reapplyTrackPreferences(
        subs: List<TrackOption> = subtitleOptions,
        audios: List<TrackOption> = audioOptions,
    ) {
        val builder = trackSelector.parameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        val subPref = preferredSubtitleKey
        if (subPref.isBlank() || subPref == "off") {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            selectedSubtitleLabel = "Desligada"
        } else {
            val match = subs.firstOrNull { trackMatchesPref(it.label, subPref) }
            if (match != null) {
                builder.setOverrideForType(TrackSelectionOverride(match.group, match.trackIndex))
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                selectedSubtitleLabel = match.label
            }
        }
        val audioPref = preferredAudioKey
        if (audioPref.isNotBlank() && audioPref != "default") {
            val match = audios.firstOrNull { trackMatchesPref(it.label, audioPref) }
            if (match != null) {
                builder.setOverrideForType(TrackSelectionOverride(match.group, match.trackIndex))
                selectedAudioLabel = match.label
            }
        }
        trackSelector.parameters = builder.build()
    }
'''
        if marker not in text:
            raise SystemExit("showNextPrompt marker missing")
        text = text.replace(marker, inject, 1)
        print("state+reapply ok")

    # onTracksChanged: marca reapply
    if "pendingReapplyTracks = true" not in text:
        old = "qualityOptions = qualities.distinctBy { it.label }.sortedByDescending { it.label.removeSuffix(\"p\").toIntOrNull() ?: 0 }"
        if old in text:
            text = text.replace(
                old,
                old + "\n                    pendingReapplyTracks = true",
                1,
            )
            print("onTracksChanged hook ok")

    if "LaunchedEffect(pendingReapplyTracks" not in text:
        # apos val exoPlayer = remember { ... }
        # inserir apos o bloco remember do exoPlayer - apos "    }\n\n    fun reloadWithUrl"
        marker = "    fun reloadWithUrl(streamUrl: String"
        if marker not in text:
            raise SystemExit("reloadWithUrl missing")
        block = '''    LaunchedEffect(pendingReapplyTracks, subtitleOptions, audioOptions) {
        if (!pendingReapplyTracks) return@LaunchedEffect
        kotlinx.coroutines.delay(250)
        reapplyTrackPreferences(subtitleOptions, audioOptions)
        pendingReapplyTracks = false
    }

    '''
        text = text.replace(marker, block + marker, 1)
        print("LaunchedEffect reapply ok")

    # reloadWithUrl limpa overrides antigos
    if "clearOverridesOfType(C.TRACK_TYPE_AUDIO)" not in text.split("fun reloadWithUrl")[1][:800]:
        old = "            exoPlayer.setMediaSource(mediaSource)"
        # so a primeira ocorrencia dentro de reloadWithUrl
        idx = text.find("fun reloadWithUrl")
        if idx < 0:
            raise SystemExit("reloadWithUrl not found")
        sub = text[idx:]
        pos = sub.find(old)
        if pos < 0:
            raise SystemExit("setMediaSource in reload not found")
        insert = '''            trackSelector.parameters = trackSelector.parameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .build()
            pendingReapplyTracks = true
'''
        text = text[:idx] + sub[:pos] + insert + sub[pos:]
        print("reload clear overrides ok")

    # playNextEpisode: ranking por LEG/DUB
    old_first = "val src = direct.first()"
    if old_first in text:
        text = text.replace(
            old_first,
            '''val ranked = direct.sortedWith(
                    compareByDescending<VipSource> { sourceModeScore(it.source_label, preferredSourceMode) }
                        .thenByDescending { it.priority ?: 0 },
                )
                val src = ranked.first()
                val mode = detectSourceMode(src.source_label)
                if (mode != "any") persistSourceMode(mode)''',
            1,
        )
        print("playNext ranking ok")

    # selectSubtitle / selectAudio persistem
    if "persistSubtitleKey" not in text.split("fun selectSubtitle")[1][:400]:
        text = text.replace(
            '''    fun selectSubtitle(option: TrackOption?) {
        trackSelector.parameters = if (option == null) {
            selectedSubtitleLabel = "Desligada"
            trackSelector.parameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        } else {
            selectedSubtitleLabel = option.label
            trackSelector.parameters.buildUpon().setOverrideForType(TrackSelectionOverride(option.group, option.trackIndex)).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
        }
    }''',
            '''    fun selectSubtitle(option: TrackOption?) {
        trackSelector.parameters = if (option == null) {
            selectedSubtitleLabel = "Desligada"
            persistSubtitleKey("off")
            trackSelector.parameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        } else {
            selectedSubtitleLabel = option.label
            persistSubtitleKey(option.label)
            trackSelector.parameters.buildUpon().setOverrideForType(TrackSelectionOverride(option.group, option.trackIndex)).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
        }
    }''',
            1,
        )
        print("selectSubtitle persist ok")

    if "persistAudioKey" not in text.split("fun selectAudio")[1][:400]:
        text = text.replace(
            '''    fun selectAudio(option: TrackOption?) {
        trackSelector.parameters = if (option == null) {
            selectedAudioLabel = "Padrao"
            trackSelector.parameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_AUDIO).build()
        } else {
            selectedAudioLabel = option.label
            trackSelector.parameters.buildUpon().setOverrideForType(TrackSelectionOverride(option.group, option.trackIndex)).build()
        }
    }''',
            '''    fun selectAudio(option: TrackOption?) {
        trackSelector.parameters = if (option == null) {
            selectedAudioLabel = "Padrao"
            persistAudioKey("default")
            trackSelector.parameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_AUDIO).build()
        } else {
            selectedAudioLabel = option.label
            persistAudioKey(option.label)
            trackSelector.parameters.buildUpon().setOverrideForType(TrackSelectionOverride(option.group, option.trackIndex)).build()
        }
    }''',
            1,
        )
        print("selectAudio persist ok")

OUT.write_text(text)
print("DONE bytes", OUT.stat().st_size)
assert "sourceModeScore" in text or "streamflix_series_prefs" in text
