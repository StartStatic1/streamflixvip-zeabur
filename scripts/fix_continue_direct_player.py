#!/usr/bin/env python3
from pathlib import Path

p = Path('android/app/src/main/java/com/streamflixvip/app/MainActivity.kt')
t = p.read_text()

imp = 'import com.streamflixvip.app.data.CatalogRepository\n'
if 'import com.streamflixvip.app.data.CatalogRepository' not in t:
    t = t.replace('import com.streamflixvip.app.data.AuthRepository\n', 'import com.streamflixvip.app.data.AuthRepository\n' + imp)
    print('import catalog')

if 'import kotlinx.coroutines.Dispatchers' not in t:
    t = t.replace('import kotlinx.coroutines.launch\n', 'import kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.launch\nimport kotlinx.coroutines.withContext\n')
    print('import coroutines')

if 'import androidx.compose.foundation.layout.Box' not in t:
    t = t.replace('import androidx.compose.foundation.layout.fillMaxSize\n', 'import androidx.compose.foundation.layout.Box\nimport androidx.compose.foundation.layout.Column\nimport androidx.compose.foundation.layout.Spacer\nimport androidx.compose.foundation.layout.fillMaxSize\nimport androidx.compose.foundation.layout.height\nimport androidx.compose.foundation.background\nimport androidx.compose.ui.Alignment\nimport androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.unit.dp\nimport androidx.compose.material3.CircularProgressIndicator\nimport androidx.compose.material3.Text\n')
    print('import ui')

old_state = '''    val showTopBar = currentRoute in listOf("home", "explore", "livetv", "profile", "mylist", "genres")\n'''
new_state = '''    val showTopBar = currentRoute in listOf("home", "explore", "livetv", "profile", "mylist", "genres")\n    val resumeScope = rememberCoroutineScope()\n    var resumeBusy by remember { mutableStateOf(false) }\n    val catalogRepo = remember { CatalogRepository() }\n'''
# use real newlines in search via already-written strings below

old_state = "    val showTopBar = currentRoute in listOf(\"home\", \"explore\", \"livetv\", \"profile\", \"mylist\", \"genres\")\n"
new_state = (
    "    val showTopBar = currentRoute in listOf(\"home\", \"explore\", \"livetv\", \"profile\", \"mylist\", \"genres\")\n"
    "    val resumeScope = rememberCoroutineScope()\n"
    "    var resumeBusy by remember { mutableStateOf(false) }\n"
    "    val catalogRepo = remember { CatalogRepository() }\n"
)
if 'var resumeBusy by remember' not in t:
    if old_state not in t:
        raise SystemExit('showTopBar nao encontrado')
    t = t.replace(old_state, new_state, 1)
    print('state ok')
else:
    print('state ja ok')

old_click = '''                    onContinueWatchingClick = { entry ->
                        navController.navigate(
                            "detail/${entry.tmdb_id}/${entry.media_type}?season=${entry.season}&episode=${entry.episode}&resume=${entry.position_seconds}",
                        )
                    },'''
new_click = '''                    onContinueWatchingClick = { entry ->
                        if (!resumeBusy) {
                            resumeBusy = true
                            resumeScope.launch {
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
                                }
                            }
                        }
                    },'''
if 'catalogRepo.getSourcesForMovie' not in t:
    if old_click not in t:
        raise SystemExit('click nao encontrado')
    t = t.replace(old_click, new_click, 1)
    print('click ok')
else:
    print('click ja ok')

old_scaf = '''    Scaffold(\n        topBar = {'''
# fix
old_scaf = '    Scaffold(\n        topBar = {'
new_scaf = '''    if (resumeBusy) {
        androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC070B12)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF00E5FF))
                    Spacer(Modifier.height(14.dp))
                    Text("Retomando de onde parou", color = Color.White)
                }
            }
        }
    }

    Scaffold(
        topBar = {'''
if 'Retomando de onde parou' not in t:
    if old_scaf not in t:
        raise SystemExit('scaffold nao encontrado')
    t = t.replace(old_scaf, new_scaf, 1)
    print('overlay ok')
else:
    print('overlay ja ok')

p.write_text(t)
print('bytes', p.stat().st_size)
