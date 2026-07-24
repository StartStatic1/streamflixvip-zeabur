package com.streamflixvip.tv

import android.app.Application
import com.streamflixvip.tv.data.SessionStore
import com.streamflixvip.tv.network.NetworkModule

/**
 * Classe Application — inicializa SessionStore e vincula ao NetworkModule
 * para que o authenticator de refresh token funcione corretamente.
 */
class StreamFlixTvApp : Application() {

    lateinit var sessionStore: SessionStore
        private set

    override fun onCreate() {
        super.onCreate()
        sessionStore = SessionStore(this)
        NetworkModule.sessionStore = sessionStore
    }
}
