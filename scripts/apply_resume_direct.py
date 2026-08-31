#!/usr/bin/env python3
from pathlib import Path

# --- HomeViewModel prefetch ---
vm = Path('android/app/src/main/java/com/streamflixvip/app/ui/home/HomeViewModel.kt')
vt = vm.read_text()
if 'prefetchContinueSources' not in vt:
    if 'import kotlinx.coroutines.launch' in vt and 'import kotlinx.coroutines.Dispatchers' not in vt:
        vt = vt.replace(
            'import kotlinx.coroutines.launch',
            'import kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.launch\nimport kotlinx.coroutines.withContext',
        )
    hook = '                )\n            } catch (e: Exception) {'
    # after Success assign, prefetch
    old = '''                _uiState.value = HomeUiState.Success(
                    continueWatching = continueWatching,'''
    new = '''                prefetchContinueSources(continueWatching)
                _uiState.value = HomeUiState.Success(
                    continueWatching = continueWatching,'''
    if old not in vt:
        raise SystemExit('home success assign nao encontrado')
    vt = vt.replace(old, new, 1)
    vt = vt.replace(
        '''    fun dismissContinueWatching(entry: WatchProgressEntry) {''',
        '''    private fun prefetchContinueSources(list: List<WatchProgressEntry>) {
        if (list.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            list.take(8).forEach { entry ->
                runCatching {
                    val season = if (entry.media_type == "tv") entry.season.coerceAtLeast(1) else 0
                    val episode = if (entry.media_type == "tv") entry.episode.coerceAtLeast(1) else 0
                    if (ResumePlaybackCache.get(entry.tmdb_id, entry.media_type, season, episode) != null) return@forEach
                    val sources = if (entry.media_type == "tv" && entry.season > 0) {
                        repository.getSourcesForEpisode(entry.tmdb_id, season, episode)
                    } else {
                        repository.getSourcesForMovie(entry.tmdb_id)
                    }
                    val src = sources.firstOrNull { it.source_url.isNotBlank() } ?: return@forEach
                    ResumePlaybackCache.put(
                        entry.tmdb_id,
                        entry.media_type,
                        season,
                        episode,
                        src.resolvedPlaybackUrl(com.streamflixvip.app.BuildConfig.API_BASE_URL),
                        src.isDirectPlayable,
                        src.source_label,
                    )
                }
            }
        }
    }

    fun dismissContinueWatching(entry: WatchProgressEntry) {''',
        1,
    )
    if 'import com.streamflixvip.app.data.ResumePlaybackCache' not in vt:
        vt = vt.replace(
            'import com.streamflixvip.app.data.ProgressRepository',
            'import com.streamflixvip.app.data.ProgressRepository\nimport com.streamflixvip.app.data.ResumePlaybackCache',
        )
    vm.write_text(vt)
    print('home vm ok')
else:
    print('home vm ja ok')

# --- PlayerScreen cache write ---
ps = Path('android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt')
pt = ps.read_text()
if 'ResumePlaybackCache.put' not in pt:
    if 'import com.streamflixvip.app.data.ResumePlaybackCache' not in pt:
        pt = pt.replace(
            'package com.streamflixvip.app.ui.player\n',
            'package com.streamflixvip.app.ui.player\n\nimport com.streamflixvip.app.data.ResumePlaybackCache\n',
        )
    needle = '    val view = LocalView.current'
    insert = '''    val view = LocalView.current
    androidx.compose.runtime.LaunchedEffect(sourceUrl, tmdbId, season, episode) {
        ResumePlaybackCache.init(view.context)
        if (sourceUrl.isNotBlank()) {
            ResumePlaybackCache.put(tmdbId, mediaType, season, episode, sourceUrl, isDirectPlayable)
        }
    }'''
    if needle not in pt:
        raise SystemExit('player LocalView nao encontrado')
    pt = pt.replace(needle, insert, 1)
    ps.write_text(pt)
    print('player ok')
else:
    print('player ja ok')

# --- MainActivity: cache first, never detail ---
ma = Path('android/app/src/main/java/com/streamflixvip/app/MainActivity.kt')
mt = ma.read_text()
if 'import com.streamflixvip.app.data.ResumePlaybackCache' not in mt:
    mt = mt.replace(
        'import com.streamflixvip.app.data.CatalogRepository',
        'import com.streamflixvip.app.data.CatalogRepository\nimport com.streamflixvip.app.data.ResumePlaybackCache',
    )
# init cache near catalogRepo
if 'ResumePlaybackCache.init' not in mt:
    mt = mt.replace(
        '    val catalogRepo = remember { CatalogRepository() }',
        '    val catalogRepo = remember { CatalogRepository() }\n    val resumeCtx = androidx.compose.ui.platform.LocalContext.current\n    androidx.compose.runtime.LaunchedEffect(Unit) { ResumePlaybackCache.init(resumeCtx) }',
    )

old_try = '''                            resumeScope.launch {
                                try {
                                    val sources = withContext(Dispatchers.IO) {
                                        if (entry.media_type == "tv" && entry.season > 0) {
                                            catalogRepo.getSourcesForEpisode(
                                                entry.tmdb_id,
                                                entry.season,
                                                entry.episode.coerceAtLeast(1),
                                            )
                                        } else {
                                            catalogRepo.getSourcesForMovie(entry.tmdb_id)
                                        }
                                    }
                                    val src = sources.firstOrNull { it.source_url.isNotBlank() }
                                    if (src == null) {
                                        navController.navigate(
                                            "detail/${entry.tmdb_id}/${entry.media_type}?season=${entry.season}&episode=${entry.episode}&resume=${entry.position_seconds}",
                                        )
                                    } else {
                                        val encodedUrl = java.net.URLEncoder.encode(
                                            src.resolvedPlaybackUrl(BuildConfig.API_BASE_URL),
                                            "UTF-8",
                                        )
                                        val encodedTitle = java.net.URLEncoder.encode(entry.displayTitle, "UTF-8")
                                        val encodedPoster = java.net.URLEncoder.encode(entry.poster_path ?: "none", "UTF-8")
                                        val season = if (entry.media_type == "tv") entry.season.coerceAtLeast(1) else 0
                                        val episode = if (entry.media_type == "tv") entry.episode.coerceAtLeast(1) else 0
                                        navController.navigate(
                                            "player/$encodedUrl/${src.isDirectPlayable}/${entry.tmdb_id}/${entry.media_type}/$season/$episode/$encodedTitle/$encodedPoster/${entry.position_seconds}",
                                        )
                                    }
                                } catch (_: Exception) {
                                    navController.navigate(
                                        "detail/${entry.tmdb_id}/${entry.media_type}?season=${entry.season}&episode=${entry.episode}&resume=${entry.position_seconds}",
                                    )
                                } finally {
                                    resumeBusy = false
                                }'''
new_try = '''                            resumeScope.launch {
                                try {
                                    val season = if (entry.media_type == "tv") entry.season.coerceAtLeast(1) else 0
                                    val episode = if (entry.media_type == "tv") entry.episode.coerceAtLeast(1) else 0
                                    val cached = ResumePlaybackCache.get(entry.tmdb_id, entry.media_type, season, episode)
                                    val playbackUrl: String
                                    val isDirect: Boolean
                                    if (cached != null) {
                                        playbackUrl = cached.url
                                        isDirect = cached.isDirect
                                    } else {
                                        val sources = withContext(Dispatchers.IO) {
                                            if (entry.media_type == "tv" && entry.season > 0) {
                                                catalogRepo.getSourcesForEpisode(
                                                    entry.tmdb_id,
                                                    entry.season,
                                                    entry.episode.coerceAtLeast(1),
                                                )
                                            } else {
                                                catalogRepo.getSourcesForMovie(entry.tmdb_id)
                                            }
                                        }
                                        val src = sources.firstOrNull { it.source_url.isNotBlank() }
                                            ?: throw IllegalStateException("no-source")
                                        playbackUrl = src.resolvedPlaybackUrl(BuildConfig.API_BASE_URL)
                                        isDirect = src.isDirectPlayable
                                        ResumePlaybackCache.put(
                                            entry.tmdb_id, entry.media_type, season, episode,
                                            playbackUrl, isDirect, src.source_label,
                                        )
                                    }
                                    val encodedUrl = java.net.URLEncoder.encode(playbackUrl, "UTF-8")
                                    val encodedTitle = java.net.URLEncoder.encode(entry.displayTitle, "UTF-8")
                                    val encodedPoster = java.net.URLEncoder.encode(entry.poster_path ?: "none", "UTF-8")
                                    navController.navigate(
                                        "player/$encodedUrl/$isDirect/${entry.tmdb_id}/${entry.media_type}/$season/$episode/$encodedTitle/$encodedPoster/${entry.position_seconds}",
                                    )
                                } catch (_: Exception) {
                                    // Sem ficha: tenta de novo a API; se falhar, some o overlay.
                                } finally {
                                    resumeBusy = false
                                }'''
if old_try not in mt:
    if 'ResumePlaybackCache.get(entry.tmdb_id' in mt:
        print('main ja ok')
    else:
        raise SystemExit('bloco continue click nao encontrado')
else:
    mt = mt.replace(old_try, new_try, 1)
    print('main click ok')

# overlay copy
mt = mt.replace(
    '                    Text("Retomando de onde parou", color = Color.White)',
    '                    Text("Retomando de onde parou", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)',
)
ma.write_text(mt)
print('main written')

# version bump so in-app update aparece
g = Path('android/app/build.gradle.kts')
gt = g.read_text()
if 'versionName = "11.8.0"' in gt:
    gt = gt.replace('versionCode = 110800', 'versionCode = 110801')
    gt = gt.replace('versionName = "11.8.0"', 'versionName = "11.8.1"')
    g.write_text(gt)
    print('version 11.8.1')
else:
    print('version skip', [l for l in gt.splitlines() if 'versionName' in l][:3])
