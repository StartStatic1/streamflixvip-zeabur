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
        // Controla o menu flutuante de escolha de temporada (estilo
        // CineVerse: um dropdown "Temporada N ▾" no lugar da lista de
        // "Temporada 1, 2, 3..." solta na tela) — não confundir com
        // expandedSeason, que é QUAL temporada está selecionada.
        val showSeasonPicker: Boolean = false,
        // Dentro da temporada selecionada, qual episódio está com o card
        // expandido mostrando thumbnail/sinopse. Diferente de
        // selectedEpisode (que é o episódio escolhido pra ASSISTIR) — dá
        // pra expandir um episódio só pra ler a sinopse sem tocar nele.
        val expandedEpisodeNumber: Int? = null,
        // Comentários: lista carregada da tabela title_comments no
        // Supabase, e se o modal fullscreen de comentários está aberto.
        val showComments: Boolean = false,
        val comments: List<com.streamflixvip.app.network.TitleComment> = emptyList(),
        val isLoadingComments: Boolean = false,
        val isPostingComment: Boolean = false,
        // Se a pessoa está logada — controla se o campo de "escrever
        // comentário" aparece dentro do modal ou se, em vez dele, aparece
        // um convite pra fazer login primeiro (ver CommentsModal).
        val canPostComments: Boolean = false,
        // Estado do coração (favoritos) — carregado ao abrir a tela (se
        // logado) e alterado de forma otimista ao tocar (ver
        // DetailViewModel.toggleFavorite). isTogglingFavorite trava
        // múltiplos toques rápidos disparando 2 chamadas simultâneas.
        val isFavorite: Boolean = false,
        val isTogglingFavorite: Boolean = false,
    ) : DetailUiState {
        /** Chave do YouTube pro trailer, se a TMDB tiver um cadastrado. */
        val trailerKey: String? get() = details.trailerKey

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
    // Sessão do usuário logado (null se ninguém estiver logado) — só
    // usados pra postar comentário, que exige autenticação (RLS via
    // auth.uid()). Ler comentários funciona sem login.
    private val userId: String? = null,
    private val accessToken: String? = null,
    private val userDisplayName: String? = null,
    private val repository: CatalogRepository = CatalogRepository(),
    private val commentsRepository: com.streamflixvip.app.data.CommentsRepository = com.streamflixvip.app.data.CommentsRepository(),
    private val favoritesRepository: com.streamflixvip.app.data.FavoritesRepository = com.streamflixvip.app.data.FavoritesRepository(),
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
                    canPostComments = userId != null && accessToken != null,
                )

                // Checa se já está favoritado — só faz sentido com sessão
                // ativa. Roda depois de já mostrar a tela principal, pra
                // não atrasar sinopse/pôster/fontes esperando essa consulta.
                if (userId != null && accessToken != null) {
                    launch {
                        val favorited = favoritesRepository.isFavorite(accessToken, userId, tmdbId, mediaType)
                        val stillCurrent = _uiState.value as? DetailUiState.Success ?: return@launch
                        _uiState.value = stillCurrent.copy(isFavorite = favorited)
                    }
                }

                if (mediaType == "tv") {
                    // Modelo novo: o dropdown de temporada (estilo CineVerse)
                    // sempre tem UMA temporada ativa/selecionada — não existe
                    // mais o estado "nada expandido" de antes. Por padrão
                    // abre a temporada 1; se a pessoa veio de "Continuar
                    // assistindo", abre direto na temporada/episódio que ela
                    // já estava vendo.
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

    /** Abre/fecha o menu flutuante de escolha de temporada (estilo CineVerse). */
    fun toggleSeasonPicker() {
        val current = _uiState.value as? DetailUiState.Success ?: return
        _uiState.value = current.copy(showSeasonPicker = !current.showSeasonPicker)
    }

    /**
     * Escolhe uma temporada a partir do menu flutuante — diferente de
     * expandSeason (que é usado pela versão antiga em lista/accordion),
     * aqui trocar de temporada sempre TROCA pra essa temporada (nunca
     * fecha tudo), porque no dropdown sempre existe uma temporada
     * "selecionada" — não existe estado de "nenhuma temporada aberta".
     */
    fun selectSeasonFromPicker(season: Int) {
        val current = _uiState.value as? DetailUiState.Success ?: return
        if (current.expandedSeason == season) {
            // já é a temporada ativa — só fecha o menu, não recarrega nada.
            _uiState.value = current.copy(showSeasonPicker = false)
            return
        }
        _uiState.value = current.copy(
            showSeasonPicker = false,
            expandedEpisodeNumber = null,
            expandedSeason = season,
            episodesOfExpandedSeason = emptyList(),
            isLoadingEpisodes = true,
        )
        viewModelScope.launch {
            val episodes = repository.getSeasonEpisodes(tmdbId, season)
            val stillCurrent = _uiState.value as? DetailUiState.Success ?: return@launch
            if (stillCurrent.expandedSeason == season) {
                _uiState.value = stillCurrent.copy(episodesOfExpandedSeason = episodes, isLoadingEpisodes = false)
            }
        }
    }

    /**
     * Expande/recolhe o card de UM episódio específico dentro da lista —
     * segunda camada de accordion (a primeira é a temporada). Mostra
     * thumbnail + sinopse só do episódio expandido, mantendo os outros
     * como linha compacta (nome + S1E2 + tags), igual à referência do
     * CineVerse. Reabrir o mesmo episódio fecha de novo.
     */
    fun toggleEpisodeExpanded(episodeNumber: Int) {
        val current = _uiState.value as? DetailUiState.Success ?: return
        _uiState.value = current.copy(
            expandedEpisodeNumber = if (current.expandedEpisodeNumber == episodeNumber) null else episodeNumber,
        )
    }

    /** Abre o modal fullscreen de comentários, carregando a lista se ainda não tiver sido buscada. */
    fun openComments() {
        val current = _uiState.value as? DetailUiState.Success ?: return
        _uiState.value = current.copy(showComments = true)
        if (current.comments.isNotEmpty() || current.isLoadingComments) return
        _uiState.value = (_uiState.value as DetailUiState.Success).copy(isLoadingComments = true)
        viewModelScope.launch {
            val comments = commentsRepository.getComments(tmdbId, mediaType)
            val stillCurrent = _uiState.value as? DetailUiState.Success ?: return@launch
            _uiState.value = stillCurrent.copy(comments = comments, isLoadingComments = false)
        }
    }

    fun closeComments() {
        val current = _uiState.value as? DetailUiState.Success ?: return
        _uiState.value = current.copy(showComments = false)
    }

    /**
     * Publica um comentário novo. Exige login — quem chama (a UI) já
     * deve ter checado userId/accessToken != null antes de mostrar o
     * campo de digitar; aqui é só a garantia de não tentar postar sem
     * sessão válida. onResult recebe true/false pra UI limpar o campo de
     * texto só em caso de sucesso.
     */
    fun postComment(text: String, isVip: Boolean, onResult: (Boolean) -> Unit) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || userId == null || accessToken == null) {
            onResult(false)
            return
        }
        val current = _uiState.value as? DetailUiState.Success ?: return
        _uiState.value = current.copy(isPostingComment = true)
        viewModelScope.launch {
            val success = commentsRepository.postComment(
                accessToken = accessToken,
                userId = userId,
                userDisplayName = userDisplayName,
                isVipAuthor = isVip,
                tmdbId = tmdbId,
                mediaType = mediaType,
                text = trimmed,
            )
            val stillCurrent = _uiState.value as? DetailUiState.Success ?: return@launch
            if (success) {
                // Recarrega a lista pra mostrar o comentário novo — mais
                // simples e confiável que montar o objeto localmente sem
                // saber o id/created_at que o Postgres gerou.
                val comments = commentsRepository.getComments(tmdbId, mediaType)
                _uiState.value = stillCurrent.copy(comments = comments, isPostingComment = false)
            } else {
                _uiState.value = stillCurrent.copy(isPostingComment = false)
            }
            onResult(success)
        }
    }

    /**
     * Adiciona ou remove este título dos Favoritos. Sem sessão (userId
     * ou accessToken ausentes), não faz nada — a tela deveria evitar
     * mostrar o coração clicável nesse caso, mas fica defensivo aqui
     * também.
     *
     * Otimista: o coração muda visualmente na hora, sem esperar a rede.
     * Se a chamada falhar de verdade, desfaz e volta ao estado anterior.
     */
    fun toggleFavorite() {
        val current = _uiState.value as? DetailUiState.Success ?: return
        val uid = userId ?: return
        val token = accessToken ?: return
        if (current.isTogglingFavorite) return // evita duplo toque disparando 2 chamadas

        val wasFavorite = current.isFavorite
        val title = current.details.title ?: current.details.name
        val posterPath = current.details.poster_path
        val originalLanguage = current.details.original_language

        _uiState.value = current.copy(isFavorite = !wasFavorite, isTogglingFavorite = true)

        viewModelScope.launch {
            val success = if (wasFavorite) {
                favoritesRepository.removeFavorite(token, uid, tmdbId, mediaType)
            } else {
                favoritesRepository.addFavorite(
                    accessToken = token,
                    userId = uid,
                    tmdbId = tmdbId,
                    mediaType = mediaType,
                    title = title,
                    posterPath = posterPath,
                    originalLanguage = originalLanguage,
                )
            }
            val stillCurrent = _uiState.value as? DetailUiState.Success ?: return@launch
            _uiState.value = if (success) {
                stillCurrent.copy(isTogglingFavorite = false)
            } else {
                // Falhou de verdade (não só ausência de rede momentânea) — desfaz a mudança otimista.
                stillCurrent.copy(isFavorite = wasFavorite, isTogglingFavorite = false)
            }
        }
    }
}
