package com.streamflixvip.tv.util

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import com.streamflixvip.tv.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Erros fatais do app.
 *
 * - Guarda o ultimo erro em last_crash.txt (Ajustes > Sobre).
 * - Guarda um relatorio pendente (crash_reports/) e envia para
 *   /api/crash-report na proxima abertura (fail-open: sem rede, tenta depois).
 * - Android 11+: tambem envia ANR / crash nativo vistos via ApplicationExitInfo.
 * - Nada sensivel: sem token, senha, playlist ou URL de stream (o servidor
 *   ainda passa um filtro). So versao, aparelho, tela/acao e stacktrace.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"
    private const val DIR = "crash_reports"
    private const val NOTICE = "crash_notice"
    private const val PREFS = "crash_log"
    private const val KEY_EXIT_TS = "exit_info_ts"
    private const val MAX_PENDING = 5

    @Volatile private var screen: String = "start"
    @Volatile private var appCtx: Context? = null
    private val trail = ArrayDeque<String>()

    /** Tela atual (rota de navegacao ou aba). */
    fun screen(name: String) {
        screen = name.take(100)
        action("tela $name")
    }

    /** Ultimas acoes do usuario (curto, sem dados pessoais). */
    fun action(text: String) {
        synchronized(trail) {
            trail.addLast(text.take(60))
            while (trail.size > 6) trail.removeFirst()
        }
    }

    private fun trailText(): String = synchronized(trail) { trail.joinToString(" > ") }

    fun install(context: Context) {
        val app = context.applicationContext
        appCtx = app
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun stackOf(error: Throwable): String {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        return sw.toString().take(12_000)
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val stack = stackOf(error)
        val now = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR")).format(Date())
        val text = buildString {
            appendLine("Data: $now")
            appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Aparelho: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Pouca memória: ${DeviceProfile.isLowRam}")
            appendLine("Thread: ${thread.name}")
            appendLine("Tela: $screen")
            appendLine()
            append(stack)
        }
        runCatching { File(context.filesDir, FILE).writeText(text) }
        runCatching { savePending(context, report("crash", stack, thread.name, System.currentTimeMillis())) }
        runCatching { File(context.filesDir, NOTICE).writeText("1") }
    }

    private fun report(kind: String, stack: String, thread: String?, ts: Long): JSONObject {
        val ram = runCatching {
            val am = appCtx?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val mi = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
            "${mi.totalMem / (1024 * 1024)}MB heap${Runtime.getRuntime().maxMemory() / (1024 * 1024)}MB" +
                if (DeviceProfile.isLowRam) " low" else ""
        }.getOrNull()
        return JSONObject().apply {
            put("kind", kind)
            put("appVersion", BuildConfig.VERSION_NAME)
            put("versionCode", BuildConfig.VERSION_CODE.toString())
            put("manufacturer", Build.MANUFACTURER)
            put("brand", Build.BRAND)
            put("model", Build.MODEL)
            put("device", Build.DEVICE)
            put("android", Build.VERSION.RELEASE)
            put("sdk", Build.VERSION.SDK_INT.toString())
            put("abi", Build.SUPPORTED_ABIS.firstOrNull() ?: "")
            if (ram != null) put("ram", ram)
            put("screen", screen)
            put("action", trailText())
            if (thread != null) put("thread", thread)
            put("message", stack.lineSequence().firstOrNull()?.take(500) ?: kind)
            put("stack", stack)
            put("ts", ts)
        }
    }

    private fun savePending(context: Context, json: JSONObject) {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        File(dir, "${json.optLong("ts")}_${json.optString("kind")}.json").writeText(json.toString())
        dir.listFiles()?.sortedBy { it.name }?.dropLast(MAX_PENDING)?.forEach { it.delete() }
    }

    /** Mostrar "o erro foi registrado" uma vez, depois de um fechamento. */
    fun consumeNotice(context: Context): Boolean = runCatching {
        val f = File(context.filesDir, NOTICE)
        f.exists().also { if (it) f.delete() }
    }.getOrDefault(false)

    /** Chamar na abertura: coleta ANR/nativo (API 30+) e envia pendentes. Nunca lanca. */
    fun uploadPendingAsync(context: Context) {
        val app = context.applicationContext
        Thread({
            runCatching { collectExitInfo(app) }
            runCatching { uploadPending(app) }
        }, "crash-upload").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
    }

    private fun collectExitInfo(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val since = prefs.getLong(KEY_EXIT_TS, 0L)
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val infos = am.getHistoricalProcessExitReasons(context.packageName, 0, 5)
        var newest = since
        for (info in infos) {
            if (info.timestamp <= since) continue
            newest = maxOf(newest, info.timestamp)
            val kind = when (info.reason) {
                ApplicationExitInfo.REASON_ANR -> "anr"
                ApplicationExitInfo.REASON_CRASH_NATIVE -> "native"
                ApplicationExitInfo.REASON_LOW_MEMORY -> "lowmem"
                else -> null
            } ?: continue
            if (since == 0L) continue // primeira vez: so marca, nao reenvia historico antigo
            val trace = runCatching {
                if (kind == "anr") info.traceInputStream?.bufferedReader()?.use { r ->
                    val sb = StringBuilder()
                    var line = r.readLine()
                    while (line != null && sb.length < 10_000) { sb.appendLine(line); line = r.readLine() }
                    sb.toString()
                } else null
            }.getOrNull()
            val stack = "${kind.uppercase()}: ${info.description ?: ""} (pss ${info.pss / 1024}MB)\n" + (trace ?: "")
            savePending(context, report(kind, stack.take(12_000), null, info.timestamp).apply { put("screen", "?"); put("action", "") })
        }
        prefs.edit().putLong(KEY_EXIT_TS, maxOf(newest, 1L)).apply()
    }

    private fun uploadPending(context: Context) {
        val files = File(context.filesDir, DIR).listFiles()?.sortedBy { it.name }?.take(MAX_PENDING) ?: return
        if (files.isEmpty()) return
        val arr = JSONArray()
        files.forEach { f -> runCatching { arr.put(JSONObject(f.readText())) }.onFailure { f.delete() } }
        if (arr.length() == 0) return
        val conn = URL(BuildConfig.API_BASE_URL + "api/crash-report").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(JSONObject().put("reports", arr).toString().toByteArray()) }
            val code = conn.responseCode
            // 2xx gravou; 400 = relatorio invalido (nao adianta reenviar). 429/5xx: tenta depois.
            if (code in 200..299 || code == 400) files.forEach { it.delete() }
        } finally {
            conn.disconnect()
        }
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
