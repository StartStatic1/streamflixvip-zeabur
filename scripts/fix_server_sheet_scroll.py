#!/usr/bin/env python3
"""Sheet de servidores: LazyColumn com scroll + padding embaixo (não corta último item)."""
from pathlib import Path

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt")
if not p.exists():
    raise SystemExit("DetailScreen.kt missing")
t = p.read_text()

# Garantir imports (LazyColumn já existe no arquivo)
if "import androidx.compose.foundation.lazy.itemsIndexed" not in t:
    t = t.replace(
        "import androidx.compose.foundation.lazy.LazyColumn",
        "import androidx.compose.foundation.lazy.LazyColumn\nimport androidx.compose.foundation.lazy.itemsIndexed",
        1,
    )

MOVIE_OLD = '''                ModalBottomSheet(onDismissRequest = { showMovieServerPicker = false }, sheetState = sheetState) {
                    Column(Modifier.padding(bottom = 24.dp)) {
                        Text(
                            "Escolha o servidor",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
                        )
                        Column(Modifier.padding(horizontal = 20.dp)) {
                            s.movieSources.forEachIndexed { index, source ->
                                val isAddon = isAddonSourceLabel(source.source_label)
                                val lockedForFree = !isVip && (index >= FREE_SERVER_SLOTS || isAddon)
                                SourceRow(
                                    source = source,
                                    isRecommended = index == 0 && !isAddon,
                                    isLockedForFree = lockedForFree,
                                    onClick = {
                                        showMovieServerPicker = false
                                        pendingWatch = PendingSource(source, 0, 0)
                                    },
                                    onLockedClick = { showPremiumSheet = true },
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }'''

MOVIE_NEW = '''                ModalBottomSheet(onDismissRequest = { showMovieServerPicker = false }, sheetState = sheetState) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 48.dp),
                    ) {
                        item {
                            Text(
                                "Escolha o servidor",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        }
                        itemsIndexed(s.movieSources) { index, source ->
                            val isAddon = isAddonSourceLabel(source.source_label)
                            val lockedForFree = !isVip && (index >= FREE_SERVER_SLOTS || isAddon)
                            SourceRow(
                                source = source,
                                isRecommended = index == 0 && !isAddon,
                                isLockedForFree = lockedForFree,
                                onClick = {
                                    showMovieServerPicker = false
                                    pendingWatch = PendingSource(source, 0, 0)
                                },
                                onLockedClick = { showPremiumSheet = true },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }'''

EP_OLD = '''        ModalBottomSheet(
            onDismissRequest = onDismissServerPicker,
            sheetState = sheetState,
        ) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(
                    "Episódio ${state.showServerPickerForEpisode} · Escolha o servidor",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                )
                Column(Modifier.padding(horizontal = 20.dp)) {
                    state.episodeSources.forEachIndexed { index, source ->
                        val isAddon = isAddonSourceLabel(source.source_label)
                                val lockedForFree = !isVip && (index >= FREE_SERVER_SLOTS || isAddon)
                        SourceRow(
                            source = source,
                            isRecommended = index == 0 && !isAddon,
                            isLockedForFree = lockedForFree,
                            onClick = {
                                onDismissServerPicker()
                                onRequestWatch(source, state.selectedSeason ?: 0, state.selectedEpisode ?: 0)
                            },
                            onLockedClick = { showPremiumSheet = true },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }'''

EP_NEW = '''        ModalBottomSheet(
            onDismissRequest = onDismissServerPicker,
            sheetState = sheetState,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 48.dp),
            ) {
                item {
                    Text(
                        "Episódio ${state.showServerPickerForEpisode} · Escolha o servidor",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                itemsIndexed(state.episodeSources) { index, source ->
                    val isAddon = isAddonSourceLabel(source.source_label)
                    val lockedForFree = !isVip && (index >= FREE_SERVER_SLOTS || isAddon)
                    SourceRow(
                        source = source,
                        isRecommended = index == 0 && !isAddon,
                        isLockedForFree = lockedForFree,
                        onClick = {
                            onDismissServerPicker()
                            onRequestWatch(source, state.selectedSeason ?: 0, state.selectedEpisode ?: 0)
                        },
                        onLockedClick = { showPremiumSheet = true },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }'''

changed = False
if MOVIE_OLD in t:
    t = t.replace(MOVIE_OLD, MOVIE_NEW, 1)
    print("movie sheet OK")
    changed = True
else:
    print("WARN movie sheet pattern not found")

if EP_OLD in t:
    t = t.replace(EP_OLD, EP_NEW, 1)
    print("episode sheet OK")
    changed = True
else:
    print("WARN episode sheet pattern not found")

# PaddingValues import
if "PaddingValues" in t and "import androidx.compose.foundation.layout.PaddingValues" not in t:
    if "import androidx.compose.foundation.layout.padding" in t:
        t = t.replace(
            "import androidx.compose.foundation.layout.padding",
            "import androidx.compose.foundation.layout.padding\nimport androidx.compose.foundation.layout.PaddingValues",
            1,
        )
        print("PaddingValues import")

if not changed:
    raise SystemExit(2)
p.write_text(t)
print("done")
