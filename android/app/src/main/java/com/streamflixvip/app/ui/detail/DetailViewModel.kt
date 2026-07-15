package com.streamflixvip.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixvip.app.data.CatalogRepository
import com.streamflixvip.app.data.VipStatusHolder
import com.streamflixvip.app.network.TmdbEpisode
import com.streamflixvip.app.network.TmdbResponse
import com.streamflixvip.app.network.VipSource
import com.streamflixvip.app.network.requiresVip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Error(val message: String) : DetailUiState
    data class Success(
        val details: TmdbResponse,
        val mediaType: String,
        val tmdbId: Int,
        // Só preenchido pra filme (série exige escolher season/episode
        // primeiro, então as fontes daquele episódio são buscadas sob
        // demanda quando o usuário seleciona um episódio na lista).
        val movieSources: List<VipSource> = emptyList(),
        // Verdade se o FILME exige VIP e o usuário atual não é VIP —
        // calculado a partir de movieSources + VipStatusHolder assim que
        // as fontes chegam.
        val movieIsLocked: Boolean = false,
        // Temporada atualmente "aberta" na UI (mostrando a lista de
        // episódios com thumbnail/sinopse). Diferente de selectedSeason,
        // que é qual episódio está escolhido pra assistir agora.
        val expandedSeason: Int? = null,
        val episodesOfExpandedSeason: List<TmdbEpisode> = emptyList(),
        val isLoadingEpisodes: Boolean = false,
        val selectedSeason: Int? = null,
        val selectedEpisode: Int? = null,
        val episodeSources: List<VipSource> = emptyList(),
        val isLoadingEpisodeSources: Boolean = false,
        // Quando um episódio tem 2+ servidores, a UI deve abrir um
        // seletor (bottom sheet) em vez de assistir direto — esse campo
        // guarda "pra qual episódio" o seletor deve abrir. Null = fechado.
        val showServerPickerForEpisode: Int? = null,
        // Do painel: até qual episódio é liberado de graça nessa série
        // (null = sem limite configurado). Usado pra decidir cadeado nos
        // cards da lista de episódios ANTES do usuário escolher um.
        val freeEpisodeLimit: Int? = null,
        // Verdade se a série INTEIRA está marcada como vip_lock=true no
        // painel (diferente de freeEpisodeLimit, que libera parcialmente).
        val seriesVipLocked: Boolean = false,
    ) : DetailUiState {
        /** Decide se um episódio específico deve mostrar cadeado, sem precisar carregar suas fontes primeiro. */
        fun episodeIsLocked(episodeNumber: Int, isVip: Boolean): Boolean {
            if (isVip) return false
            if (seriesVipLocked) return true
            val limit = freeEpisodeLimit ?: return false
            return episodeNumber > limit
        }
    }
}

class DetailViewModel(
    private val tmdbId: Int,
    private val mediaType: String,
    // Preenchidos quando a tela abre a partir de "Continuar assistindo" —
    // já sabemos qual episódio a pessoa estava vendo, então adiantamos a
    // busca das fontes daquele episódio em vez de forçar escolher de novo.
    private val initialSeason: Int = -1,
    private val initialEpisode: Int = -1,
    private val repository: CatalogRepository = CatalogRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState

    init {
        loadDetails()
    }

    fun loadDetails() {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            try {
                val details = if (mediaType == "tv") {
                    repository.getSeriesDetails(tmdbId)
                } else {
                    repository.getMovieDetails(tmdbId)
                }

                val movieSources = if (mediaType == "movie") {
                    repository.getSourcesForMovie(tmdbId)
                } else {
                    emptyList()
                }
                // Pra série, busca a config VIP do título ANTES de qualquer
                // episódio ser selecionado — é o que permite mostrar o
                // cadeado direto nos cards da lista, sem esperar o usuário
                // clicar em cada um pra descobrir se está bloqueado.
                val seriesVipConfig = if (mediaType == "tv") repository.getSeriesVipConfig(tmdbId) else null

                val isVipNow = VipStatusHolder.isVip.value
                val movieIsLocked = mediaType == "movie" && !isVipNow && requiresVip(movieSources, episodeNumber = null)

                _uiState.value = DetailUiState.Success(
                    details = details,
                    mediaType = mediaType,
                    tmdbId = tmdbId,
                    movieSources = movieSources,
                    movieIsLocked = movieIsLocked,
                    freeEpisodeLimit = movieSources.firstOrNull()?.vip_free_episode_limit ?: seriesVipConfig?.vip_free_episode_limit,
                    seriesVipLocked = seriesVipConfig?.vip_lock == true,
                )

                if (mediaType == "tv") {
                    // Abre direto a temporada que a pessoa estava vendo (vindo de
                    // "Continuar assistindo"), ou a primeira temporada da série —
                    // assim a lista de episódios já aparece pronta ao entrar na
                    // tela, sem exigir um toque extra pra "abrir a temporada 1".
                    val seasons = details.seasons.orEmpty().filter { it.season_number > 0 }
                    val seasonToOpen = if (initialSeason > 0) initialSeason else seasons.firstOrNull()?.season_number
                    if (seasonToOpen != null) {
                        expandSeason(seasonToOpen)
                    }
                    if (initialSeason > 0) {
                        // Aqui é só preparar a tela ao abrir vindo de "Continuar
                        // assistindo" — a pessoa não tocou em nada ainda, então
                        // não deve abrir sheet nem tocar nada sozinho. Por isso
                        // busca as fontes direto pelo repository, sem passar
                        // pela decisão de auto-play/sheet do loadEpisodeSources.
                        preloadEpisodeSourcesSilently(initialSeason, initialEpisode.coerceAtLeast(1))
                    }
                }
            } catch (e: Exception) {
                _uiState.value = DetailUiState.Error(e.message ?: "Erro ao carregar detalhes")
            }
        }
    }

    /**
     * Abre uma temporada na UI, buscando a lista real de episódios
     * (título, sinopse, imagem, duração) via TMDB. Repetir o toque na
     * mesma temporada já aberta a fecha de novo (comportamento de
     * accordion, como o site já faz).
     */
    fun expandSeason(season: Int) {
        val current = _uiState.value as? DetailUiState.Success ?: return
        if (current.expandedSeason == season) {
            _uiState.value = current.copy(expandedSeason = null, episodesOfExpandedSeason = emptyList())
            return
        }
        _uiState.value = current.copy(expandedSeason = season, episodesOfExpandedSeason = emptyList(), isLoadingEpisodes = true)
        viewModelScope.launch {
            val episodes = repository.getSeasonEpisodes(tmdbId, season)
            val stillCurrent = _uiState.value as? DetailUiState.Success ?: return@launch
            if (stillCurrent.expandedSeason == season) {
                _uiState.value = stillCurrent.copy(episodesOfExpandedSeason = episodes, isLoadingEpisodes = false)
            }
        }
    }

    /**
     * Carrega as fontes de um episódio sem decidir nada sozinho (sem
     * auto-play, sem abrir sheet) — usado só ao abrir a tela vindo de
     * "Continuar assistindo", pra deixar o card daquele episódio já
     * marcado como selecionado, caso a pessoa queira ver "onde estava".
     */
    private fun preloadEpisodeSourcesSilently(season: Int, episode: Int) {
        val current = _uiState.value as? DetailUiState.Success ?: return
        val isVipNow = VipStatusHolder.isVip.value
        if (current.episodeIsLocked(episode, isVipNow)) {
            _uiState.value = current.copy(selectedSeason = season, selectedEpisode = episode, episodeSources = emptyList())
            return
        }
        viewModelScope.launch {
            val sources = try {
                repository.getSourcesForEpisode(tmdbId, season, episode)
            } catch (_: Exception) {
                emptyList()
            }
            val stillCurrent = _uiState.value as? DetailUiState.Success ?: return@launch
            _uiState.value = stillCurrent.copy(
                selectedSeason = season,
                selectedEpisode = episode,
                episodeSources = sources,
            )
        }
    }

    /**
     * Chamado quando o usuário toca num episódio numa série — busca as
     * fontes daquele episódio e decide o que fazer sozinho, sem exigir um
     * segundo toque em "Onde assistir" mais abaixo na tela:
     *  - 0 fontes: não faz nada além de guardar o estado (a seção mostra
     *    "nenhuma fonte disponível" pro usuário, sem sheet).
     *  - 1 fonte: essa é a única opção possível, então já dispara a
     *    reprodução direto — não faz sentido perguntar "qual servidor"
     *    quando só existe um.
     *  - 2+ fontes: aí sim vale abrir o seletor (bottom sheet), porque a
     *    escolha é real e a pessoa pode preferir uma fonte específica.
     *
     * onAutoPlay é chamado só no caso de fonte única, com o VipSource já
     * pronto pra tocar — quem decide navegar pro player é a tela (que tem
     * acesso ao NavController), o ViewModel só decide "deveria tocar".
     */
    fun loadEpisodeSources(season: Int, episode: Int, onAutoPlay: (VipSource) -> Unit) {
        val current = _uiState.value as? DetailUiState.Success ?: return
        val isVipNow = VipStatusHolder.isVip.value

        // Episódio bloqueado: nem busca fonte, nem toca nada — só marca
        // como selecionado pra o card acender e a seção "Onde assistir"
        // mostrar o cadeado + CTA de upgrade em vez da lista de servidores.
        if (current.episodeIsLocked(episode, isVipNow)) {
            _uiState.value = current.copy(
                selectedSeason = season,
                selectedEpisode = episode,
                episodeSources = emptyList(),
                isLoadingEpisodeSources = false,
                showServerPickerForEpisode = null,
            )
            return
        }

        _uiState.value = current.copy(
            selectedSeason = season,
            selectedEpisode = episode,
            isLoadingEpisodeSources = true,
        )
        viewModelScope.launch {
            val sources = try {
                repository.getSourcesForEpisode(tmdbId, season, episode)
            } catch (_: Exception) {
                emptyList()
            }
            val stillCurrent = _uiState.value as? DetailUiState.Success ?: return@launch
            // Evita aplicar um resultado atrasado se o usuário já trocou
            // de episódio de novo enquanto essa busca ainda rodava.
            if (stillCurrent.selectedSeason != season || stillCurrent.selectedEpisode != episode) return@launch

            when {
                sources.size == 1 -> {
                    _uiState.value = stillCurrent.copy(
                        episodeSources = sources,
                        isLoadingEpisodeSources = false,
                        showServerPickerForEpisode = null,
                    )
                    onAutoPlay(sources.first())
                }
                sources.size > 1 -> {
                    _uiState.value = stillCurrent.copy(
                        episodeSources = sources,
                        isLoadingEpisodeSources = false,
                        showServerPickerForEpisode = episode,
                    )
                }
                else -> {
                    _uiState.value = stillCurrent.copy(
                        episodeSources = sources,
                        isLoadingEpisodeSources = false,
                        showServerPickerForEpisode = null,
                    )
                }
            }
        }
    }

    /** Fecha o seletor de servidor sem trocar de episódio (ex: usuário tocou fora do sheet). */
    fun closeServerPicker() {
        val current = _uiState.value as? DetailUiState.Success ?: return
        _uiState.value = current.copy(showServerPickerForEpisode = null)
    }
}
