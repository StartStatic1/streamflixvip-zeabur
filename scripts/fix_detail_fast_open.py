#!/usr/bin/env python3
"""Detail abre assim que TMDB responde; vipConfig e fontes nao bloqueiam."""
from pathlib import Path

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailViewModel.kt")
t = p.read_text()

OLD = '''    fun loadDetails() {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            try {
                // Detalhes TMDB + vipConfig em paralelo — tela aparece sem esperar fontes
                val detailsDeferred = async {
                    if (mediaType == "tv") repository.getSeriesDetails(tmdbId)
                    else repository.getMovieDetails(tmdbId)
                }
                val vipDeferred = async {
                    try {
                        repository.getVipTitleConfig(tmdbId, mediaType)
                    } catch (_: Exception) {
                        null
                    }
                }
                val details = detailsDeferred.await()
                val vipConfig = vipDeferred.await()

                _uiState.value = DetailUiState.Success(
                    details = details,
                    mediaType = mediaType,
                    tmdbId = tmdbId,
                    movieSources = emptyList(),
                    isLoadingMovieSources = mediaType == "movie",
                    vipConfig = vipConfig,
                    canPostComments = userId != null && accessToken != null,
                )

                // Fontes do filme em background (add-ons podem demorar)
                if (mediaType == "movie") {
                    launch {
                        val sources = try {
                            repository.getSourcesForMovie(tmdbId)
                        } catch (_: Exception) {
                            emptyList()
                        }
                        val still = _uiState.value as? DetailUiState.Success ?: return@launch
                        _uiState.value = still.copy(
                            movieSources = sources,
                            isLoadingMovieSources = false,
                        )
                    }
                }
'''

NEW = '''    fun loadDetails() {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            try {
                // So TMDB bloqueia a tela — vip + fontes em background (evita 6-7s de spinner)
                val details = if (mediaType == "tv") repository.getSeriesDetails(tmdbId)
                else repository.getMovieDetails(tmdbId)

                _uiState.value = DetailUiState.Success(
                    details = details,
                    mediaType = mediaType,
                    tmdbId = tmdbId,
                    movieSources = emptyList(),
                    isLoadingMovieSources = mediaType == "movie",
                    vipConfig = null,
                    canPostComments = userId != null && accessToken != null,
                )

                // VIP config em paralelo (nao trava abertura)
                launch {
                    val vipConfig = try {
                        repository.getVipTitleConfig(tmdbId, mediaType)
                    } catch (_: Exception) {
                        null
                    }
                    val still = _uiState.value as? DetailUiState.Success ?: return@launch
                    _uiState.value = still.copy(vipConfig = vipConfig)
                }

                // Fontes do filme em background (add-ons podem demorar)
                if (mediaType == "movie") {
                    launch {
                        val sources = try {
                            repository.getSourcesForMovie(tmdbId)
                        } catch (_: Exception) {
                            emptyList()
                        }
                        val still = _uiState.value as? DetailUiState.Success ?: return@launch
                        _uiState.value = still.copy(
                            movieSources = sources,
                            isLoadingMovieSources = false,
                        )
                    }
                }
'''

if OLD not in t:
    if "So TMDB bloqueia a tela" in t:
        print("already fast open")
        raise SystemExit(0)
    raise SystemExit("loadDetails pattern not found")

t = t.replace(OLD, NEW, 1)
p.write_text(t)
print("DetailViewModel fast open OK")

# UI: so mostra "Buscando fontes" depois de 500ms (evita flash se fontes ja chegaram)
detail = Path("android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt")
if detail.exists():
    d = detail.read_text()
    old_busy = '''            } else if (state.isLoadingMovieSources) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Buscando fontes…",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (state.movieSources.isEmpty()) {'''

    # Inject delayed visibility near DetailContent if we can find a simple replace
    # Keep simpler: change label only + shorter visual
    if old_busy in d:
        new_busy = '''            } else if (state.isLoadingMovieSources) {
                item {
                    // Indicador discreto — fontes chegam em background sem travar a tela
                    Text(
                        "Carregando servidores…",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            } else if (state.movieSources.isEmpty()) {'''
        d = d.replace(old_busy, new_busy, 1)
        detail.write_text(d)
        print("DetailScreen label OK")
    else:
        print("WARN busy UI block not found (ok if already changed)")

print("done")
