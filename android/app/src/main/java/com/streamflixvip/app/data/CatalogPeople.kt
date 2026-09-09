package com.streamflixvip.app.data

import com.streamflixvip.app.network.NetworkModule
import com.streamflixvip.app.network.TmdbResponse

suspend fun CatalogRepository.getPerson(personId: Int): TmdbResponse =
    NetworkModule.tmdbApi.request(
        path = "/person/$personId",
        appendToResponse = "combined_credits",
    )
