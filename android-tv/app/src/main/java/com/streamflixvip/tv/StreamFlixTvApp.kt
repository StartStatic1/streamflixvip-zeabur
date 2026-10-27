package com.streamflixvip.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.streamflixvip.tv.util.CrashLog
import com.streamflixvip.tv.util.DeviceProfile
import com.streamflixvip.tv.data.SessionStore
import com.streamflixvip.tv.data.TvActivationManager
import com.streamflixvip.tv.data.TvSettingsStore
import com.streamflixvip.tv.network.NetworkModule

/**
 * Classe Application — inicializa SessionStore + deviceId e vincula ao NetworkModule
 * para gate VIP (live-tv) e refresh de token.
 */
class StreamFlixTvApp : Application(), ImageLoaderFactory {

    /** Coil com limite de memoria; em aparelho fraco usa RGB_565 (metade da RAM por imagem). */
    override fun newImageLoader(): ImageLoader {
        val low = DeviceProfile.isLowRam
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this).maxSizePercent(if (low) 0.12 else 0.2).build()
            }
            .diskCache {
                DiskCache.Builder().directory(cacheDir.resolve("img")).maxSizeBytes(if (low) 60L * 1024 * 1024 else 150L * 1024 * 1024).build()
            }
            .allowRgb565(low)
            .crossfade(!low)
            .build()
    }


    lateinit var sessionStore: SessionStore
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        DeviceProfile.init(this)
        TvSettingsStore.init(this)
        com.streamflixvip.tv.ui.livetv.LiveHostHealth.init(this)
        sessionStore = SessionStore(this)
        NetworkModule.sessionStore = sessionStore
        // VIP na TV e por device_id (tv_activations), nao por e-mail JWT
        NetworkModule.deviceId = TvActivationManager.resolveStableDeviceId(this)
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
