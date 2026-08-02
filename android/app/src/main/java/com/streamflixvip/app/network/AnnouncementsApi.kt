package com.streamflixvip.app.network

import com.squareup.moshi.JsonClass
import retrofit2.http.GET

/**
 * Avisos publicados via announcements.json (filme novo, manutenção, promo).
 * O switch de notificações no Perfil controla se a seção é exibida.
 */
interface AnnouncementsApi {
    @GET("api/announcements")
    suspend fun getAnnouncements(): AnnouncementsResponse
}

@JsonClass(generateAdapter = true)
data class AnnouncementsResponse(
    val announcements: List<AnnouncementItem> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class AnnouncementItem(
    val id: String,
    val type: String = "info",
    val title: String,
    val body: String,
    val createdAt: String? = null,
    val linkTmdbId: Int? = null,
    val linkMediaType: String? = null,
) {
    val typeLabel: String
        get() = when (type.lowercase()) {
            "movie", "filme" -> "Filme novo"
            "maintenance", "manutencao", "manutenção" -> "Manutenção"
            "promo", "promoção", "promocao" -> "Promoção"
            else -> "Aviso"
        }
}
