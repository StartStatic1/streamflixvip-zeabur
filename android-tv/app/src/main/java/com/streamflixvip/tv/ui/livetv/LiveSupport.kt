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

/** Melhor qualidade primeiro, depois prioridade da API. */
internal fun orderStreams(streams: List<LiveStreamOption>): List<LiveStreamOption> =
    streams.sortedWith(
        compareBy<LiveStreamOption> { QUALITY_RANK[(it.quality ?: "").uppercase().replace(" ", "")] ?: 5 }
            .thenBy { it.priority ?: 100 },
    )

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
