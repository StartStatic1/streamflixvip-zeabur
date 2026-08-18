#!/usr/bin/env python3
"""Apply player UX improvements to PlayerScreen.kt"""
from pathlib import Path

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt")
t = p.read_text()
assert "package com.streamflixvip.app.ui.player" in t, "bad file"
assert len(t) > 20000, f"file too small: {len(t)}"

if "playPreviousEpisode" in t and "nextCountdown" in t and "showController()" in t:
    print("already patched")
    raise SystemExit(0)

t = t.replace(
    'var controlsVisible by remember { mutableStateOf(false) }',
    'var controlsVisible by remember { mutableStateOf(true) }',
)
if "var nextCountdown" not in t:
    t = t.replace(
        'var showNextPrompt by remember { mutableStateOf(false) }',
        'var showNextPrompt by remember { mutableStateOf(false) }\n    var nextCountdown by remember { mutableStateOf(10) }',
    )

if "fun hideNativeSettingsButtonSafe" not in t:
    t = t.replace(
        "private fun isLikelyHls(url: String): Boolean {",
        """private fun hideNativeSettingsButtonSafe(view: PlayerView) {
    view.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_settings)?.visibility = android.view.View.GONE
}

private fun isLikelyHls(url: String): Boolean {""",
    )

old_pv = """                    controllerShowTimeoutMs = 3500
                    controllerHideOnTouch = true
                    post { hideController() }
                    subtitleView?.let { sub ->"""
new_pv = """                    controllerShowTimeoutMs = 2800
                    controllerHideOnTouch = true
                    post {
                        showController()
                        hideNativeSettingsButtonSafe(this)
                    }
                    subtitleView?.let { sub ->"""
if old_pv not in t:
    raise SystemExit("pv block not found")
t = t.replace(old_pv, new_pv)

old_post = """                    fun hideNativeSettingsButton() {
                        findViewById<android.view.View>(androidx.media3.ui.R.id.exo_settings)?.visibility = android.view.View.GONE
                    }
                    post {
                        hideNativeSettingsButton()
                        hideController()
                    }
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controlsVisible = visibility == android.view.View.VISIBLE
                            hideNativeSettingsButton()
                        },
                    )"""
new_post = """                    fun hideNativeSettingsButton() {
                        findViewById<android.view.View>(androidx.media3.ui.R.id.exo_settings)?.visibility = android.view.View.GONE
                    }
                    post { hideNativeSettingsButton() }
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controlsVisible = visibility == android.view.View.VISIBLE
                            hideNativeSettingsButton()
                        },
                    )"""
if old_post not in t:
    raise SystemExit("post block not found")
t = t.replace(old_post, new_post)

old_next = """            if (!played) errorMessage = "Sem proximo episodio disponivel"
        } finally {
            isLoadingNext = false
        }
    }

    fun selectSubtitle"""
new_next = """            if (!played) errorMessage = "Sem proximo episodio disponivel"
        } finally {
            isLoadingNext = false
        }
    }

    suspend fun playPreviousEpisode() {
        if (mediaType != "tv" || currentSeason <= 0 || currentEpisode <= 0) return
        if (currentSeason == 1 && currentEpisode == 1) {
            errorMessage = "Ja e o primeiro episodio"
            return
        }
        isLoadingNext = true
        showNextPrompt = false
        try {
            val tries = mutableListOf<Pair<Int, Int>>()
            if (currentEpisode > 1) tries += currentSeason to (currentEpisode - 1)
            if (currentSeason > 1) {
                for (e in 30 downTo 1) tries += (currentSeason - 1) to e
            }
            var played = false
            for ((s, e) in tries) {
                val resp = try {
                    NetworkModule.mediaSourcesApi.getEpisodeSources(tmdbId, "tv", s, e)
                } catch (_: Exception) { continue }
                val direct = resp.sources.filter { it.isDirectPlayable }
                if (direct.isEmpty()) continue
                val src = direct.first()
                val urls = src.candidatePlaybackUrls(BuildConfig.API_BASE_URL, NetworkModule.ZEABUR_BASE_URL)
                val playUrl = StreamUrlResolver.resolveFastest(urls).ifBlank { src.resolvedPlaybackUrl(BuildConfig.API_BASE_URL) }
                if (playUrl.isBlank()) continue
                currentSeason = s
                currentEpisode = e
                currentTitle = "$title — S${s}E${e}"
                reloadWithUrl(playUrl, resetPosition = true)
                played = true
                break
            }
            if (!played) errorMessage = "Sem episodio anterior disponivel"
        } finally {
            isLoadingNext = false
        }
    }

    fun selectSubtitle"""
if old_next not in t:
    raise SystemExit("next block not found")
t = t.replace(old_next, new_next)

marker = """            if (mediaType == "tv" && currentEpisode > 0) {
                val dur = exoPlayer.duration
                val pos = exoPlayer.currentPosition
                if (dur > 0 && (dur - pos) in 1..30_000L && !showNextPrompt && !isLoadingNext) {
                    showNextPrompt = true
                }
            }
        }
    }
"""
countdown = """            if (mediaType == "tv" && currentEpisode > 0) {
                val dur = exoPlayer.duration
                val pos = exoPlayer.currentPosition
                if (dur > 0 && (dur - pos) in 1..30_000L && !showNextPrompt && !isLoadingNext) {
                    showNextPrompt = true
                }
            }
        }
    }

    LaunchedEffect(showNextPrompt) {
        if (!showNextPrompt) return@LaunchedEffect
        nextCountdown = 10
        while (nextCountdown > 0 && showNextPrompt) {
            delay(1000L)
            if (!showNextPrompt) return@LaunchedEffect
            nextCountdown -= 1
        }
        if (showNextPrompt && nextCountdown <= 0 && !isLoadingNext) {
            playNextEpisode()
        }
    }
"""
if marker not in t:
    raise SystemExit("marker not found")
t = t.replace(marker, countdown)

old_btns = """            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (mediaType == "tv" && currentEpisode > 0) {
                    Surface(
                        color = Color.White.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                        modifier = Modifier.clickable { if (!isLoadingNext) MainScope().launch { playNextEpisode() } },
                    ) {
                        Text(
                            if (isLoadingNext) "..." else "Proximo",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
                Surface(
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.clickable { settingsPanel = SettingsPanel.MAIN },
                ) {
                    Text("Ajustes", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                }
            }"""
new_btns = """            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (mediaType == "tv" && currentEpisode > 0) {
                    val canPrev = !(currentSeason == 1 && currentEpisode == 1)
                    if (canPrev) {
                        Surface(
                            color = Color.White.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                            modifier = Modifier.clickable { if (!isLoadingNext) MainScope().launch { playPreviousEpisode() } },
                        ) {
                            Text(
                                "Anterior",
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                    }
                    Surface(
                        color = Color.White.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                        modifier = Modifier.clickable { if (!isLoadingNext) MainScope().launch { playNextEpisode() } },
                    ) {
                        Text(
                            if (isLoadingNext) "..." else "Proximo",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
                Surface(
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.clickable { settingsPanel = SettingsPanel.MAIN },
                ) {
                    Text("Ajustes", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                }
            }"""
if old_btns not in t:
    raise SystemExit("btns not found")
t = t.replace(old_btns, new_btns)

old_prompt = """                    Text("S${currentSeason} E${currentEpisode + 1} · Assistir agora?", color = Color.White, fontSize = 13.sp)
                    Surface(
                        color = Color.White.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                        modifier = Modifier.clickable { MainScope().launch { playNextEpisode() } },
                    ) {
                        Text("Sim", color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                    Surface(
                        color = Color.White.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { showNextPrompt = false },
                    ) {
                        Text("Nao", color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                    }"""
new_prompt = """                    Text(
                        "S${currentSeason} E${currentEpisode + 1} · em ${nextCountdown}s",
                        color = Color.White,
                        fontSize = 13.sp,
                    )
                    Surface(
                        color = Color.White.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                        modifier = Modifier.clickable { MainScope().launch { playNextEpisode() } },
                    ) {
                        Text("Agora", color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                    Surface(
                        color = Color.White.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { showNextPrompt = false },
                    ) {
                        Text("Cancelar", color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                    }"""
if old_prompt not in t:
    raise SystemExit("prompt not found")
t = t.replace(old_prompt, new_prompt)

p.write_text(t)
print("patched ok", len(t))
for s in ["playPreviousEpisode", "nextCountdown", "showController()", "Anterior"]:
    assert s in t, s
print("all features present")
