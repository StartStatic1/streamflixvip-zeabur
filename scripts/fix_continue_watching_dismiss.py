#!/usr/bin/env python3
from pathlib import Path

# ProgressRepository
pr = Path("android/app/src/main/java/com/streamflixvip/app/data/ProgressRepository.kt")
pt = pr.read_text()
if "removeFromContinueWatching" not in pt:
    insert = '''
    /** Remove o titulo da lista Continuar assistindo (todos os EPs desse tmdb). */
    suspend fun removeFromContinueWatching(
        accessToken: String,
        userId: String,
        tmdbId: Int,
        mediaType: String,
    ) {
        try {
            api.deleteProgressByTitle(
                apiKey = anonKey,
                bearerToken = "Bearer $accessToken",
                userIdFilter = PostgrestFilter.eq(userId),
                tmdbIdFilter = PostgrestFilter.eq(tmdbId),
                mediaTypeFilter = PostgrestFilter.eq(mediaType),
            )
        } catch (_: Exception) {
        }
    }
'''
    idx = pt.rfind("}")
    pt = pt[:idx] + insert + "\n" + pt[idx:]
    pr.write_text(pt)
    print("ProgressRepository ok")
else:
    print("ProgressRepository already")

# SupabaseApi
sa = Path("android/app/src/main/java/com/streamflixvip/app/network/SupabaseApi.kt")
st = sa.read_text()
if "deleteProgressByTitle" not in st:
    old = '''    @retrofit2.http.DELETE("rest/v1/watch_progress")
    suspend fun deleteProgress(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("user_id") userIdFilter: String,
        @Query("tmdb_id") tmdbIdFilter: String,
        @Query("media_type") mediaTypeFilter: String,
        @Query("season") seasonFilter: String,
        @Query("episode") episodeFilter: String,
    )
}'''
    new = '''    @retrofit2.http.DELETE("rest/v1/watch_progress")
    suspend fun deleteProgress(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("user_id") userIdFilter: String,
        @Query("tmdb_id") tmdbIdFilter: String,
        @Query("media_type") mediaTypeFilter: String,
        @Query("season") seasonFilter: String,
        @Query("episode") episodeFilter: String,
    )

    @retrofit2.http.DELETE("rest/v1/watch_progress")
    suspend fun deleteProgressByTitle(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("user_id") userIdFilter: String,
        @Query("tmdb_id") tmdbIdFilter: String,
        @Query("media_type") mediaTypeFilter: String,
    )
}'''
    if old not in st:
        raise SystemExit("deleteProgress not found")
    sa.write_text(st.replace(old, new, 1))
    print("SupabaseApi ok")
else:
    print("SupabaseApi already")

# HomeViewModel
vm = Path("android/app/src/main/java/com/streamflixvip/app/ui/home/HomeViewModel.kt")
vt = vm.read_text()
if "dismissContinueWatching" not in vt:
    method = '''
    fun dismissContinueWatching(entry: WatchProgressEntry) {
        val uid = userId
        val token = accessToken
        if (uid == null || token == null) return
        viewModelScope.launch {
            progressRepository.removeFromContinueWatching(token, uid, entry.tmdb_id, entry.media_type)
            val cur = _uiState.value as? HomeUiState.Success ?: return@launch
            _uiState.value = cur.copy(
                continueWatching = cur.continueWatching.filterNot {
                    it.tmdb_id == entry.tmdb_id && it.media_type == entry.media_type
                },
            )
        }
    }
'''
    idx = vt.rfind("}")
    vt = vt[:idx] + method + vt[idx:]
    vm.write_text(vt)
    print("HomeViewModel ok")
else:
    print("HomeViewModel already")

# HomeScreen UI
hs = Path("android/app/src/main/java/com/streamflixvip/app/ui/home/HomeScreen.kt")
ht = hs.read_text()

if "onContinueWatchingDismiss" not in ht:
    ht = ht.replace(
        "onContinueWatchingClick: (WatchProgressEntry) -> Unit,",
        "onContinueWatchingClick: (WatchProgressEntry) -> Unit,\n    onContinueWatchingDismiss: (WatchProgressEntry) -> Unit = {},",
        1,
    )
    ht = ht.replace(
        "ContinueWatchingRow(entries = s.continueWatching, onItemClick = onContinueWatchingClick)",
        "ContinueWatchingRow(entries = s.continueWatching, onItemClick = onContinueWatchingClick, onItemDismiss = onContinueWatchingDismiss)",
        1,
    )
    ht = ht.replace(
        """private fun ContinueWatchingRow(
    entries: List<WatchProgressEntry>,
    onItemClick: (WatchProgressEntry) -> Unit,
) {""",
        """private fun ContinueWatchingRow(
    entries: List<WatchProgressEntry>,
    onItemClick: (WatchProgressEntry) -> Unit,
    onItemDismiss: (WatchProgressEntry) -> Unit = {},
) {""",
        1,
    )
    ht = ht.replace(
        """                ContinueWatchingCard(
                    entry = entry,
                    onClick = { onItemClick(entry) },
                )""",
        """                ContinueWatchingCard(
                    entry = entry,
                    onClick = { onItemClick(entry) },
                    onDismiss = { onItemDismiss(entry) },
                )""",
        1,
    )
    ht = ht.replace(
        """private fun ContinueWatchingCard(
    entry: WatchProgressEntry,
    onClick: () -> Unit,
) {""",
        """private fun ContinueWatchingCard(
    entry: WatchProgressEntry,
    onClick: () -> Unit,
    onDismiss: () -> Unit = {},
) {""",
        1,
    )
    # Add X button after AsyncImage inside the poster Box
    needle = """            AsyncImage(
                model = posterUrl,
                contentDescription = entry.displayTitle,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)"""
    insert_x = """            AsyncImage(
                model = posterUrl,
                contentDescription = entry.displayTitle,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // X para tirar da lista Continuar assistindo
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center,
            ) {
                Text("✕", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)"""
    if needle not in ht:
        raise SystemExit("AsyncImage block not found for X")
    ht = ht.replace(needle, insert_x, 1)
    hs.write_text(ht)
    print("HomeScreen ok")
else:
    print("HomeScreen already")

# MainActivity wire dismiss
ma = Path("android/app/src/main/java/com/streamflixvip/app/MainActivity.kt")
mt = ma.read_text()
if "onContinueWatchingDismiss" not in mt:
    # Find HomeScreen block with onContinueWatchingClick
    old = "onContinueWatchingClick = { entry ->"
    if old not in mt:
        raise SystemExit("MainActivity onContinueWatchingClick not found")
    # Insert dismiss parameter before onContinueWatchingClick line
    mt = mt.replace(
        "onContinueWatchingClick = { entry ->",
        "onContinueWatchingDismiss = { entry -> homeViewModel.dismissContinueWatching(entry) },\n                    onContinueWatchingClick = { entry ->",
        1,
    )
    # homeViewModel variable name?
    if "homeViewModel" not in mt:
        # try other names
        for name in ["homeVm", "hm", "viewModel"]:
            if name in mt:
                print("maybe", name)
    ma.write_text(mt)
    print("MainActivity ok")
else:
    print("MainActivity already")

# Fix homeViewModel name if wrong
mt = ma.read_text()
if "homeViewModel.dismissContinueWatching" in mt:
    # verify variable exists
    if "val homeViewModel" not in mt and "homeViewModel =" not in mt:
        # search pattern HomeViewModel(
        import re
        m = re.search(r"(val|var)\s+(\w+)\s*=\s*remember\s*\{[^}]*HomeViewModel",
                       mt, re.DOTALL)
        if not m:
            m = re.search(r"(val|var)\s+(\w+).*HomeViewModel", mt)
        if m:
            real = m.group(2)
            print("HomeViewModel var is", real)
            if real != "homeViewModel":
                mt = mt.replace("homeViewModel.dismissContinueWatching", f"{real}.dismissContinueWatching")
                ma.write_text(mt)
                print("renamed to", real)

print("DONE")
