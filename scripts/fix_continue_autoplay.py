#!/usr/bin/env python3
from pathlib import Path
p = Path('android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt')
t = p.read_text()
old = '''    var autoResumedContinue by rememberSaveable { mutableStateOf(false) }
    val successForResume = state as? DetailUiState.Success
    LaunchedEffect(successForResume, resumeSeconds) {
        if (autoResumedContinue || resumeSeconds <= 0) return@LaunchedEffect
        val s = successForResume ?: return@LaunchedEffect
        autoResumedContinue = true
        val title = s.details.title ?: s.details.name ?: "Sem titulo"
        val posterPath = s.details.poster_path
        if (s.movieSources.isNotEmpty() && initialSeason <= 0) {
            onPlaySource(s.movieSources.first(), 0, 0, title, posterPath)
            return@LaunchedEffect
        }
        if (initialSeason > 0) {
            val ep = initialEpisode.coerceAtLeast(1)
            viewModel.loadEpisodeSources(initialSeason, ep, forceAutoPlay = true) { src ->
                onPlaySource(src, initialSeason, ep, title, posterPath)
            }
        }
    }'''
new = '''    var autoResumedContinue by rememberSaveable { mutableStateOf(false) }
    val successForResume = state as? DetailUiState.Success
    LaunchedEffect(
        successForResume?.details?.id,
        resumeSeconds,
        successForResume?.movieSources?.size,
        successForResume?.isLoadingMovieSources,
        initialSeason,
        initialEpisode,
    ) {
        if (autoResumedContinue || resumeSeconds <= 0) return@LaunchedEffect
        val s = successForResume ?: return@LaunchedEffect
        val title = s.details.title ?: s.details.name ?: "Sem titulo"
        val posterPath = s.details.poster_path
        if (initialSeason > 0) {
            autoResumedContinue = true
            val ep = initialEpisode.coerceAtLeast(1)
            viewModel.loadEpisodeSources(initialSeason, ep, forceAutoPlay = true) { src ->
                onPlaySource(src, initialSeason, ep, title, posterPath)
            }
            return@LaunchedEffect
        }
        if (s.mediaType == "movie") {
            if (s.isLoadingMovieSources && s.movieSources.isEmpty()) return@LaunchedEffect
            val src = s.movieSources.firstOrNull() ?: return@LaunchedEffect
            autoResumedContinue = true
            onPlaySource(src, 0, 0, title, posterPath)
        }
    }'''
if old not in t:
    if 's.isLoadingMovieSources && s.movieSources.isEmpty()' in t:
        print('ja corrigido')
    else:
        raise SystemExit('bloco auto resume nao encontrado')
else:
    p.write_text(t.replace(old, new, 1))
    print('fix ok')
