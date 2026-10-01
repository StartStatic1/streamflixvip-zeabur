package com.streamflixvip.app.network

import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class FreeCatalogResponse(
    val items: List<TmdbItem> = emptyList(),
    val total: Int = 0,
    val error: String? = null,
    val needsMigration: Boolean? = null,
)

interface FreeCatalogApi {
    @GET("api/free-catalog")
    suspend fun getFreeCatalog(
        @Query("limit") limit: Int = 50,
    ): FreeCatalogResponse
}
