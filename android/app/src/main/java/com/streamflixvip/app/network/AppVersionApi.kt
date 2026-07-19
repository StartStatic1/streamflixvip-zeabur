package com.streamflixvip.app.network

import retrofit2.http.GET

/**
 * Consulta a versão mais recente publicada do app, via api/app-version.js
 * (mesmo backend Express do site). Chamado uma vez no início da Activity
 * — se a versão instalada estiver desatualizada, MainActivity bloqueia a
 * navegação e mostra a tela de UpdateRequiredScreen.
 */
interface AppVersionApi {
    @GET("api/app-version")
    suspend fun getLatestVersion(): AppVersionResponse
}

data class AppVersionResponse(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val forceUpdate: Boolean,
    val releaseNotes: String? = null,
)
