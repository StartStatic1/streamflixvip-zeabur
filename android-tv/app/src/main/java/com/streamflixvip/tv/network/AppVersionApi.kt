package com.streamflixvip.tv.network

import com.squareup.moshi.JsonClass
import retrofit2.http.GET

interface AppVersionApi {

    @GET("api/app-version")
    suspend fun getVersion(): AppVersionResponse
}

@JsonClass(generateAdapter = true)
data class AppVersionResponse(
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkUrl: String? = null,
    val forceUpdate: Boolean = false,
    val releaseNotes: String? = null,
)
