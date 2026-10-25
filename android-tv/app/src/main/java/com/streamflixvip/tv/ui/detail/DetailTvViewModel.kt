package com.streamflixvip.tv.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixvip.tv.data.LocalFavorite
import com.streamflixvip.tv.data.LocalLibraryStore
import com.streamflixvip.tv.data.LocalWatchProgress
import com.streamflixvip.tv.data.RecentStore
import com.streamflixvip.tv.network.MediaSourcesResponse
import com.streamflixvip.tv.network.NetworkModule
import com.streamflixvip.tv.network.TmdbEpisode
import com.streamflixvip.tv.network.TmdbResponse
import com.streamflixvip.tv.network.VipSource
import com.streamflixvip.tv.network.fetchSourcesProgressively
import com.streamflixvip.tv.network.sourcesMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Onde o botao principal leva: filme, ou T/E da serie, com texto pronto. */
data class ResumeTarget(
    val season: Int,
    val episode: Int,
    val label: String,
    val hasProgress: Boolean,
)

/** Painel de servidores aberto para um filme/episodio. */
data class SourcePanel(
    val season: Int,
    val episode: Int,
    val title: String,
    val heading: String,
    val sources: List<VipSource> = emptyList(),
    val loading: Boolean = true,
    val message: String? = null,
    val fromStart: Boolean = false,
    val preferredLabel: String? = null,
)

data class DetailTvUiState(
    val isLoading: Boolean = true,
    val details: TmdbResponse? = null,
    val error: String? = null,
    val seasons: List<Int> = emptyList(),
    val selectedSeason: Int = 1,
    val seasonEpisodes: Map<Int, List<TmdbEpisode>> = emptyMap(),
    val loadingSeasons: Set<Int> = emptySet(),
    val failedSeasons: Set<Int> = emptySet(),
    val progress: List<LocalWatchProgress> = emptyList(),
    val watched: Set<Pair<Int, Int>> = emptySet(),
    val isFavorite: Boolean = false,
    val panel: SourcePanel? = null,
)

class DetailTvViewModel(
    application: Application,
    private val tmdbId: Int,
    private val mediaType: String,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DetailTvUiState())
    val uiState: StateFlow<DetailTvUiState> = _uiState.asStateFlow()

    private val library = LocalLibraryStore(application)
    private val recent = RecentStore(application)
    private var sourcesJob: Job? = null
    private var didInitialSeason = false

    val isSeries: Boolean get() = mediaType == "tv"

    fun loadDetails() {
        refreshLibrary()
        if (_uiState.value.details != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val path = if (isSeries) "/tv/$tmdbId" else "/movie/$tmdbId"
            val append = if (isSeries) {
                "videos,credits,images,recommendations,similar,content_ratings,external_ids"
            } else {
                "videos,credits,images,recommendations,similar,release_dates"
            }
            runCatching {
                NetworkModule.tmdbApi.requestMap(
                    path,
                    mapOf(
                        "append_to_response" to append,
                        "include_image_language" to "pt,en,null",
                        "include_video_language" to "pt,en,null",
                    ),
                )
            }.onSuccess { d ->
                val seasons = d.seasons.orEmpty()
                    .filter { it.season_number > 0 && it.episode_count > 0 }
                    .map { it.season_number }
                    .ifEmpty { (1..(d.number_of_seasons ?: 1).coerceAtLeast(1)).toList() }
                _uiState.update { it.copy(isLoading = false, details = d, seasons = if (isSeries) seasons else emptyList()) }
                if (isSeries) {
                    val target = resumeTarget()
                    val start = if (target.season in seasons) target.season else seasons.first()
                    didInitialSeason = true
                    selectSeason(start)
                }
            }.onFailure {
                _uiState.update { it.copy(isLoading = false, error = "Não foi possível carregar os detalhes. Verifique a internet.") }
            }
        }
    }

    fun retry() {
        _uiState.update { it.copy(details = null) }
        loadDetails()
    }

    fun refreshLibrary() {
        _uiState.update {
            it.copy(
                progress = library.getProgressListForTitle(tmdbId, mediaType),
                watched = library.watchedEpisodes(tmdbId, mediaType),
                isFavorite = library.isFavorite(tmdbId, mediaType),
            )
        }
    }

    fun selectSeason(season: Int) {
        _uiState.update { it.copy(selectedSeason = season, failedSeasons = it.failedSeasons - season) }
        val st = _uiState.value
        if (st.seasonEpisodes.containsKey(season) || st.loadingSeasons.contains(season)) return
        viewModelScope.launch {
            _uiState.update { it.copy(loadingSeasons = it.loadingSeasons + season) }
            runCatching {
                NetworkModule.tmdbApi.requestSeasonDetail(path = "/tv/$tmdbId/season/$season")
            }.onSuccess { sd ->
                _uiState.update {
                    it.copy(
                        seasonEpisodes = it.seasonEpisodes + (season to sd.episodes.orEmpty()),
                        loadingSeasons = it.loadingSeasons - season,
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(loadingSeasons = it.loadingSeasons - season, failedSeasons = it.failedSeasons + season) }
            }
        }
    }

    fun toggleFavorite(): Boolean {
        val d = _uiState.value.details ?: return false
        val added = library.toggleFavorite(
            LocalFavorite(tmdbId, mediaType, d.title ?: d.name ?: "Sem título", d.poster_path),
        )
        _uiState.update { it.copy(isFavorite = added) }
        return added
    }

    /** Proximo passo do botao principal (continuar / proximo episodio / comecar). */
    fun resumeTarget(): ResumeTarget {
        val st = _uiState.value
        if (!isSeries) {
            val p = library.getProgressFor(tmdbId, "movie", 0, 0)
            return if (p != null && p.positionSeconds > 30) {
                ResumeTarget(0, 0, if (p.remainingMinutes > 0) "Continuar · faltam ${p.remainingMinutes} min" else "Continuar", true)
            } else ResumeTarget(0, 0, "Assistir", false)
        }
        val seasons = st.seasons.ifEmpty { listOf(1) }
        val latest = library.getLatestProgressForTitle(tmdbId, mediaType)
        val lastWatched = st.watched.maxWithOrNull(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
        if (latest != null && (lastWatched == null || !isAfter(lastWatched, latest.season to latest.episode))) {
            return ResumeTarget(latest.season, latest.episode, "Continuar T${latest.season}:E${latest.episode}", true)
        }
        if (lastWatched != null) {
            val (s, e) = lastWatched
            val eps = st.seasonEpisodes[s]
            val seasonCount = st.details?.seasons?.firstOrNull { it.season_number == s }?.episode_count
            val hasNextInSeason = when {
                eps != null -> eps.any { it.episode_number == e + 1 }
                seasonCount != null && seasonCount > 0 -> e + 1 <= seasonCount
                else -> true
            }
            if (hasNextInSeason) return ResumeTarget(s, e + 1, "Assistir T$s:E${e + 1}", false)
            val nextSeason = seasons.firstOrNull { it > s }
            if (nextSeason != null) return ResumeTarget(nextSeason, 1, "Assistir T$nextSeason:E1", false)
        }
        val first = seasons.first()
        return ResumeTarget(first, 1, "Assistir T$first:E1", false)
    }

    private fun isAfter(a: Pair<Int, Int>, b: Pair<Int, Int>): Boolean =
        a.first > b.first || (a.first == b.first && a.second > b.second)

    fun progressFor(season: Int, episode: Int): LocalWatchProgress? =
        _uiState.value.progress.firstOrNull { it.season == season && it.episode == episode }

    /** Abre o painel e busca servidores aos poucos (fast=1, igual ao celular 16.3). */
    fun openSources(season: Int, episode: Int, fromStart: Boolean = false) {
        val d = _uiState.value.details ?: return
        val baseTitle = d.title ?: d.name ?: "Sem título"
        val epName = if (isSeries) {
            _uiState.value.seasonEpisodes[season]?.firstOrNull { it.episode_number == episode }?.name
        } else null
        val title = if (isSeries) "$baseTitle · T${season}E$episode" else baseTitle
        val heading = if (isSeries) {
            "T$season · E$episode" + (epName?.takeIf { it.isNotBlank() }?.let { " — $it" } ?: "")
        } else baseTitle
        sourcesJob?.cancel()
        val preferred = recent.lastServer(tmdbId)
        _uiState.update {
            it.copy(panel = SourcePanel(season, episode, title, heading, fromStart = fromStart, preferredLabel = preferred))
        }
        sourcesJob = viewModelScope.launch {
            var last: MediaSourcesResponse? = null
            val result = runCatching {
                fetchSourcesProgressively(
                    fetch = {
                        if (isSeries) {
                            NetworkModule.vipApi.getMediaSources(tmdbId, "tv", season, episode, fast = 1)
                        } else {
                            NetworkModule.vipApi.getMediaSources(tmdbId, "movie", fast = 1)
                        }
                    },
                    onPartial = { partial ->
                        last = partial
                        updatePanel(season, episode) { p -> p.copy(sources = order(partial.sources, preferred), loading = true) }
                    },
                )
            }
            // cancelado (painel fechado/reaberto): nao mexe no painel novo
            ensureActive()
            val final = result.getOrNull() ?: last
            val sources = order(final?.sources.orEmpty(), preferred)
            updatePanel(season, episode) { p ->
                p.copy(
                    sources = if (sources.isEmpty()) p.sources else sources,
                    loading = false,
                    message = if (sources.isEmpty() && p.sources.isEmpty()) sourcesMessage(final) else null,
                )
            }
        }
    }

    private fun updatePanel(season: Int, episode: Int, f: (SourcePanel) -> SourcePanel) {
        _uiState.update { st ->
            val p = st.panel
            if (p == null || p.season != season || p.episode != episode) st else st.copy(panel = f(p))
        }
    }

    private fun order(list: List<VipSource>, preferred: String?): List<VipSource> {
        val unique = list.distinctBy { it.source_url }
        if (preferred.isNullOrBlank()) return unique
        return unique.sortedByDescending { it.displayName == preferred }
    }

    fun closeSources() {
        sourcesJob?.cancel()
        _uiState.update { it.copy(panel = null) }
    }

    fun rememberServer(source: VipSource) {
        recent.setLastServer(tmdbId, source.displayName)
    }
}
