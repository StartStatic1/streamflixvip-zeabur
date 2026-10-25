package com.streamflixvip.tv.util

import android.content.Context
import android.os.Build
import com.streamflixvip.tv.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Guarda o ultimo erro fatal num arquivo local (nao envia nada para fora).
 * Aparece em Ajustes > Sobre para dar para diagnosticar fechamentos no Fire TV.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        val now = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR")).format(Date())
        val text = buildString {
            appendLine("Data: $now")
            appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Aparelho: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Pouca memória: ${DeviceProfile.isLowRam}")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(sw.toString().take(12_000))
        }
        File(context.filesDir, FILE).writeText(text)
    }

    /** Resumo do ultimo erro (data + primeira linha da causa) ou null. */
    fun lastSummary(context: Context): String? = runCatching {
        val f = File(context.filesDir, FILE)
        if (!f.exists()) return null
        val lines = f.readLines()
        val date = lines.firstOrNull { it.startsWith("Data:") }?.removePrefix("Data:")?.trim()
        val cause = lines.dropWhile { it.isNotBlank() }.drop(1).firstOrNull()?.take(160)
        listOfNotNull(date, cause).joinToString(" · ")
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }
}
