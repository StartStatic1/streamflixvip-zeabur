package com.streamflixvip.app.ui.livetv

import com.streamflixvip.app.network.LiveChannel

/**
 * Canal selecionado para o player ao vivo — evita empilhar dezenas de URLs
 * na rota de navegação. Consumido uma vez ao abrir LivePlayerScreen.
 */
object PendingLiveChannel {
    @Volatile
    private var pending: LiveChannel? = null

    @Volatile var epgNow: String? = null
        private set
    @Volatile var epgNext: String? = null
        private set

    fun setEpg(now: String?, next: String?) { epgNow = now; epgNext = next }

    fun set(channel: LiveChannel) {
        pending = channel
    }

    fun consume(): LiveChannel? {
        val c = pending
        pending = null
        return c
    }
}
