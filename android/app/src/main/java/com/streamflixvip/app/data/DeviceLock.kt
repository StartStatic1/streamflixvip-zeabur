package com.streamflixvip.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.streamflixvip.app.network.NetworkModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * VIP em 1 celular por vez. O servidor decide; o app só BLOQUEIA quando o
 * servidor responde explicitamente "em uso em outro celular". Sem internet,
 * timeout ou erro do servidor = continua VIP (nunca derruba quem pagou).
 */
object DeviceLock {
    data class Info(
        val message: String,
        val otherLabel: String?,
        val swapsLeft: Int?,
        val limitReached: Boolean,
    )

    private val _state = MutableStateFlow<Info?>(null)
    val state: StateFlow<Info?> = _state

    @Volatile var blocked: Boolean = false
        private set

    @Volatile var deviceId: String? = null
        private set
    val deviceLabel: String = runCatching { "${Build.MANUFACTURER} ${Build.MODEL}".trim() }.getOrDefault("Android")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var lastAutoCheck = 0L

    @SuppressLint("HardwareIds")
    fun init(context: Context) {
        if (deviceId != null) return
        deviceId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()?.takeIf { it.isNotBlank() }?.let { "ph-$it" }
    }

    /** Resposta de stream veio com X-Device-Lock: phone -> confere (no máx. 1x a cada 30 s). */
    fun onServerSaysInUse() {
        val now = System.currentTimeMillis()
        if (now - lastAutoCheck < 30_000) return
        lastAutoCheck = now
        scope.launch { runCatching { check(claim = false) } }
    }

    /**
     * @return true = liberado neste aparelho, false = bloqueado (servidor disse), null = não deu pra saber (mantém como está).
     */
    suspend fun check(claim: Boolean = false): Boolean? = withContext(Dispatchers.IO) {
        if (deviceId == null || NetworkModule.sessionStore?.accessToken.isNullOrBlank()) return@withContext null
        try {
            val body = """{"action":"${if (claim) "claim" else "check"}"}"""
                .toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url(NetworkModule.ZEABUR_BASE_URL + "api/device-lock")
                .post(body)
                .build()
            NetworkModule.okHttpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@withContext null
                val j = org.json.JSONObject(res.body?.string() ?: return@withContext null)
                if (j.optBoolean("ok", false)) {
                    setBlocked(null)
                    true
                } else {
                    val code = j.optString("code")
                    if (code != "DEVICE_IN_USE" && code != "SWAP_LIMIT") return@withContext null
                    setBlocked(
                        Info(
                            message = j.optString("message").ifBlank { "Sua conta VIP está em uso em outro celular." },
                            otherLabel = j.optString("otherLabel").takeIf { it.isNotBlank() && it != "null" },
                            swapsLeft = if (j.has("swapsLeft")) j.optInt("swapsLeft") else null,
                            limitReached = code == "SWAP_LIMIT" || j.optInt("swapsLeft", 1) <= 0,
                        ),
                    )
                    false
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun setBlocked(info: Info?) {
        val was = blocked
        blocked = info != null
        _state.value = info
        if (was != blocked) VipStatusHolder.reapply()
    }

    fun clearOnSignOut() {
        blocked = false
        _state.value = null
    }
}
