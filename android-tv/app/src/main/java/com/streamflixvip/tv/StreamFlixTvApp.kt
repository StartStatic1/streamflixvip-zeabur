package com.streamflixvip.tv

import android.app.Application
import com.streamflixvip.tv.data.SessionStore
import com.streamflixvip.tv.data.TvActivationManager
import com.streamflixvip.tv.network.NetworkModule

/**
 * Classe Application — inicializa SessionStore + deviceId e vincula ao NetworkModule
 * para gate VIP (live-tv) e refresh de token.
 */
class StreamFlixTvApp : Application() {

    lateinit var sessionStore: SessionStore
        private set

    override fun onCreate() {
        super.onCreate()
        sessionStore = SessionStore(this)
        NetworkModule.sessionStore = sessionStore
        // VIP na TV e por device_id (tv_activations), nao por e-mail JWT
        NetworkModule.deviceId = TvActivationManager.resolveStableDeviceId(this)
    }
}
