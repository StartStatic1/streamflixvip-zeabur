package com.streamflixvip.app

import android.app.Application
import com.streamflixvip.app.ads.AdsHelper
import com.streamflixvip.app.util.CrashLog

/**
 * Classe Application — inicializacao global (registro de erros, ads, etc.).
 */
class StreamFlixApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        runCatching { com.streamflixvip.app.data.DeviceLock.init(this) }
        runCatching { com.streamflixvip.app.ui.theme.AccentStore.init(this) }
        runCatching { AdsHelper.init(this) }
        runCatching { com.streamflixvip.app.notify.FollowedSeries.init(this) }
        runCatching { com.streamflixvip.app.notify.Notifier.schedule(this) }
        runCatching { com.streamflixvip.app.offline.OfflineVip.init(this) }
        runCatching { com.streamflixvip.app.offline.OfflineDownloads.init(this) }
        if (CrashLog.consumeNotice(this)) {
            runCatching {
                android.widget.Toast.makeText(
                    this,
                    "O app fechou por um erro. Ele foi registrado e enviado para correção.",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }
        }
        CrashLog.uploadPendingAsync(this)
    }
}
