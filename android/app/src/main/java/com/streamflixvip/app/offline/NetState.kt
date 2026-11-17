package com.streamflixvip.app.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Tem internet agora? (para o aviso "Sem internet" e o atalho dos downloads). */
object NetState {
    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online
    private var cm: ConnectivityManager? = null

    fun init(context: Context) {
        if (cm != null) return
        val c = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        cm = c
        update()
        runCatching {
            c.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = update()
                override fun onLost(network: Network) = update()
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = update()
            })
        }
    }

    private fun update() {
        val c = cm ?: return
        _online.value = runCatching {
            val caps = c.getNetworkCapabilities(c.activeNetwork)
            caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(true)
    }
}
