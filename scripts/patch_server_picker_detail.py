#!/usr/bin/env python3
from pathlib import Path
p = Path('android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt')
t = p.read_text(encoding='utf-8')
if 'serversAvailableLabel(' in t and t.count('Escolha onde assistir') >= 2:
    print('DetailScreen already patched')
    raise SystemExit(0)

old_movie_title = '''                        item {
                            Text(
                                "Escolha o servidor",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        }'''
new_movie_title = '''                        item {
                            ServerSheetTitle(
                                title = "Escolha onde assistir",
                                subtitle = serversAvailableLabel(s.movieSources.size, s.isLoadingMovieSources),
                            )
                        }'''
if old_movie_title not in t:
    raise SystemExit('movie title not found')
t = t.replace(old_movie_title, new_movie_title, 1)

old_ep_title = '''                item {
                    Text(
                        "Episódio ${state.showServerPickerForEpisode} · Escolha o servidor",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }'''
new_ep_title = '''                item {
                    ServerSheetTitle(
                        title = "Escolha onde assistir",
                        subtitle = "Episódio ${state.showServerPickerForEpisode} · " + serversAvailableLabel(state.episodeSources.size),
                    )
                }'''
if old_ep_title not in t:
    raise SystemExit('episode title not found')
t = t.replace(old_ep_title, new_ep_title, 1)

t = t.replace(
    'itemsIndexed(s.movieSources) { index, source ->',
    'itemsIndexed(s.movieSources.sortedBy { sourceDisplayRank(it) }) { index, source ->',
    1,
)
t = t.replace(
    'itemsIndexed(state.episodeSources) { index, source ->',
    'itemsIndexed(state.episodeSources.sortedBy { sourceDisplayRank(it) }) { index, source ->',
    1,
)

a = '''                                SourceRow(
                                    source = source,
                                    isRecommended = index == 0 && !isAddon,'''
b = '''                                SourceRow(
                                    source = source,
                                    index = index,
                                    isRecommended = index == 0 && !isAddon,'''
if a not in t:
    raise SystemExit('movie SourceRow not found')
t = t.replace(a, b, 1)

a2 = '''                    SourceRow(
                        source = source,
                        isRecommended = index == 0 && !isAddon,'''
b2 = '''                    SourceRow(
                        source = source,
                        index = index,
                        isRecommended = index == 0 && !isAddon,'''
if a2 not in t:
    raise SystemExit('episode SourceRow not found')
t = t.replace(a2, b2, 1)

old_fn = '''private fun SourceRow(
    source: VipSource,
    isRecommended: Boolean,
    isLockedForFree: Boolean,
    onClick: () -> Unit,
    onLockedClick: () -> Unit,
) {
    ServerSourceCard(
        source = source,
        isRecommended = isRecommended,
        isLockedForFree = isLockedForFree,
        onClick = onClick,
        onLockedClick = onLockedClick,
    )
}'''
new_fn = '''private fun SourceRow(
    source: VipSource,
    isRecommended: Boolean,
    isLockedForFree: Boolean,
    onClick: () -> Unit,
    onLockedClick: () -> Unit,
    index: Int = 0,
) {
    ServerSourceCard(
        source = source,
        index = index,
        isRecommended = isRecommended,
        isLockedForFree = isLockedForFree,
        onClick = onClick,
        onLockedClick = onLockedClick,
    )
}'''
if old_fn not in t:
    raise SystemExit('SourceRow fn not found')
t = t.replace(old_fn, new_fn, 1)
p.write_text(t, encoding='utf-8')
print('DetailScreen picker sheet patched')
