#!/usr/bin/env python3
from pathlib import Path

ma = Path("android/app/src/main/java/com/streamflixvip/app/MainActivity.kt")
t = ma.read_text()
if "ApkInstaller.download" not in t:
    t = t.replace(
        "import androidx.activity.compose.setContent",
        "import androidx.activity.compose.setContent\nimport androidx.lifecycle.lifecycleScope",
        1,
    )
    t = t.replace(
        "import androidx.compose.runtime",
        "import kotlinx.coroutines.launch\nimport androidx.compose.runtime",
        1,
    )
    start = t.find("            onDownloadClick = {")
    end = t.find("            },", start)
    end = t.find("\n", end)
    if start < 0 or end < 0:
        raise SystemExit("onDownloadClick bounds")
    new = """            onDownloadClick = {
                val url = updateInfo!!.apkUrl
                if (url.isBlank()) return@UpdateRequiredScreen
                isDownloadingUpdate = true
                (context as? androidx.activity.ComponentActivity)?.lifecycleScope?.launch {
                    try {
                        if (!com.streamflixvip.app.update.ApkInstaller.canInstallPackages(context)) {
                            com.streamflixvip.app.update.ApkInstaller.openInstallPermissionSettings(context)
                            isDownloadingUpdate = false
                            return@launch
                        }
                        val file = com.streamflixvip.app.update.ApkInstaller.download(context, url) { }
                        com.streamflixvip.app.update.ApkInstaller.install(context, file)
                    } catch (_: Exception) {
                    } finally {
                        isDownloadingUpdate = false
                    }
                }
            },"""
    t = t[:start] + new + t[end:]
    ma.write_text(t)
print("MA ok")

ds = Path("android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt")
t = ds.read_text()
if "resumeSeconds: Int = 0" not in t:
    t = t.replace(
        "fun DetailScreen(\n    viewModel: DetailViewModel,\n    onPlaySource:",
        "fun DetailScreen(\n    viewModel: DetailViewModel,\n    resumeSeconds: Int = 0,\n    initialSeason: Int = -1,\n    initialEpisode: Int = -1,\n    onPlaySource:",
        1,
    )
if "autoResumedContinue" not in t:
    a = "    var showMovieServerPicker by remember { mutableStateOf(false) }\n"
    b = """    var showMovieServerPicker by remember { mutableStateOf(false) }
    var autoResumedContinue by remember { mutableStateOf(false) }
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
            viewModel.loadEpisodeSources(initialSeason, ep) { src ->
                onPlaySource(src, initialSeason, ep, title, posterPath)
            }
        }
    }
"""
    if a not in t:
        raise SystemExit("DS anchor missing")
    t = t.replace(a, b, 1)
ds.write_text(t)
print("DS ok")

t = ma.read_text()
if "resumeSeconds = resumeSeconds" not in t:
    t = t.replace(
        "DetailScreen(\n                    viewModel = viewModel,\n                    onPlaySource =",
        "DetailScreen(\n                    viewModel = viewModel,\n                    resumeSeconds = resumeSeconds,\n                    initialSeason = initialSeason,\n                    initialEpisode = initialEpisode,\n                    onPlaySource =",
        1,
    )
    ma.write_text(t)
print("DONE")
