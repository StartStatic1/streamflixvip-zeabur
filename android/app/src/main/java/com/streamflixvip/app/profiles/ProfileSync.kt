package com.streamflixvip.app.profiles

import com.streamflixvip.app.BuildConfig
import com.streamflixvip.app.data.AuthTokenHelper
import com.streamflixvip.app.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sincroniza a lista de perfis com a tabela `user_profiles` (uma linha por conta, coluna jsonb).
 * Se a tabela ainda não existir (migration não aplicada) o servidor responde 404/400 e marcamos
 * [available]=false: o app segue só com os perfis locais, sem erro para o usuário.
 */
object ProfileSync {
    @Volatile var available: Boolean? = null
        private set

    private val base get() = BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/user_profiles"

    /** Baixa da nuvem; se houver perfis lá, substitui os locais. Se a nuvem estiver vazia, envia os locais. */
    suspend fun pull(userId: String) = withContext(Dispatchers.IO) {
        runCatching {
            val token = AuthTokenHelper.validAccessToken() ?: return@runCatching
            val req = Request.Builder()
                .url("$base?user_id=eq.$userId&select=profiles")
                .header("apikey", NetworkModule.supabaseAnonKey)
                .header("Authorization", "Bearer $token")
                .get().build()
            NetworkModule.okHttpClient.newCall(req).execute().use { r ->
                if (!r.isSuccessful) { if (r.code == 404 || r.code == 400) available = false; return@runCatching }
                available = true
                val rows = JSONArray(r.body?.string().orEmpty().ifBlank { "[]" })
                val remote = rows.optJSONObject(0)?.optJSONArray("profiles")?.toString()
                val list = ProfileStore.parse(remote)
                if (list.isNotEmpty()) withContext(Dispatchers.Main) { ProfileStore.replaceAll(list) }
                else push(userId)
            }
        }
    }

    suspend fun push(userId: String) = withContext(Dispatchers.IO) {
        if (available == false) return@withContext
        runCatching {
            val token = AuthTokenHelper.validAccessToken() ?: return@runCatching
            val body = JSONObject()
                .put("user_id", userId)
                .put("profiles", ProfileStore.toJsonArray())
                .toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("$base?on_conflict=user_id")
                .header("apikey", NetworkModule.supabaseAnonKey)
                .header("Authorization", "Bearer $token")
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(body).build()
            NetworkModule.okHttpClient.newCall(req).execute().use { r ->
                if (r.code == 404 || r.code == 400) available = false
            }
        }
    }
}
