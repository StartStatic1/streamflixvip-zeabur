#!/usr/bin/env python3
"""No primeiro IO error, troca sozinho para a fonte do menu Fontes."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
p = root / "android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt"
t = p.read_text()

old = """    LaunchedEffect(errorMessage) {
        if (errorMessage == null || isRecovering) return@LaunchedEffect
        if (retryAttempt < 2) {
            retryAttempt += 1
            isRecovering = true
            delay(1200L * retryAttempt)
            val candidates = VipSource(source_url = url, source_label = null, priority = null)
                .candidatePlaybackUrls(BuildConfig.API_BASE_URL, NetworkModule.ZEABUR_BASE_URL)
            reloadWithUrl(StreamUrlResolver.resolveFastest(candidates).ifBlank { activeUrl })
            delay(800)
            if (exoPlayer.playerError == null) errorMessage = null
            isRecovering = false
        }
    }"""

new = """    LaunchedEffect(errorMessage) {
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

if "alternateSources.firstOrNull { it != activeUrl" in t:
    print("ok auto fonte ja")
elif old in t:
    t = t.replace(old, new, 1)
    p.write_text(t)
    print("ok auto fonte")
else:
    print("aviso auto fonte")
print("fim apply_auto_fonte")
