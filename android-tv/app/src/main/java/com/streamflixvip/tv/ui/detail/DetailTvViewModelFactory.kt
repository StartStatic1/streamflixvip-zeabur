package com.streamflixvip.tv.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class DetailTvViewModelFactory(
    private val tmdbId: Int,
    private val mediaType: String,
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return DetailTvViewModel(tmdbId, mediaType) as T
    }
}
