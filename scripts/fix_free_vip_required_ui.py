#!/usr/bin/env python3
"""Quando API devolve VIP_REQUIRED, free ve cadeado (nao 'fora da grade')."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
vm = ROOT / "android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailViewModel.kt"
t = vm.read_text(encoding="utf-8")

if "VIP_REQUIRED" in t and "res.vipConfig" in t:
    print("DetailViewModel already handles VIP_REQUIRED")
else:
    # ensure imports
    if "NetworkModule" not in t:
        t = t.replace(
            "import com.streamflixvip.app.network.VipTitleConfig\n",
            "import com.streamflixvip.app.network.VipTitleConfig\n"
            "import com.streamflixvip.app.network.NetworkModule\n",
            1,
        )
        print("import NetworkModule")

    old = '''                if (mediaType == "movie") {
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
                }'''

    new = '''                if (mediaType == "movie") {
                    launch {
                        var sources = emptyList<com.streamflixvip.app.network.VipSource>()
                        var fromApiConfig: VipTitleConfig? = null
                        var requiredByApi = false
                        try {
                            val res = NetworkModule.mediaSourcesApi.getMovieSources(tmdbId)
                            if (res.code == "VIP_REQUIRED" || res.code == "AUTH_REQUIRED" || res.requiresVip) {
                                requiredByApi = true
                                fromApiConfig = res.vipConfig
                                sources = emptyList()
                            } else if (res.sources.isNotEmpty()) {
                                sources = res.sources
                                fromApiConfig = res.vipConfig
                            } else {
                                // API vazia: tenta repo (respeita VIP no fallback)
                                sources = try {
                                    repository.getSourcesForMovie(tmdbId)
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            }
                        } catch (_: Exception) {
                            sources = try {
                                repository.getSourcesForMovie(tmdbId)
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                        val still = _uiState.value as? DetailUiState.Success ?: return@launch
                        val mergedConfig = when {
                            fromApiConfig != null -> fromApiConfig
                            requiredByApi && still.vipConfig == null ->
                                VipTitleConfig(vip_lock = true, vip_free_episode_limit = null)
                            requiredByApi && still.vipConfig != null && still.vipConfig.vip_lock != true ->
                                still.vipConfig.copy(vip_lock = true)
                            else -> still.vipConfig
                        }
                        _uiState.value = still.copy(
                            movieSources = sources,
                            isLoadingMovieSources = false,
                            vipConfig = mergedConfig,
                        )
                    }
                }'''

    if old not in t:
        print("WARN movie sources block not found")
        sys.exit(1)
    t = t.replace(old, new, 1)
    print("ok movie VIP_REQUIRED handling")

vm.write_text(t, encoding="utf-8")

# DetailScreen: se free + fontes vazias + requiresVip → cadeado (reforço)
ds = ROOT / "android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt"
dt = ds.read_text(encoding="utf-8")
old_ui = '''            if (state.movieIsLocked(isVip)) {
                item {
                    Column(Modifier.padding(16.dp)) {
                        VipLockCard(onUpgradeClick = onUpgradeClick, onTicketClick = onTicketClick)
                    }
                }
            } else if (state.isLoadingMovieSources) {
                // Loading cinema fica no hero (no lugar do Assistir)
            } else if (state.movieSources.isEmpty()) {
                item {
                    MovieRequestCard()
                }
            }'''
new_ui = '''            if (state.movieIsLocked(isVip) || (!isVip && state.movieSources.isEmpty() && state.vipConfig?.vip_lock == true)) {
                item {
                    Column(Modifier.padding(16.dp)) {
                        VipLockCard(onUpgradeClick = onUpgradeClick, onTicketClick = onTicketClick)
                    }
                }
            } else if (state.isLoadingMovieSources) {
                // Loading cinema fica no hero (no lugar do Assistir)
            } else if (state.movieSources.isEmpty()) {
                item {
                    MovieRequestCard()
                }
            }'''
if old_ui in dt:
    dt = dt.replace(old_ui, new_ui, 1)
    ds.write_text(dt, encoding="utf-8")
    print("ok DetailScreen lock fallback")
elif "vipConfig?.vip_lock == true" in dt:
    print("DetailScreen already")
else:
    print("WARN DetailScreen block")

print("done")
