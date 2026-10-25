package com.streamflixvip.tv.util

import android.app.ActivityManager
import android.content.Context

/**
 * Perfil do aparelho: Fire TV Stick / TV box baratas tem pouca RAM (1-1,5 GB),
 * heap Java pequeno e, muitas vezes, um decodificador de video so.
 */
object DeviceProfile {
    @Volatile
    var isLowRam: Boolean = false
        private set

    fun init(context: Context) {
        isLowRam = runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            am.isLowRamDevice || info.totalMem < 2_000_000_000L || am.memoryClass <= 192
        }.getOrDefault(false)
    }

    /** Teto do buffer do player em bytes (padrao do ExoPlayer pode passar de 100 MB). */
    val playerBufferBytes: Int get() = if (isLowRam) 24 * 1024 * 1024 else 64 * 1024 * 1024
}
