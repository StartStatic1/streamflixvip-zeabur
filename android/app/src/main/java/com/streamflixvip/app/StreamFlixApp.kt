package com.streamflixvip.app

import android.app.Application

/**
 * Classe Application — ponto único de inicialização do app.
 * Por ora só existe pra dar um lugar certo pra inicializar coisas globais
 * no futuro (ex: analytics, crash reporting) sem precisar mexer na
 * Activity depois.
 */
class StreamFlixApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
