package com.streamflixvip.tv.ui.player

import android.graphics.Color as AColor
import androidx.annotation.OptIn
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.streamflixvip.tv.data.SubBackground
import com.streamflixvip.tv.data.SubColor
import com.streamflixvip.tv.data.SubSize

// ---------------------------------------------------------------------------
// Reproducao: mesmas regras do app de celular 16.3 (cabecalhos, HLS/DASH/arquivo,
// buffer para IPTV e mensagens de erro em portugues).
// ---------------------------------------------------------------------------

internal fun isLikelyHls(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains(".m3u8") || lower.contains("format=m3u8") || lower.contains("type=m3u8") ||
        lower.contains("/hls/") || lower.contains("mpegurl") || lower.contains("index.m3u") ||
        (lower.contains("playlist") && (lower.contains("m3u") || lower.contains("/hls/")))
}

internal fun isLikelyDash(url: String): Boolean {
    val lower = url.lowercase()
    return lower.substringBefore('?').endsWith(".mpd") || lower.contains("format=mpd")
}

internal fun playbackHeaders(url: String): Map<String, String> {
    val host = try { java.net.URI(url).host.orEmpty() } catch (_: Exception) { "" }
    val origin = when {
        host.contains("pengu.uk", ignoreCase = true) -> "https://pengu.uk/"
        host.contains("hakunaymatata", ignoreCase = true) -> "https://mzfi.me/"
        host.contains("mzfi.me", ignoreCase = true) -> "https://mzfi.me/"
        host.contains("streamflixvip", ignoreCase = true) -> "https://mzfi.me/"
        host.isNotBlank() -> "https://$host/"
        else -> url
    }
    return mapOf("Referer" to origin, "Accept" to "*/*", "Connection" to "keep-alive")
}

/** Mesmo User-Agent em todo lugar (VOD, ao vivo e previa): os paineis Xtream liberam VLC. */
internal const val PLAYER_USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"

@OptIn(UnstableApi::class)
internal fun playbackHttpFactory(url: String): DefaultHttpDataSource.Factory =
    DefaultHttpDataSource.Factory()
        .setUserAgent(PLAYER_USER_AGENT)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(20_000)
        .setDefaultRequestProperties(playbackHeaders(url))

@OptIn(UnstableApi::class)
internal fun buildPlaybackMediaSource(url: String, forceHls: Boolean = false): MediaSource {
    val http = playbackHttpFactory(url)
    return when {
        forceHls || isLikelyHls(url) -> HlsMediaSource.Factory(http).createMediaSource(
            MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).build(),
        )
        isLikelyDash(url) -> DashMediaSource.Factory(http).createMediaSource(
            MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_MPD).build(),
        )
        else -> ProgressiveMediaSource.Factory(
            http,
            DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true),
        ).createMediaSource(MediaItem.fromUri(url))
    }
}

/** Comeca a tocar com 1,5s de buffer, volta de travada com 3s, guarda ate 90s. */
@OptIn(UnstableApi::class)
internal fun streamingLoadControl(): DefaultLoadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(25_000, if (com.streamflixvip.tv.util.DeviceProfile.isLowRam) 50_000 else 90_000, 1_500, 3_000)
    // sem teto o ExoPlayer pode guardar 100+ MB no heap e o Fire TV fecha o app (falta de memoria)
    .setTargetBufferBytes(com.streamflixvip.tv.util.DeviceProfile.playerBufferBytes)
    .setPrioritizeTimeOverSizeThresholds(false)
    .build()

private val NETWORK_ERROR_CODES = setOf(
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
    PlaybackException.ERROR_CODE_TIMEOUT,
)

internal fun isNetworkError(code: Int) = code in NETWORK_ERROR_CODES ||
    code == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW

internal fun isContainerError(code: Int): Boolean =
    code == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
        code == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
        code == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED

internal fun friendlyPlaybackError(error: PlaybackException): String {
    val code = error.errorCode
    val name = error.errorCodeName
    return when {
        name.contains("DECOD", ignoreCase = true) ->
            "Esta TV não consegue decodificar este vídeo. Tente outro servidor."
        code == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
            val http = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
            "O servidor recusou o vídeo" + (if (http != null) " (HTTP $http)" else "") + "."
        }
        code == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "Vídeo não encontrado neste servidor."
        code in NETWORK_ERROR_CODES -> "Sem resposta do servidor do vídeo. Verifique sua internet."
        isContainerError(code) || name.contains("PARSING", ignoreCase = true) -> "Formato de vídeo não suportado neste servidor."
        else -> "Não foi possível reproduzir ($name)."
    }
}

internal fun formatTime(ms: Long): String {
    if (ms <= 0L || ms == C.TIME_UNSET) return "0:00"
    val totalSec = (ms / 1000L).toInt()
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

internal fun humanTrackLabel(label: String?, language: String?, index: Int): String {
    val names = mapOf(
        "pt" to "Português", "por" to "Português", "pt-br" to "Português (BR)", "pt-pt" to "Português (PT)",
        "en" to "Inglês", "eng" to "Inglês", "es" to "Espanhol", "spa" to "Espanhol", "fr" to "Francês",
        "fre" to "Francês", "de" to "Alemão", "ger" to "Alemão", "it" to "Italiano", "ita" to "Italiano",
        "ja" to "Japonês", "jpn" to "Japonês", "ko" to "Coreano", "kor" to "Coreano", "zh" to "Chinês", "ru" to "Russo",
    )
    val lang = language?.lowercase()?.trim().orEmpty()
    val langName = names[lang] ?: names[lang.split("-", "_").firstOrNull().orEmpty()]
    val raw = label?.trim().orEmpty()
    return when {
        raw.isNotBlank() && raw.lowercase() !in setOf("und", "null", "unknown") && langName != null && !raw.contains(langName, true) -> "$langName · $raw"
        raw.isNotBlank() && raw.lowercase() !in setOf("und", "null", "unknown") -> names[raw.lowercase()] ?: raw
        langName != null -> langName
        else -> "Faixa ${index + 1}"
    }
}

internal fun isPortuguese(language: String?, label: String?): Boolean {
    val l = (language ?: "").lowercase()
    val n = (label ?: "").lowercase()
    return l.startsWith("pt") || l == "por" || n.contains("portug") || n.contains("pt-br") || n.contains("brazil")
}

// ---------------------------------------------------------------------------
// Legenda online (SRT / VTT) desenhada por cima do video.
// ---------------------------------------------------------------------------

data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String, val top: Boolean = false)

private val CUE_TIME = Regex(
    """((?:\d{1,2}:)?\d{1,2}:\d{2}[,.]\d{1,3})\s*-->\s*((?:\d{1,2}:)?\d{1,2}:\d{2}[,.]\d{1,3})(.*)""",
)
private val ASS_TOP = Regex("""\{[^}]*\\an[789][^}]*\}""")
private val BRACE_TAG = Regex("""\{[^}]*\}""")
private val HTML_TAG = Regex("""<(/?)([a-zA-Z]+)[^>]*>""")
private val VTT_TOP_LINE = Regex("""line:\s*(?:0|1|2|-?[0-9]%|1[0-9]%)(?:[,\s]|$)""")

private fun parseCueTimestamp(ts: String): Long {
    val clean = ts.trim().replace(',', '.')
    val main = clean.substringBefore('.')
    val frac = clean.substringAfter('.', "0").padEnd(3, '0').take(3).toLongOrNull() ?: 0L
    val parts = main.split(':').map { it.toLongOrNull() ?: 0L }
    val (h, m, s) = when (parts.size) {
        3 -> Triple(parts[0], parts[1], parts[2])
        2 -> Triple(0L, parts[0], parts[1])
        else -> return 0L
    }
    return (h * 3_600_000L + m * 60_000L + s * 1_000L + frac).coerceAtLeast(0L)
}

private fun cleanCueText(raw: String): String {
    var t = raw.replace(BRACE_TAG, "")
    t = HTML_TAG.replace(t) { m ->
        val name = m.groupValues[2].lowercase()
        if (name == "i" || name == "b") "<${m.groupValues[1]}$name>" else ""
    }
    t = t.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&nbsp;", " ").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("\\N", "\n")
    return t.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
}

internal fun parseSubtitleText(content: String): List<SubtitleCue> {
    val lines = content.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val cues = mutableListOf<SubtitleCue>()
    var i = 0
    while (i < lines.size) {
        val m = CUE_TIME.find(lines[i])
        if (m == null) { i++; continue }
        val start = parseCueTimestamp(m.groupValues[1])
        val end = parseCueTimestamp(m.groupValues[2])
        var top = VTT_TOP_LINE.containsMatchIn(m.groupValues[3])
        val buf = mutableListOf<String>()
        i++
        while (i < lines.size && lines[i].isNotBlank() && CUE_TIME.find(lines[i]) == null) {
            val l = lines[i]
            val nextIsTime = i + 1 < lines.size && CUE_TIME.find(lines[i + 1]) != null
            if (!(nextIsTime && l.trim().all { it.isDigit() })) buf.add(l)
            i++
        }
        val raw = buf.joinToString("\n")
        if (ASS_TOP.containsMatchIn(raw)) top = true
        val text = cleanCueText(raw)
        if (text.isNotEmpty() && end > start) cues.add(SubtitleCue(start, end, text, top))
    }
    return cues.sortedBy { it.startMs }
}

/** Busca binaria da fala ativa em [pos]. */
internal fun cueAt(cues: List<SubtitleCue>, pos: Long): SubtitleCue? {
    var lo = 0
    var hi = cues.size - 1
    var found = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (cues[mid].startMs <= pos) { found = mid; lo = mid + 1 } else hi = mid - 1
    }
    if (found < 0) return null
    // pode haver sobreposicao: olha alguns anteriores
    for (k in found downTo maxOf(0, found - 3)) {
        val c = cues[k]
        if (pos in c.startMs until c.endMs) return c
    }
    return null
}

internal fun cueToAnnotated(text: String): AnnotatedString = buildAnnotatedString {
    var italic = 0
    var bold = 0
    var idx = 0
    val tag = Regex("""</?[ib]>""")
    fun seg(s: String) {
        pushStyle(
            SpanStyle(
                fontStyle = if (italic > 0) FontStyle.Italic else FontStyle.Normal,
                fontWeight = if (bold > 0) FontWeight.Bold else null,
            ),
        )
        append(s)
        pop()
    }
    for (m in tag.findAll(text)) {
        if (m.range.first > idx) seg(text.substring(idx, m.range.first))
        when (m.value) {
            "<i>" -> italic++
            "</i>" -> italic = (italic - 1).coerceAtLeast(0)
            "<b>" -> bold++
            "</b>" -> bold = (bold - 1).coerceAtLeast(0)
        }
        idx = m.range.last + 1
    }
    if (idx < text.length) seg(text.substring(idx))
}

/** Aplica o estilo escolhido nas legendas do proprio video. */
@OptIn(UnstableApi::class)
internal fun applySubtitleStyle(view: SubtitleView, size: SubSize, color: SubColor, background: SubBackground) {
    view.setApplyEmbeddedStyles(false)
    view.setApplyEmbeddedFontSizes(false)
    val (bg, edgeType) = when (background) {
        SubBackground.OUTLINE -> AColor.TRANSPARENT to CaptionStyleCompat.EDGE_TYPE_OUTLINE
        SubBackground.SHADOW -> AColor.TRANSPARENT to CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
        SubBackground.BOX -> 0xB3000000.toInt() to CaptionStyleCompat.EDGE_TYPE_NONE
        SubBackground.NONE -> AColor.TRANSPARENT to CaptionStyleCompat.EDGE_TYPE_NONE
    }
    view.setStyle(CaptionStyleCompat(color.argb, bg, AColor.TRANSPARENT, edgeType, AColor.BLACK, null))
    view.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, size.sp)
}
