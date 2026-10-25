package com.streamflixvip.tv.ui.livetv

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import com.streamflixvip.tv.network.LiveStreamOption

private val QUALITY_RANK = mapOf("4K" to 0, "UHD" to 0, "FHD" to 1, "FULLHD" to 1, "HD" to 2, "SD" to 3, "STD" to 3)

/**
 * Ordem dos links de um canal:
 *  1) servidor que falhou neste aparelho nas ultimas horas vai para o fim;
 *  2) prioridade da API (ja junta prioridade da fonte + qualidade + teste de reproducao);
 *  3) qualidade como desempate.
 * Antes a qualidade vinha primeiro e um FHD de servidor morto (ex. lives01.top)
 * sempre ganhava de um HD que funciona.
 */
internal fun orderStreams(streams: List<LiveStreamOption>): List<LiveStreamOption> =
    streams.sortedWith(
        compareBy<LiveStreamOption> { LiveHostHealth.penalty(it.url) }
            .thenBy { it.priority ?: 100 }
            .thenBy { QUALITY_RANK[(it.quality ?: "").uppercase().replace(" ", "")] ?: 5 },
    )

/** Lembra (no aparelho) quais servidores de TV ao vivo falharam recentemente. */
internal object LiveHostHealth {
    private const val PREFS = "sfv_live_hosts"
    private const val WINDOW_MS = 6 * 60 * 60 * 1000L
    private var prefs: android.content.SharedPreferences? = null
    private val mem = java.util.concurrent.ConcurrentHashMap<String, Pair<Int, Long>>()

    fun init(context: android.content.Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        prefs = p
        val now = System.currentTimeMillis()
        p.all.forEach { (host, v) ->
            val parts = (v as? String)?.split("|") ?: return@forEach
            val fails = parts.getOrNull(0)?.toIntOrNull() ?: return@forEach
            val at = parts.getOrNull(1)?.toLongOrNull() ?: return@forEach
            if (now - at < WINDOW_MS) mem[host] = fails to at
        }
    }

    fun hostOf(url: String): String = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull() ?: url.take(40)

    /** 0 = ok; 1 = falhou 1x; 2 = falhou 2x ou mais (vai para o fim). */
    fun penalty(url: String): Int {
        val (fails, at) = mem[hostOf(url)] ?: return 0
        if (System.currentTimeMillis() - at > WINDOW_MS) return 0
        return fails.coerceAtMost(2)
    }

    fun markFail(url: String) {
        val h = hostOf(url)
        val fails = (mem[h]?.first ?: 0) + 1
        val now = System.currentTimeMillis()
        mem[h] = fails to now
        prefs?.edit()?.putString(h, "$fails|$now")?.apply()
    }

    fun markOk(url: String) {
        val h = hostOf(url)
        if (mem.remove(h) != null) prefs?.edit()?.remove(h)?.apply()
    }
}

internal fun streamLabel(option: LiveStreamOption, index: Int): String = buildString {
    val q = option.quality?.takeIf { it.isNotBlank() }
    val lab = option.label?.takeIf { it.isNotBlank() }
    if (q != null) append(q)
    if (option.leg == true) append(if (isEmpty()) "Legendado" else " · Legendado")
    if (lab != null) append(if (isEmpty()) lab else " · $lab")
    if (isEmpty()) append("Fonte ${index + 1}")
}

@OptIn(UnstableApi::class)
internal fun buildLiveMediaSource(url: String): MediaSource {
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent("VLC/3.0.20 LibVLC/3.0.20")
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(10_000)
        .setReadTimeoutMs(15_000)
        .setDefaultRequestProperties(mapOf("Referer" to url, "Connection" to "keep-alive", "Icy-MetaData" to "1"))
    val lower = url.lowercase()
    return if (lower.contains(".m3u8") || lower.contains("/live/") || lower.contains("m3u")) {
        HlsMediaSource.Factory(http).createMediaSource(
            MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).build(),
        )
    } else {
        ProgressiveMediaSource.Factory(http, DefaultExtractorsFactory()).createMediaSource(MediaItem.fromUri(url))
    }
}

/** Ao vivo: comeca rapido (1s) e segura 30s de folga. */
@OptIn(UnstableApi::class)
internal fun liveLoadControl(): DefaultLoadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(15_000, 30_000, 1_000, 2_500)
    .setTargetBufferBytes(com.streamflixvip.tv.util.DeviceProfile.playerBufferBytes / 2)
    .setPrioritizeTimeOverSizeThresholds(false)
    .build()
