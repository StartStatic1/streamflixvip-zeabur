package com.streamflixvip.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixvip.app.data.CatalogRepository
import com.streamflixvip.app.data.VipStatusHolder
import com.streamflixvip.app.network.TmdbEpisode
import com.streamflixvip.app.network.TmdbItem
import com.streamflixvip.app.network.TmdbResponse
import com.streamflixvip.app.network.VipSource
import com.streamflixvip.app.network.VipTitleConfig
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
        // Config de bloqueio VIP do TÍTULO inteiro, vinda da tabela
        // dedicada vip_titles — serve pra filme e série igual, sem
        // depender de nenhuma fonte/servidor estar marcada de um jeito
        // específico. Null = título nunca foi configurado = sem bloqueio.
        val vipConfig: VipTitleConfig? = null,
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
        // Fileira "Você também pode gostar" — preenchida depois dos dados
        // principais, sem bloquear a tela por ela (ver loadSimilarTitles).
        // Vazia por padrão: a seção só aparece quando há algo pra mostrar.
        val similarTitles: List<TmdbItem> = emptyList(),
    ) : DetailUiState {
        /** Decide se o FILME deve mostrar cadeado em vez da lista de fontes. */
        fun movieIsLocked(isVip: Boolean): Boolean = !isVip && requiresVip(vipConfig, episodeNumber = null)

        /** Decide se um episódio específico deve mostrar cadeado, sem precisar carregar suas fontes primeiro. */
        fun episodeIsLocked(episodeNumber: Int, isVip: Boolean): Boolean = !isVip && requiresVip(vipConfig, episodeNumber)
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
                // Config de bloqueio VIP do título — vem de uma consulta só,
                // direto da tabela dedicada vip_titles. Serve tanto pra
                // filme quanto série, e não depende de nenhuma fonte estar
                // cadastrada de um jeito específico.
                val vipConfig = repository.getVipTitleConfig(tmdbId, mediaType)

                _uiState.value = DetailUiState.Success(
                    details = details,
                    mediaType = mediaType,
                    tmdbId = tmdbId,
                    movieSources = movieSources,
                    vipConfig = vipConfig,
                )

                if (mediaType == "tv") {
                    // Diferente de antes: NÃO abre a temporada 1 sozinha ao
                    // entrar na tela. Lista de temporadas fica recolhida por
                    // padrão (só os títulos "Temporada 1", "Temporada 2"...
                    // visíveis), igual à referência do CineVerse — exige 1
                    // toque pra expandir e ver os episódios. Exceção: vindo
                    // de "Continuar assistindo", aí sim abre direto na
                    // temporada/episódio que a pessoa já estava vendo,
                    // porque nesse caso a intenção já é clara.
                    if (initialSeason > 0) {
                        expandSeason(initialSeason)
                        // Aqui é só preparar a tela ao abrir vindo de "Continuar
                        // assistindo" — a pessoa não tocou em nada ainda, então
                        // não deve abrir sheet nem tocar nada sozinho. Por isso
                        // busca as fontes direto pelo repository, sem passar
                        // pela decisão de auto-play/sheet do loadEpisodeSources.
                        preloadEpisodeSourcesSilently(initialSeason, initialEpisode.coerceAtLeast(1))
                    }
                }

                // Similares carregam DEPOIS de já mostrar a tela principal —
                // não faz sentido segurar sinopse/pôster/fontes esperando
                // uma fileira secundária no rodapé. Falha ou demora aqui
                // nunca deve impedir o resto da tela de aparecer.
                launch {
                    val similar = repository.getSimilarTitles(tmdbId, mediaType)
                    val stillCurrent = _uiState.value as? DetailUiState.Success ?: return@launch
                    _uiState.value = stillCurrent.copy(similarTitles = similar)
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
