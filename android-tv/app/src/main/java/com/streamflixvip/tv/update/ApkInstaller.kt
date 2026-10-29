package com.streamflixvip.tv.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Baixa o APK dentro do app (sem abrir navegador/Silk) e dispara o instalador.
 */
object ApkInstaller {

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(2, TimeUnit.MINUTES)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * @param onProgress 0..100
     * @return arquivo baixado
     */
    suspend fun download(
        context: Context,
        apkUrl: String,
        onProgress: (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "streamflix-tv-update.apk")
        val part = File(dir, "streamflix-tv-update.apk.part")
        if (out.exists()) out.delete()
        if (part.exists()) part.delete()
        var total = -1L
        var lastError: Exception? = null
        // ate 3 tentativas; se a conexao cair no meio, continua de onde parou (Range)
        for (attempt in 1..3) {
            try {
                val have = if (part.exists()) part.length() else 0L
                val builder = Request.Builder().url(apkUrl).header("User-Agent", "StreamFlixVIP-TV-Updater")
                if (have > 0) builder.header("Range", "bytes=$have-")
                client.newCall(builder.build()).execute().use { response ->
                    if (!response.isSuccessful) throw IllegalStateException("Download falhou: HTTP ${response.code}")
                    val body = response.body ?: throw IllegalStateException("Resposta vazia")
                    val resumed = have > 0 && response.code == 206
                    if (!resumed && part.exists()) part.delete()
                    val start = if (resumed) have else 0L
                    val len = body.contentLength()
                    if (len > 0) total = start + len
                    body.byteStream().use { input ->
                        java.io.FileOutputStream(part, resumed).use { output ->
                            val buffer = ByteArray(128 * 1024)
                            var read: Int
                            var done = start
                            var lastPct = -1
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                done += read
                                if (total > 0) {
                                    val pct = ((done * 100) / total).toInt().coerceIn(0, 99)
                                    if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                                }
                            }
                            output.flush()
                        }
                    }
                }
                if (total > 0 && part.length() != total) throw java.io.IOException("Download incompleto (${part.length()}/$total)")
                lastError = null
                break
            } catch (e: Exception) {
                lastError = e
                if (attempt < 3) kotlinx.coroutines.delay(1500L * attempt)
            }
        }
        lastError?.let { throw it }
        verifyApk(context, part)
        if (!part.renameTo(out)) { part.copyTo(out, overwrite = true); part.delete() }
        onProgress(100)
        out
    }

    /** Confere se o arquivo e um APK inteiro e do mesmo app (evita "erro ao analisar o pacote"). */
    private fun verifyApk(context: Context, file: File) {
        if (file.length() < 512 * 1024) throw java.io.IOException("Arquivo baixado muito pequeno — tente de novo")
        val info = runCatching { context.packageManager.getPackageArchiveInfo(file.absolutePath, 0) }.getOrNull()
            ?: throw java.io.IOException("Arquivo baixado corrompido — tente de novo")
        if (info.packageName != context.packageName) throw java.io.IOException("Arquivo de atualizacao invalido")
    }

    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /** Abre a tela do sistema para permitir instalar apps desconhecidos. */
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun install(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
