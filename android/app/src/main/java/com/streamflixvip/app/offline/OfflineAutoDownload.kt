package com.streamflixvip.app.offline

import android.content.Context
import com.streamflixvip.app.BuildConfig
import com.streamflixvip.app.network.NetworkModule
import com.streamflixvip.app.network.StreamUrlResolver
import com.streamflixvip.app.network.VipSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Botão "Baixar" fora do player (tela do filme / card do episódio): escolhe
 * sozinho o primeiro servidor que dá para baixar (arquivo direto ou HLS),
 * dublado primeiro, e começa o download.
 */
object OfflineAutoDownload {
    data class Result(val ok: Boolean, val message: String)

    /** 0 = dublado, 1 = sem marca, 2 = legendado. */
    fun audioRank(label: String?): Int {
        val l = label.orEmpty().lowercase()
        return when {
            l.contains("[d]") || l.contains("dublad") || l.contains("dual") -> 0
            l.contains("[l]") || l.contains("legendad") -> 2
            else -> 1
        }
    }

    private fun looksDownloadable(s: VipSource): Boolean {
        val lower = s.source_url.lowercase()
        if (s.source_url.isBlank() || lower.startsWith("rtmp") || lower.contains("/live/")) return false
        return s.isDirectPlayable
    }

    /** Confere que o link entrega vídeo (não uma página) antes de baixar arquivo direto. */
    private suspend fun probeIsMedia(url: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 8_000
            c.readTimeout = 8_000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "VLC/3.0.20 LibVLC/3.0.20")
            c.setRequestProperty("Range", "bytes=0-1")
            OfflineDownloads.headersFor(url).forEach { (k, v) -> c.setRequestProperty(k, v) }
            try {
                val code = c.responseCode
                val type = c.contentType.orEmpty().lowercase()
                code in 200..399 && !type.startsWith("text/html") && !type.contains("json")
            } finally { c.disconnect() }
        }.getOrDefault(false)
    }

    suspend fun startBest(context: Context, sources: List<VipSource>, meta: OfflineDownloads.Meta): Result {
        val id = OfflineDownloads.idFor(meta.tmdbId, meta.mediaType, meta.season, meta.episode)
        OfflineDownloads.find(id)?.let {
            if (it.isDone) return Result(true, "Já está nos seus downloads (Perfil → Downloads)")
            if (it.state != androidx.media3.exoplayer.offline.Download.STATE_FAILED) return Result(true, "Já está baixando")
        }
        val ordered = sources.filter(::looksDownloadable)
            .withIndex()
            .sortedWith(compareBy({ if (it.value.needsVpn) 1 else 0 }, { audioRank(it.value.source_label) }, { it.index }))
            .map { it.value }
            .distinctBy { it.source_url }
        if (ordered.isEmpty()) {
            return Result(false, "Nenhum servidor deste título dá para baixar (são players de site). Dá para assistir online normalmente.")
        }
        var lastError: String? = null
        for (s in ordered.take(5)) {
            val url = runCatching {
                StreamUrlResolver.resolveFastest(s.candidatePlaybackUrls(BuildConfig.API_BASE_URL, NetworkModule.ZEABUR_BASE_URL))
            }.getOrNull().orEmpty().ifBlank { s.source_url }
            if (!OfflineDownloads.isHls(url) && !probeIsMedia(url)) continue
            val err = OfflineDownloads.start(context, url, meta.copy(sourceLabel = s.source_label))
            if (err == null) {
                val which = when (audioRank(s.source_label)) { 0 -> " (dublado)"; 2 -> " (legendado)"; else -> "" }
                return Result(true, "Download começou$which. Acompanhe em Perfil → Downloads")
            }
            if (err.startsWith("Já está")) return Result(true, err)
            lastError = err
        }
        return Result(false, lastError ?: "Nenhum servidor deste título respondeu para baixar agora. Tente mais tarde.")
    }
}
