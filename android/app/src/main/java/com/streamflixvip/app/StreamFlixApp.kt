package com.streamflixvip.app

import android.app.Application
import com.streamflixvip.app.ads.AdsHelper

/**
 * Classe Application — inicializacao global (ads, etc.).
 */
class StreamFlixApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AdsHelper.init(this)
    }
}
