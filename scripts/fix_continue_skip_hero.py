#!/usr/bin/env python3
from pathlib import Path
p = Path('android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt')
t = p.read_text()

old_sig = '''    onToggleFavorite: () -> Unit,
) {'''
new_sig = '''    onToggleFavorite: () -> Unit,
    skipHeroLoading: Boolean = false,
) {'''
if 'skipHeroLoading: Boolean' not in t:
    if old_sig not in t:
        raise SystemExit('signature nao encontrada')
    t = t.replace(old_sig, new_sig, 1)
    print('sig ok')
else:
    print('sig ja ok')

old_call = '''                DetailContent(
                state = s,'''
new_call = '''                DetailContent(
                skipHeroLoading = resumeSeconds > 0,
                state = s,'''
if 'skipHeroLoading = resumeSeconds' not in t:
    if old_call not in t:
        raise SystemExit('call nao encontrado')
    t = t.replace(old_call, new_call, 1)
    print('call ok')
else:
    print('call ja ok')

old_flags = '''    val heroWatchEnabled = state.mediaType == "movie" &&
        !state.movieIsLocked(isVip) &&
        state.movieSources.isNotEmpty()
    val heroServersLoading = state.mediaType == "movie" &&
        !state.movieIsLocked(isVip) &&
        state.isLoadingMovieSources &&
        state.movieSources.isEmpty()'''
new_flags = '''    val heroWatchEnabled = !skipHeroLoading && state.mediaType == "movie" &&
        !state.movieIsLocked(isVip) &&
        state.movieSources.isNotEmpty()
    val heroServersLoading = !skipHeroLoading && state.mediaType == "movie" &&
        !state.movieIsLocked(isVip) &&
        state.isLoadingMovieSources &&
        state.movieSources.isEmpty()'''
if old_flags not in t:
    if '!skipHeroLoading && state.mediaType' in t:
        print('flags ja ok')
    else:
        raise SystemExit('flags nao encontrados')
else:
    t = t.replace(old_flags, new_flags, 1)
    print('flags ok')

p.write_text(t)
print('done', p.stat().st_size)
