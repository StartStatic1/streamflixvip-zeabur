#!/usr/bin/env python3
"""Sobe loadAlternateSources antes do LaunchedEffect (erro de compile)."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
p = root / "android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt"
t = p.read_text()

broken = """    LaunchedEffect(errorMessage) {
        if (errorMessage == null || isRecovering) return@LaunchedEffect
        isRecovering = true
        if (alternateSources.isEmpty()) {
            try { loadAlternateSources() } catch (_: Exception) {}
        }
        val next = alternateSources.firstOrNull { it != activeUrl && it.isNotBlank() }
        if (next != null) {
            retryAttempt = 0
            errorMessage = null
            reloadWithUrl(next)
            isRecovering = false
            return@LaunchedEffect
        }
        if (retryAttempt < 2) {
            retryAttempt += 1
            delay(800L * retryAttempt)
            reloadWithUrl(activeUrl)
            delay(600)
            if (exoPlayer.playerError == null) errorMessage = null
        }
        isRecovering = false
    }

    suspend fun loadAlternateSources() {
        try {
            val resp = if (mediaType == \"tv\" && currentSeason > 0) {
                NetworkModule.mediaSourcesApi.getEpisodeSources(tmdbId, \"tv\", currentSeason, currentEpisode)
            } else {
                NetworkModule.mediaSourcesApi.getMovieSources(tmdbId, mediaType)
            }
            alternateSources = resp.sources.filter { it.isDirectPlayable }
                .flatMap { it.candidatePlaybackUrls(BuildConfig.API_BASE_URL, NetworkModule.ZEABUR_BASE_URL) }
                .distinct().filter { it.isNotBlank() && it != activeUrl }
            alternateIndex = 0
        } catch (_: Exception) {
            alternateSources = emptyList()
        }
    }"""

fixed = """    suspend fun loadAlternateSources() {
        try {
            val resp = if (mediaType == \"tv\" && currentSeason > 0) {
                NetworkModule.mediaSourcesApi.getEpisodeSources(tmdbId, \"tv\", currentSeason, currentEpisode)
            } else {
                NetworkModule.mediaSourcesApi.getMovieSources(tmdbId, mediaType)
            }
            alternateSources = resp.sources.filter { it.isDirectPlayable }
                .flatMap { it.candidatePlaybackUrls(BuildConfig.API_BASE_URL, NetworkModule.ZEABUR_BASE_URL) }
                .distinct().filter { it.isNotBlank() && it != activeUrl }
            alternateIndex = 0
        } catch (_: Exception) {
            alternateSources = emptyList()
        }
    }

    LaunchedEffect(errorMessage) {
        if (errorMessage == null || isRecovering) return@LaunchedEffect
        isRecovering = true
        if (alternateSources.isEmpty()) {
            try { loadAlternateSources() } catch (_: Exception) {}
        }
        val next = alternateSources.firstOrNull { it != activeUrl && it.isNotBlank() }
        if (next != null) {
            retryAttempt = 0
            errorMessage = null
            reloadWithUrl(next)
            isRecovering = false
            return@LaunchedEffect
        }
        if (retryAttempt < 2) {
            retryAttempt += 1
            delay(800L * retryAttempt)
            reloadWithUrl(activeUrl)
            delay(600)
            if (exoPlayer.playerError == null) errorMessage = null
        }
        isRecovering = false
    }"""

if t.count("suspend fun loadAlternateSources()") == 1 and t.find("suspend fun loadAlternateSources()") < t.find("LaunchedEffect(errorMessage)"):
    print("ok ordem ja")
elif broken in t:
    t = t.replace(broken, fixed, 1)
    p.write_text(t)
    print("ok compile fonte")
else:
    print("aviso compile fonte")
print("fim apply_auto_fonte_fix")
