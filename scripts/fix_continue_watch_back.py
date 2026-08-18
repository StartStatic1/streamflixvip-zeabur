#!/usr/bin/env python3
from pathlib import Path

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailViewModel.kt")
t = p.read_text()
if "forceAutoPlay" not in t:
    t = t.replace(
        "fun loadEpisodeSources(season: Int, episode: Int, onAutoPlay: (VipSource) -> Unit) {",
        "fun loadEpisodeSources(season: Int, episode: Int, forceAutoPlay: Boolean = false, onAutoPlay: (VipSource) -> Unit) {",
        1,
    )
    old = """                sources.size > 1 -> {
                    _uiState.value = stillCurrent.copy(
                        episodeSources = sources,
                        isLoadingEpisodeSources = false,
                        showServerPickerForEpisode = episode,
                    )
                }"""
    new = """                sources.size > 1 -> {
                    if (forceAutoPlay) {
                        _uiState.value = stillCurrent.copy(
                            episodeSources = sources,
                            isLoadingEpisodeSources = false,
                            showServerPickerForEpisode = null,
                        )
                        onAutoPlay(sources.first())
                    } else {
                        _uiState.value = stillCurrent.copy(
                            episodeSources = sources,
                            isLoadingEpisodeSources = false,
                            showServerPickerForEpisode = episode,
                        )
                    }
                }"""
    if old not in t:
        raise SystemExit("multi-source block not found")
    t = t.replace(old, new, 1)
    p.write_text(t)
print("VM ok")

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt")
t = p.read_text()
old = "viewModel.loadEpisodeSources(initialSeason, ep) { src ->"
new = "viewModel.loadEpisodeSources(initialSeason, ep, forceAutoPlay = true) { src ->"
if old in t:
    t = t.replace(old, new, 1)
    p.write_text(t)
    print("DS ok")
elif "forceAutoPlay = true" in t:
    print("DS already")
else:
    raise SystemExit("DS call not found")

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt")
t = p.read_text()
if "import androidx.activity.compose.BackHandler" not in t:
    t = t.replace(
        "import androidx.compose.animation.AnimatedVisibility",
        "import androidx.activity.compose.BackHandler\nimport androidx.compose.animation.AnimatedVisibility",
        1,
    )
if "onBack: () -> Unit" not in t.split("fun PlayerScreen")[1][:600]:
    t = t.replace(
        "fun PlayerScreen(\n    sourceUrl: String,\n    isDirectPlayable: Boolean,",
        "fun PlayerScreen(\n    sourceUrl: String,\n    isDirectPlayable: Boolean,\n    onBack: () -> Unit = {},",
        1,
    )
if "BackHandler" not in t.split("fun PlayerScreen")[1][:2000]:
    anchor = "    var resolvedUrl by remember(sourceUrl)"
    if anchor not in t:
        raise SystemExit("resolvedUrl missing")
    t = t.replace(anchor, "    BackHandler { onBack() }\n\n    " + anchor, 1)
if "onBack: () -> Unit" not in t.split("fun NativePlayer")[1][:400]:
    t = t.replace(
        "private fun NativePlayer(\n    url: String,",
        "private fun NativePlayer(\n    url: String,\n    onBack: () -> Unit = {},",
        1,
    )
    t = t.replace(
        "NativePlayer(\n            url = currentResolvedUrl,\n            userId = userId,",
        "NativePlayer(\n            url = currentResolvedUrl,\n            onBack = onBack,\n            userId = userId,",
        1,
    )
if 'Text("< Voltar"' not in t and 'Text("< Voltar"' not in t:
    marker = """        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp),
        ) {
            val epLabel"""
    if marker in t:
        inject = """        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 12.dp, end = 16.dp, top = 8.dp),
        ) {
            Column {
            Text(
                "< Voltar",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .clickable { onBack() },
            )
            val epLabel"""
        t = t.replace(marker, inject, 1)
        old_c = """                if (epLabel != null) {
                    Text(epLabel, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
        }"""
        new_c = """                if (epLabel != null) {
                    Text(epLabel, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
            }
        }"""
        if old_c in t:
            t = t.replace(old_c, new_c, 1)
            print("back UI ok")
        else:
            print("WARN back UI close")
    else:
        print("WARN top marker")
p.write_text(t)
print("Player ok")

p = Path("android/app/src/main/java/com/streamflixvip/app/MainActivity.kt")
t = p.read_text()
idx = t.find("PlayerScreen(")
snippet = t[idx:idx+400] if idx >= 0 else ""
if "onBack = { navController.popBackStack() }" not in snippet:
    t = t.replace(
        "PlayerScreen(\n                        sourceUrl = url,\n                        isDirectPlayable = isDirect,",
        "PlayerScreen(\n                        sourceUrl = url,\n                        isDirectPlayable = isDirect,\n                        onBack = { navController.popBackStack() },",
        1,
    )
    p.write_text(t)
    print("MA ok")
else:
    print("MA already")
print("DONE")
