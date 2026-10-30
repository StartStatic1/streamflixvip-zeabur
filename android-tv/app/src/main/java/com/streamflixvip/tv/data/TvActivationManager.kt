package com.streamflixvip.tv.data

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaDrm
import android.provider.Settings
import android.util.Base64
import com.streamflixvip.tv.network.NetworkModule
import com.streamflixvip.tv.network.TvStatusRequest
import java.util.UUID

/**
 * Ativação da TV por código VIP — sem login de e-mail/senha.
 *
 * device_id prioritário = Widevine MediaDrm (costuma sobreviver a reinstalação
 * e troca de assinatura do APK). Fallback = ANDROID_ID.
 *
 * O cache local (SharedPreferences) some ao desinstalar; por isso o splash
 * SEMPRE chama [revalidate] antes de decidir home vs ativação. Se o servidor
 * ainda tem esse device_id ativo, a TV entra direto sem digitar o código de novo.
 */
class TvActivationManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sfv_tv_activation", Context.MODE_PRIVATE)

    val deviceId: String = resolveStableDeviceId(context)

    private var expiresAtMs: Long
        get() = prefs.getLong(KEY_EXPIRES_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_EXPIRES_AT, value).apply()

    var planLabel: String?
        get() = prefs.getString(KEY_PLAN_LABEL, null)
        private set(value) = prefs.edit().putString(KEY_PLAN_LABEL, value).apply()

    /** true se o cache local diz que essa TV está ativada e dentro da validade. */
    val isActivatedLocally: Boolean get() = expiresAtMs > System.currentTimeMillis()

    /** Mensagem "VIP em uso em outra TV" (null = tudo certo). Só vem de resposta EXPLÍCITA do servidor. */
    val inUseMessage = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    /** true = limite de trocas atingido (não mostra "Usar nesta TV"). */
    @Volatile var swapLimitReached: Boolean = false
        private set

    private val label: String = runCatching { android.os.Build.MODEL.orEmpty().take(40) }.getOrDefault("")

    class TvInUseException(message: String, val limit: Boolean) : Exception(message)

    /** "Usar nesta TV": move a assinatura pra esta TV (a outra é desconectada). */
    suspend fun claimHere(): Result<Unit> {
        return try {
            val r = NetworkModule.vipApi.getTvStatus(TvStatusRequest(deviceId = deviceId, claim = true, deviceLabel = label))
            if (r.active && r.expiresAt != null) {
                expiresAtMs = parseIso(r.expiresAt) ?: 0L
                planLabel = r.planLabel
                inUseMessage.value = null
                swapLimitReached = false
                if (isActivatedLocally) Result.success(Unit) else Result.failure(Exception("Não foi possível ativar. Tente novamente."))
            } else if (r.inUse) {
                swapLimitReached = r.code == "SWAP_LIMIT"
                inUseMessage.value = r.message ?: "Sua conta VIP está em uso em outra TV."
                Result.failure(TvInUseException(inUseMessage.value!!, swapLimitReached))
            } else {
                Result.failure(Exception("Ativação não encontrada. Digite o código de novo."))
            }
        } catch (_: Exception) {
            Result.failure(Exception("Sem conexão. Verifique a internet da TV e tente de novo."))
        }
    }

    suspend fun activate(code: String, replace: Boolean = false): Result<Unit> {
        return try {
            val response = NetworkModule.vipApi.activateTv(
                com.streamflixvip.tv.network.ActivateTvRequest(code = code.trim(), deviceId = deviceId, deviceLabel = label.ifBlank { null }, replace = replace),
            )
            if (response.success && response.expiresAt != null) {
                expiresAtMs = parseIso(response.expiresAt) ?: 0L
                planLabel = response.planLabel
                inUseMessage.value = null
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.error ?: "Código inválido."))
            }
        } catch (e: retrofit2.HttpException) {
            val json = runCatching {
                e.response()?.errorBody()?.string()?.let { org.json.JSONObject(it) }
            }.getOrNull()
            val message = json?.optString("error")
            val errCode = json?.optString("code").orEmpty()
            if (e.code() == 409 && (errCode == "TV_IN_USE" || errCode == "SWAP_LIMIT")) {
                return Result.failure(TvInUseException(message?.takeIf { it.isNotBlank() } ?: "Este VIP já está em uso em outra TV.", errCode == "SWAP_LIMIT"))
            }
            Result.failure(Exception(message?.takeIf { it.isNotBlank() } ?: "Não foi possível ativar. Tente novamente."))
        } catch (e: Exception) {
            Result.failure(Exception("Sem conexão. Verifique a internet da TV e tente de novo."))
        }
    }

    /**
     * Revalida contra o servidor. Se a rede falhar, mantém o cache local
     * (não desloga só porque a TV perdeu internet um instante).
     * @return true se o servidor (ou cache) confirma ativação válida.
     */
    suspend fun revalidate(): Boolean {
        try {
            val response = NetworkModule.vipApi.getTvStatus(TvStatusRequest(deviceId = deviceId, deviceLabel = label.ifBlank { null }))
            if (response.active && response.expiresAt != null) {
                expiresAtMs = parseIso(response.expiresAt) ?: expiresAtMs
                planLabel = response.planLabel
                inUseMessage.value = null
            } else if (!response.active) {
                expiresAtMs = 0L
                if (response.inUse) {
                    swapLimitReached = response.code == "SWAP_LIMIT"
                    inUseMessage.value = response.message ?: "Sua conta VIP está em uso em outra TV."
                } else {
                    inUseMessage.value = null
                }
            }
        } catch (_: Exception) {
            // mantém cache
        }
        return isActivatedLocally
    }

    fun clearLocalActivation() {
        expiresAtMs = 0L
        planLabel = null
        prefs.edit().remove(KEY_EXPIRES_AT).remove(KEY_PLAN_LABEL).apply()
    }

    /** Aceita "...Z" e "...+00:00" (Supabase). Instant.parse do Android 9-11 não lê offset. */
    private fun parseIso(iso: String): Long? =
        runCatching { java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull()
            ?: runCatching { java.time.OffsetDateTime.parse(iso.trim().replace(' ', 'T')).toInstant().toEpochMilli() }.getOrNull()

    /** Pareamento por QR/código confirmado: grava a validade que veio do servidor. */
    fun applyPaired(expiresAt: String?, plan: String?): Boolean {
        val ms = expiresAt?.let { parseIso(it) } ?: return isActivatedLocally
        if (ms > System.currentTimeMillis()) {
            expiresAtMs = ms
            if (plan != null) planLabel = plan
        }
        return isActivatedLocally
    }

    companion object {
        private const val KEY_EXPIRES_AT = "expires_at_ms"
        private const val KEY_PLAN_LABEL = "plan_label"

        private val WIDEVINE_UUID: UUID =
            UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")

        /**
         * ID estável do aparelho. MediaDrm Widevine costuma permanecer igual
         * após desinstalar/reinstalar e até com outra assinatura de APK.
         * ANDROID_ID muda quando a signing key muda — por isso não é primário.
         */
        fun resolveStableDeviceId(context: Context): String {
            val drmId = runCatching {
                val drm = MediaDrm(WIDEVINE_UUID)
                try {
                    val bytes = drm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
                    if (bytes != null && bytes.isNotEmpty()) {
                        Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
                            .trimEnd('=')
                            .take(32)
                    } else null
                } finally {
                    runCatching { drm.release() }
                }
            }.getOrNull()

            if (!drmId.isNullOrBlank()) return "wv_$drmId"

            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID,
            )
            return androidId?.takeIf { it.isNotBlank() } ?: "unknown-device"
        }
    }
}
