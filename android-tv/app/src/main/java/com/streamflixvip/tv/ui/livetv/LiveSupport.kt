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
    val q = option.quality?.takeIf { it.isNotBlank() && !it.equals("Padrao", true) }
    val lab = option.label?.takeIf { it.isNotBlank() }
    if (q != null) append(q)
    if (option.leg == true) append(if (isEmpty()) "Legendado" else " · Legendado")
    if (lab != null) append(if (isEmpty()) lab else " · $lab")
    if (isEmpty()) append("Fonte ${index + 1}")
    append(if (isLiveTs(option.url)) " · TS" else if (isLiveHls(option.url)) " · HLS" else "")
}

internal fun isLiveHls(url: String): Boolean {
    val l = url.lowercase().substringBefore('#')
    val path = l.substringBefore('?')
    return path.endsWith(".m3u8") || path.endsWith(".m3u") || l.contains("output=m3u8") ||
        l.contains("type=m3u8") || l.contains("format=m3u8") || l.contains("/hls/")
}

internal fun isLiveTs(url: String): Boolean {
    val l = url.lowercase()
    return l.substringBefore('?').endsWith(".ts") || l.contains("output=ts") || l.contains("output=mpegts")
}

/**
 * HLS so quando e HLS de verdade. Antes todo link com "/live/" ia como HLS, entao a
 * reserva .ts do Xtream (/live/u/p/123.ts) nunca abria. .ts e link sem extensao vao
 * como fluxo continuo (MPEG-TS detectado pelo extrator).
 */
@OptIn(UnstableApi::class)
internal fun buildLiveMediaSource(url: String): MediaSource {
    val headers = buildMap {
        put("Connection", "keep-alive")
        put("Accept", "*/*")
        if (!isLiveHls(url)) put("Icy-MetaData", "1")
    }
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent(com.streamflixvip.tv.ui.player.PLAYER_USER_AGENT)
        .setAllowCrossProtocolRedirects(true)
        .setKeepPostFor302Redirects(true)
        .setConnectTimeoutMs(10_000)
        .setReadTimeoutMs(20_000)
        .setDefaultRequestProperties(headers)
    val live = MediaItem.LiveConfiguration.Builder()
        // fica ~20s atras do ao vivo: sobra folga para a rede oscilar sem travar
        .setTargetOffsetMs(20_000)
        .setMinOffsetMs(8_000)
        .setMinPlaybackSpeed(0.97f)
        .setMaxPlaybackSpeed(1.03f)
        .build()
    return if (isLiveHls(url)) {
        HlsMediaSource.Factory(http)
            .setAllowChunklessPreparation(true)
            .createMediaSource(
                MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).setLiveConfiguration(live).build(),
            )
    } else {
        ProgressiveMediaSource.Factory(
            http,
            DefaultExtractorsFactory().setTsExtractorFlags(androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES),
        )
            .setContinueLoadingCheckIntervalBytes(256 * 1024)
            .createMediaSource(MediaItem.Builder().setUri(url).setLiveConfiguration(live).build())
    }
}

/** Ao vivo: comeca com 2,5s, volta de travada com 5s e segura ate 50s de folga. */
@OptIn(UnstableApi::class)
internal fun liveLoadControl(): DefaultLoadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(if (com.streamflixvip.tv.util.DeviceProfile.isLowRam) 20_000 else 30_000, 50_000, 2_500, 5_000)
    // teto de memoria do aparelho (Fire TV fraco fecha o app se o buffer crescer demais)
    .setTargetBufferBytes(com.streamflixvip.tv.util.DeviceProfile.playerBufferBytes)
    .setPrioritizeTimeOverSizeThresholds(!com.streamflixvip.tv.util.DeviceProfile.isLowRam)
    .setBackBuffer(0, false)
    .build()
