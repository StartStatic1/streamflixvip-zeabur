#!/usr/bin/env python3
from pathlib import Path

# --- ProgressRepository: delete by title (all eps) ---
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
    if "suspend fun removeProgress" not in pt:
        raise SystemExit("removeProgress not found")
    # append before last closing brace of class
    idx = pt.rfind("}")
    pt = pt[:idx] + insert + "\n" + pt[idx:]
    pr.write_text(pt)
    print("ProgressRepository ok")
else:
    print("ProgressRepository already")

# --- SupabaseApi: deleteProgressByTitle ---
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

    /** Apaga todo progresso do titulo (filme ou serie) — usado no X do Continuar assistindo. */
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
        raise SystemExit("deleteProgress block not found")
    st = st.replace(old, new, 1)
    sa.write_text(st)
    print("SupabaseApi ok")
else:
    print("SupabaseApi already")

# --- HomeViewModel: dismiss method ---
vm = Path("android/app/src/main/java/com/streamflixvip/app/ui/home/HomeViewModel.kt")
vt = vm.read_text()
if "fun dismissContinueWatching" not in vt:
    # find end of class - add method before last }
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
    # HomeViewModel uses userId accessToken - check names
    if "private val userId" not in vt and "userId" not in vt:
        # might be constructor params
        pass
    idx = vt.rfind("}")
    vt = vt[:idx] + method + "\n" + vt[idx:]
    vm.write_text(vt)
    print("HomeViewModel ok")
else:
    print("HomeViewModel already")

# Verify userId/accessToken field names in HomeViewModel
vt = vm.read_text()
print("userId refs", vt.count("userId"))
print("accessToken refs", vt.count("accessToken"))

print("DONE - HomeScreen UI patch separate")
