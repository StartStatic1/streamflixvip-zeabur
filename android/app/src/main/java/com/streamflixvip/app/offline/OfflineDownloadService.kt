package com.streamflixvip.app.offline

import android.app.Notification
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.streamflixvip.app.R

/** Serviço que mantém os downloads andando com o app fechado (mostra o progresso na barra). */
@OptIn(UnstableApi::class)
class OfflineDownloadService : DownloadService(
    FOREGROUND_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.offline_channel_name,
    0,
) {
    private val helper by lazy { DownloadNotificationHelper(this, CHANNEL_ID) }

    override fun getDownloadManager(): DownloadManager = OfflineDownloads.manager(this)

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification {
        val names = downloads.mapNotNull { OfflineDownloads.Meta.from(it.request.data)?.title }
        val msg = when (names.size) {
            0 -> "Preparando download"
            1 -> names[0]
            else -> "${names.size} downloads: ${names.first()} e mais"
        }
        return helper.buildProgressNotification(this, R.drawable.ic_stat_notify, null, msg, downloads, notMetRequirements)
    }

    companion object {
        const val CHANNEL_ID = "sfv_offline_downloads"
        const val FOREGROUND_ID = 7301
    }
}
