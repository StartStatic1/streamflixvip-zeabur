package com.streamflixvip.tv.ui.detail

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class DetailTvViewModelFactory(
    private val application: Application,
    private val tmdbId: Int,
    private val mediaType: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        DetailTvViewModel(application, tmdbId, mediaType) as T
}
