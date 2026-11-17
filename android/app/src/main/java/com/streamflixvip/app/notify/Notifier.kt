package com.streamflixvip.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.streamflixvip.app.MainActivity
import com.streamflixvip.app.R
import com.streamflixvip.app.data.PreferencesStore
import com.streamflixvip.app.network.TmdbImages
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Notificações de verdade no celular, sem Firebase: um trabalho do WorkManager
 * pergunta ao servidor a cada ~45 min (/api/notify-feed) se tem episódio novo
 * das séries seguidas ou aviso do painel marcado como notificação.
 */
object Notifier {
    const val CHANNEL_EPISODES = "sfv_new_episodes"
    const val CHANNEL_NEWS = "sfv_news"
    private const val WORK_PERIODIC = "sfv_notify_periodic"
    private const val WORK_NOW = "sfv_notify_now"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_EPISODES, "Episódios novos", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Quando sai episódio novo de uma série da sua lista ou que você está assistindo"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_NEWS, "Novidades e avisos", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Filmes novos, promoções e avisos de manutenção"
            },
        )
    }

    /** Liga (ou desliga) a consulta periódica conforme a chave do Perfil. */
    fun schedule(context: Context) {
        val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        if (!PreferencesStore(context).notificationsEnabled) {
            wm.cancelUniqueWork(WORK_PERIODIC)
            return
        }
        ensureChannels(context)
        val req = PeriodicWorkRequestBuilder<NotifyWorker>(45, TimeUnit.MINUTES, 15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(10, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(WORK_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Uma consulta já (ex.: logo depois de ligar a chave). */
    fun checkNow(context: Context) {
        val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        val req = OneTimeWorkRequestBuilder<NotifyWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.REPLACE, req)
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun post(
        context: Context,
        notifId: Int,
        channel: String,
        title: String,
        body: String,
        tmdbId: Int?,
        mediaType: String?,
        posterPath: String? = null,
        season: Int = -1,
        episode: Int = -1,
    ) {
        if (!canPost(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (tmdbId != null && tmdbId > 0 && !mediaType.isNullOrBlank()) {
                putExtra(PendingOpen.EXTRA_TMDB_ID, tmdbId)
                putExtra(PendingOpen.EXTRA_MEDIA_TYPE, mediaType)
                putExtra(PendingOpen.EXTRA_SEASON, season)
                putExtra(PendingOpen.EXTRA_EPISODE, episode)
            } else {
                putExtra(PendingOpen.EXTRA_OPEN_PROFILE, true)
            }
        }
        val pi = PendingIntent.getActivity(
            context, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setColor(0xFF00E5FF.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        posterPath?.let { p -> loadBitmap(TmdbImages.poster(p, "w185"))?.let { b.setLargeIcon(it) } }
        runCatching { NotificationManagerCompat.from(context).notify(notifId, b.build()) }
    }

    private fun loadBitmap(url: String?): Bitmap? {
        url ?: return null
        return runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 6000; c.readTimeout = 8000
            c.inputStream.use { BitmapFactory.decodeStream(it) }.also { c.disconnect() }
        }.getOrNull()
    }
}
