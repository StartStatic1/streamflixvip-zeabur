#!/usr/bin/env python3
from pathlib import Path
p = Path('android/app/src/main/java/com/streamflixvip/app/MainActivity.kt')
t = p.read_text()

if 'import com.streamflixvip.app.data.CatalogRepository' not in t:
    t = t.replace(
        'import com.streamflixvip.app.data.AuthRepository\n',
        'import com.streamflixvip.app.data.AuthRepository\nimport com.streamflixvip.app.data.CatalogRepository\n',
        1,
    )
    print('import catalog')

if 'import kotlinx.coroutines.Dispatchers' not in t:
    t = t.replace(
        'import kotlinx.coroutines.launch\n',
        'import kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.launch\nimport kotlinx.coroutines.withContext\n',
        1,
    )
    print('import coroutines')

if 'import androidx.compose.foundation.layout.Box' not in t:
    t = t.replace(
        'import androidx.compose.foundation.layout.fillMaxSize\n',
        'import androidx.compose.foundation.background\n'
        'import androidx.compose.foundation.layout.Box\n'
        'import androidx.compose.foundation.layout.Column\n'
        'import androidx.compose.foundation.layout.Spacer\n'
        'import androidx.compose.foundation.layout.fillMaxSize\n'
        'import androidx.compose.foundation.layout.height\n'
        'import androidx.compose.material3.CircularProgressIndicator\n'
        'import androidx.compose.material3.Text\n'
        'import androidx.compose.ui.Alignment\n'
        'import androidx.compose.ui.graphics.Color\n'
        'import androidx.compose.ui.unit.dp\n',
        1,
    )
    print('import ui')

needle = '    val showTopBar = currentRoute in listOf("home", "explore", "livetv", "profile", "mylist", "genres")'
if 'var resumeBusy by remember' not in t:
    if needle not in t:
        raise SystemExit('showTopBar nao encontrado')
    t = t.replace(
        needle,
        needle + '\n    val resumeScope = rememberCoroutineScope()\n    var resumeBusy by remember { mutableStateOf(false) }\n    val catalogRepo = remember { CatalogRepository() }',
        1,
    )
    print('state ok')

click_old = (
    '                    onContinueWatchingClick = { entry ->\n'
    '                        navController.navigate(\n'
    '                            "detail/${entry.tmdb_id}/${entry.media_type}?season=${entry.season}&episode=${entry.episode}&resume=${entry.position_seconds}",\n'
    '                        )\n'
    '                    },'
)
if 'catalogRepo.getSourcesForMovie' not in t:
    if click_old not in t:
        raise SystemExit('click nao encontrado')
    click_new = Path('/dev/stdin')
    click_new = '''                    onContinueWatchingClick = { entry ->
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
    t = t.replace(click_old, click_new, 1)
    print('click ok')

scaf = '    Scaffold(\n        topBar = {'
# real newline version:
scaf = '    Scaffold(\n        topBar = {'.encode().decode('unicode_escape') if False else None
scaf = '    Scaffold(\n        topBar = {'
# Build with actual newlines:
scaf = '    Scaffold(' + chr(10) + '        topBar = {'
if 'Retomando de onde parou' not in t:
    if scaf not in t:
        raise SystemExit('scaffold nao encontrado')
    overlay = (
        '    if (resumeBusy) {' + chr(10) +
        '        androidx.compose.ui.window.Dialog(onDismissRequest = {}) {' + chr(10) +
        '            Box(' + chr(10) +
        '                modifier = Modifier' + chr(10) +
        '                    .fillMaxSize()' + chr(10) +
        '                    .background(Color(0xCC070B12)),' + chr(10) +
        '                contentAlignment = Alignment.Center,' + chr(10) +
        '            ) {' + chr(10) +
        '                Column(horizontalAlignment = Alignment.CenterHorizontally) {' + chr(10) +
        '                    CircularProgressIndicator(color = Color(0xFF00E5FF))' + chr(10) +
        '                    Spacer(Modifier.height(14.dp))' + chr(10) +
        '                    Text("Retomando de onde parou", color = Color.White)' + chr(10) +
        '                }' + chr(10) +
        '            }' + chr(10) +
        '        }' + chr(10) +
        '    }' + chr(10) + chr(10) +
        scaf
    )
    t = t.replace(scaf, overlay, 1)
    print('overlay ok')

p.write_text(t)
print('bytes', p.stat().st_size)
