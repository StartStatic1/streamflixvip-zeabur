#!/usr/bin/env python3
"""Mostra botao Assistir mesmo com fontes ainda carregando (filme)."""
from pathlib import Path

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt")
t = p.read_text()

OLD = '''    val heroWatchEnabled = state.mediaType == "movie" &&
        state.movieSources.isNotEmpty() &&
        !state.movieIsLocked(isVip)'''

NEW = '''    // Botao visivel cedo: com fontes prontas OU ainda carregando (nao trava 5-9s sem CTA)
    val heroWatchEnabled = state.mediaType == "movie" &&
        !state.movieIsLocked(isVip) &&
        (state.movieSources.isNotEmpty() || state.isLoadingMovieSources)'''

if OLD not in t:
    if "isLoadingMovieSources)" in t and "heroWatchEnabled" in t:
        print("hero already patched")
    else:
        raise SystemExit("heroWatchEnabled not found")
else:
    t = t.replace(OLD, NEW, 1)
    print("heroWatchEnabled OK")

# onWatchMovieNow: se ainda carregando e vazio, abre picker (ou no-op seguro)
OLD_WATCH = '''                onWatchMovieNow = {
                    when (s.movieSources.size) {
                        0 -> Unit
                        1 -> pendingWatch = PendingSource(s.movieSources.first(), 0, 0)
                        else -> showMovieServerPicker = true
                    }
                },'''

NEW_WATCH = '''                onWatchMovieNow = {
                    when {
                        s.movieSources.isEmpty() && s.isLoadingMovieSources -> {
                            // Ainda buscando — abre sheet; lista preenche quando chegar
                            showMovieServerPicker = true
                        }
                        s.movieSources.size == 1 -> pendingWatch = PendingSource(s.movieSources.first(), 0, 0)
                        s.movieSources.size > 1 -> showMovieServerPicker = true
                        else -> Unit
                    }
                },'''

if OLD_WATCH in t:
    t = t.replace(OLD_WATCH, NEW_WATCH, 1)
    print("onWatchMovieNow OK")
else:
    print("WARN onWatchMovieNow pattern")

p.write_text(t)
print("done")
