package com.streamflixvip.app.ui.livetv

import com.streamflixvip.app.network.LiveChannel

/**
 * Canal selecionado para o player ao vivo — evita empilhar dezenas de URLs
 * na rota de navegação. Consumido uma vez ao abrir LivePlayerScreen.
 */
object PendingLiveChannel {
    @Volatile
    private var pending: LiveChannel? = null

    fun set(channel: LiveChannel) {
        pending = channel
    }

    fun consume(): LiveChannel? {
        val c = pending
        pending = null
        return c
    }
}
