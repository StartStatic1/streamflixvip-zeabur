package com.streamflixvip.app.offline

import android.content.Context
import android.net.Uri
import android.os.StatFs
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.source.MediaSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * Downloads para ver sem internet (VIP, só no celular), com o DownloadManager do Media3.
 * Funciona com arquivo direto (MP4/MKV) e HLS (.m3u8, baixa uma qualidade até 720p).
 * O vídeo vem direto da fonte (ou do nosso servidor, como no play normal) — nunca pelo proxy residencial.
 */
@OptIn(UnstableApi::class)
object OfflineDownloads {
    data class Meta(
        val tmdbId: Int,
        val mediaType: String,
        val season: Int,
        val episode: Int,
        val title: String,
        val posterPath: String?,
        val sourceLabel: String?,
    ) {
        fun toBytes(): ByteArray = JSONObject()
            .put("tmdbId", tmdbId).put("mediaType", mediaType).put("season", season).put("episode", episode)
            .put("title", title).put("poster", posterPath ?: "").put("label", sourceLabel ?: "")
            .toString().toByteArray()

        companion object {
            fun from(bytes: ByteArray?): Meta? = runCatching {
                val o = JSONObject(String(bytes ?: return null))
                Meta(
                    o.optInt("tmdbId"), o.optString("mediaType", "movie"), o.optInt("season"), o.optInt("episode"),
                    o.optString("title"), o.optString("poster").ifBlank { null }, o.optString("label").ifBlank { null },
                )
            }.getOrNull()
        }
    }

    data class Item(val id: String, val meta: Meta, val download: Download) {
        val state: Int get() = download.state
        val percent: Float get() = download.percentDownloaded.let { if (it.isNaN() || it < 0) 0f else it }
        val bytes: Long get() = download.bytesDownloaded
        val isDone: Boolean get() = download.state == Download.STATE_COMPLETED
    }

    private var appContext: Context? = null
    private var db: StandaloneDatabaseProvider? = null
    private var cache: SimpleCache? = null
    private var manager: DownloadManager? = null
    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items: StateFlow<List<Item>> = _items

    fun idFor(tmdbId: Int, mediaType: String, season: Int, episode: Int): String =
        if (mediaType == "tv") "tv:$tmdbId:$season:$episode" else "movie:$tmdbId"

    fun dir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "offline").apply { mkdirs() }

    @Synchronized
    private fun database(context: Context): StandaloneDatabaseProvider =
        db ?: StandaloneDatabaseProvider(context.applicationContext).also { db = it }

    @Synchronized
    fun cache(context: Context): SimpleCache {
        cache?.let { return it }
        val c = SimpleCache(dir(context), NoOpCacheEvictor(), database(context))
        cache = c
        return c
    }

    /** Mesmos cabeçalhos que o player usa (Referer do próprio host etc.). */
    internal fun headersFor(url: String): Map<String, String> {
        val host = runCatching { java.net.URI(url).host.orEmpty() }.getOrDefault("")
        val origin = when {
            host.contains("pengu.uk", true) -> "https://pengu.uk/"
            host.contains("hakunaymatata", true) || host.contains("mzfi.me", true) || host.contains("streamflixvip", true) -> "https://mzfi.me/"
            host.isNotBlank() -> "https://$host/"
            else -> url
        }
        return mapOf("Referer" to origin, "Accept" to "*/*")
    }

    private fun upstream(): DataSource.Factory {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("VLC/3.0.20 LibVLC/3.0.20")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(30_000)
        return ResolvingDataSource.Factory(http) { spec ->
            spec.withAdditionalHeaders(headersFor(spec.uri.toString()))
        }
    }

    @Synchronized
    fun manager(context: Context): DownloadManager {
        manager?.let { return it }
        val ctx = context.applicationContext
        appContext = ctx
        val m = DownloadManager(
            ctx,
            database(ctx),
            cache(ctx),
            upstream(),
            Executors.newFixedThreadPool(2),
        )
        m.maxParallelDownloads = 2
        m.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(dm: DownloadManager, download: Download, finalException: Exception?) = refresh()
            override fun onDownloadRemoved(dm: DownloadManager, download: Download) = refresh()
            override fun onInitialized(dm: DownloadManager) = refresh()
        })
        manager = m
        refresh()
        return m
    }

    fun refresh() {
        val m = manager ?: return
        val out = mutableListOf<Item>()
        runCatching {
            m.downloadIndex.getDownloads().use { c ->
                while (c.moveToNext()) {
                    val d = c.download
                    val meta = Meta.from(d.request.data) ?: continue
                    out += Item(d.request.id, meta, d)
                }
            }
        }
        _items.value = out.sortedByDescending { it.download.startTimeMs }
        // O DownloadManager só avisa quando muda o estado, não o progresso:
        // enquanto houver download ativo, relê a cada 1 s (player e tela de Downloads)
        val active = out.any {
            it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_QUEUED || it.state == Download.STATE_RESTARTING
        }
        if (active && !tickScheduled) {
            tickScheduled = true
            mainHandler.postDelayed({ tickScheduled = false; refresh() }, 1_000)
        }
    }

    @Volatile private var tickScheduled = false
    private val mainHandler by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }

    fun init(context: Context) { runCatching { manager(context) } }

    fun find(id: String): Item? = _items.value.firstOrNull { it.id == id }

    fun completedByUri(url: String): Item? = _items.value.firstOrNull { it.isDone && it.download.request.uri.toString() == url }

    fun isHls(url: String): Boolean {
        val l = url.lowercase()
        return l.contains(".m3u8") || l.contains("format=m3u8") || l.contains("type=m3u8") || l.contains("/hls/") || l.contains("mpegurl")
    }

    /** Se esta URL já foi baixada, a fonte que lê do aparelho; senão null. */
    fun offlineSourceFor(url: String): MediaSource? {
        val ctx = appContext ?: return null
        val item = completedByUri(url) ?: return null
        return runCatching { offlineMediaSource(ctx, item) }.getOrNull()
    }

    /** Fonte do player lendo do que já foi baixado (sem internet). */
    fun offlineMediaSource(context: Context, item: Item): MediaSource {
        val factory = CacheDataSource.Factory()
            .setCache(cache(context))
            .setUpstreamDataSourceFactory(null)
            .setCacheWriteDataSinkFactory(null)
        return androidx.media3.exoplayer.offline.DownloadHelper.createMediaSource(item.download.request, factory)
    }

    /**
     * Começa a baixar. Devolve null se deu certo, ou a mensagem para mostrar.
     * HLS: escolhe uma qualidade só (até 720p) para não baixar todas.
     */
    suspend fun start(context: Context, url: String, meta: Meta): String? {
        val ctx = context.applicationContext
        manager(ctx)
        val id = idFor(meta.tmdbId, meta.mediaType, meta.season, meta.episode)
        find(id)?.let { if (it.state != Download.STATE_FAILED) return if (it.isDone) "Já está nos seus downloads" else "Já está baixando" }
        val lower = url.lowercase()
        if (lower.startsWith("rtmp") || lower.endsWith(".ts") && !isHls(url)) return "Esta fonte é ao vivo e não dá para baixar."
        val item = MediaItem.Builder().setUri(Uri.parse(url)).apply {
            if (isHls(url)) setMimeType(MimeTypes.APPLICATION_M3U8)
            else if (lower.substringBefore('?').endsWith(".mpd")) setMimeType(MimeTypes.APPLICATION_MPD)
        }.build()
        val helper = DownloadHelper.forMediaItem(
            ctx, item, DefaultRenderersFactory(ctx), upstream(),
        )
        val prepared: String? = suspendCancellableCoroutine { cont ->
            helper.prepare(object : DownloadHelper.Callback {
                override fun onPrepared(h: DownloadHelper) { if (cont.isActive) cont.resume(null) }
                override fun onPrepareError(h: DownloadHelper, e: java.io.IOException) {
                    if (cont.isActive) cont.resume("Não consegui ler esta fonte para baixar (${e.javaClass.simpleName}). Tente outro servidor.")
                }
            })
            cont.invokeOnCancellation { runCatching { helper.release() } }
        }
        if (prepared != null) { runCatching { helper.release() }; return prepared }
        return runCatching {
            if (isHls(url) && helper.periodCount > 0) {
                val params = DownloadHelper.getDefaultTrackSelectorParameters(ctx).buildUpon()
                    .setMaxVideoSize(1280, 720)
                    .setPreferredAudioLanguages("pt-BR", "pt", "por")
                    .build()
                for (p in 0 until helper.periodCount) {
                    helper.clearTrackSelections(p)
                    helper.addTrackSelection(p, params)
                }
            }
            val req: DownloadRequest = helper.getDownloadRequest(id, meta.toBytes())
            DownloadService.sendAddDownload(ctx, OfflineDownloadService::class.java, req, false)
            null
        }.getOrElse { "Não deu para começar o download: ${it.message ?: "erro"}" }.also { runCatching { helper.release() } }
    }

    fun remove(context: Context, id: String) {
        DownloadService.sendRemoveDownload(context.applicationContext, OfflineDownloadService::class.java, id, false)
    }

    fun retry(context: Context, item: Item) {
        DownloadService.sendAddDownload(context.applicationContext, OfflineDownloadService::class.java, item.download.request, false)
    }

    fun usedBytes(): Long = _items.value.sumOf { it.bytes }

    fun freeBytes(context: Context): Long = runCatching { StatFs(dir(context).path).availableBytes }.getOrDefault(0L)
}
