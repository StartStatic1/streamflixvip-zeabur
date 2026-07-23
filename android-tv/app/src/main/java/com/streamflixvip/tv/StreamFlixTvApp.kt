package com.streamflixvip.tv

import android.app.Application

/**
 * Classe Application do app de TV — mesmo papel da StreamFlixApp do
 * celular (ver android/.../StreamFlixApp.kt): ponto único de
 * inicialização de coisas globais futuras (analytics, crash reporting).
 */
class StreamFlixTvApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
