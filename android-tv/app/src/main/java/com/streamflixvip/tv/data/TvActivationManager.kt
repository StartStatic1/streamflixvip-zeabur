package com.streamflixvip.tv.data

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import com.streamflixvip.tv.network.NetworkModule
import com.streamflixvip.tv.network.TvStatusRequest

/**
 * Ativação da TV por código VIP — sem login de e-mail/senha, diferente do
 * mobile. Guarda localmente a expiração já validada pelo servidor
 * (`/api/activate-tv`), e revalida de vez em quando contra `/api/tv-status`
 * pra pegar revogação manual feita direto no Supabase (ver ADMIN.md /
 * migrations/tv_activations.sql).
 *
 * device_id = Settings.Secure.ANDROID_ID: estável mesmo reinstalando o
 * app (só muda com reset de fábrica do aparelho), então dá pra identificar
 * "essa TV" sem precisar gerar e persistir um UUID próprio.
 */
class TvActivationManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sfv_tv_activation", Context.MODE_PRIVATE)

    val deviceId: String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown-device"

    private var expiresAtMs: Long
        get() = prefs.getLong(KEY_EXPIRES_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_EXPIRES_AT, value).apply()

    var planLabel: String?
        get() = prefs.getString(KEY_PLAN_LABEL, null)
        private set(value) = prefs.edit().putString(KEY_PLAN_LABEL, value).apply()

    /** true se o cache local diz que essa TV está ativada e dentro da validade. */
    val isActivatedLocally: Boolean get() = expiresAtMs > System.currentTimeMillis()

    /** Envia o código digitado pro servidor. Em caso de sucesso, já salva o cache local. */
    suspend fun activate(code: String): Result<Unit> {
        return try {
            val response = NetworkModule.vipApi.activateTv(
                com.streamflixvip.tv.network.ActivateTvRequest(code = code.trim(), deviceId = deviceId),
            )
            if (response.success && response.expiresAt != null) {
                expiresAtMs = parseIso(response.expiresAt) ?: 0L
                planLabel = response.planLabel
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.error ?: "Código inválido."))
            }
        } catch (e: retrofit2.HttpException) {
            val message = runCatching {
                e.response()?.errorBody()?.string()
                    ?.let { org.json.JSONObject(it).optString("error") }
            }.getOrNull()
            Result.failure(Exception(message?.takeIf { it.isNotBlank() } ?: "Não foi possível ativar. Tente novamente."))
        } catch (e: Exception) {
            Result.failure(Exception("Sem conexão. Verifique a internet da TV e tente de novo."))
        }
    }

    /**
     * Revalida contra o servidor (chamar no início do app, sem bloquear —
     * se falhar por rede, mantém o que já está em cache, pra não deslogar
     * alguém ativo só porque a TV caiu de internet por um instante).
     */
    suspend fun revalidate() {
        try {
            val response = NetworkModule.vipApi.getTvStatus(TvStatusRequest(deviceId = deviceId))
            if (response.active && response.expiresAt != null) {
                expiresAtMs = parseIso(response.expiresAt) ?: expiresAtMs
                planLabel = response.planLabel
            } else if (!response.active) {
                expiresAtMs = 0L
            }
        } catch (_: Exception) {
        }
    }

    /** Limpa o cache local de ativação (usado na tela Conta / "Desativar este aparelho"). */
    fun clearLocalActivation() {
        expiresAtMs = 0L
        planLabel = null
        prefs.edit().remove(KEY_EXPIRES_AT).remove(KEY_PLAN_LABEL).apply()
    }

    private fun parseIso(iso: String): Long? = runCatching {
        java.time.Instant.parse(iso).toEpochMilli()
    }.getOrNull()

    companion object {
        private const val KEY_EXPIRES_AT = "expires_at_ms"
        private const val KEY_PLAN_LABEL = "plan_label"
    }
}
