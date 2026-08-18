#!/usr/bin/env python3
from pathlib import Path

p = Path("android/app/src/main/java/com/streamflixvip/app/data/CatalogRepository.kt")
t = p.read_text()

if "VIP_REQUIRED" in t and "HttpException" in t and "sem fallback" in t:
    print("already patched")
    raise SystemExit(0)

# Ensure imports
if "import retrofit2.HttpException" not in t:
    t = t.replace(
        "import com.streamflixvip.app.network.VipSource",
        "import com.streamflixvip.app.network.VipSource\nimport retrofit2.HttpException",
        1,
    )

old_movie = '''    suspend fun getSourcesForMovie(tmdbId: Int): List<VipSource> {
        try {
            val res = mediaSources.getMovieSources(tmdbId)
            if (res.sources.isNotEmpty()) return prioritize(res.sources)
            // API respondeu mas vazia — título sem fonte cadastrada
            if (res.error == null) return emptyList()
        } catch (_: Exception) {
            // soft / rede: fallback
        }
        return try {
            prioritize(
                supabase.getSourcesForMovie(
                    apiKey = anonKey,
                    tmdbIdFilter = PostgrestFilter.eq(tmdbId),
                    mediaTypeFilter = PostgrestFilter.eq("movie"),
                ),
            )
        } catch (_: Exception) {
            emptyList()
        }
    }'''

new_movie = '''    suspend fun getSourcesForMovie(tmdbId: Int): List<VipSource> {
        // Sem fallback Supabase: senão free fura o vip_lock do painel.
        try {
            val res = mediaSources.getMovieSources(tmdbId)
            if (res.code == "VIP_REQUIRED" || res.code == "AUTH_REQUIRED") return emptyList()
            if (res.sources.isNotEmpty()) return prioritize(res.sources)
            return emptyList()
        } catch (e: HttpException) {
            if (e.code() == 401 || e.code() == 403) return emptyList()
            return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }
    }'''

old_ep = '''    suspend fun getSourcesForEpisode(tmdbId: Int, season: Int, episode: Int): List<VipSource> {
        try {
            val res = mediaSources.getEpisodeSources(tmdbId, season = season, episode = episode)
            if (res.sources.isNotEmpty()) return prioritize(res.sources)
            if (res.error == null) return emptyList()
        } catch (_: Exception) {
        }
        return try {
            prioritize(
                supabase.getSourcesForEpisode(
                    apiKey = anonKey,
                    tmdbIdFilter = PostgrestFilter.eq(tmdbId),
                    seasonFilter = PostgrestFilter.eq(season),
                    episodeFilter = PostgrestFilter.eq(episode),
                ),
            )
        } catch (_: Exception) {
            emptyList()
        }
    }'''

new_ep = '''    suspend fun getSourcesForEpisode(tmdbId: Int, season: Int, episode: Int): List<VipSource> {
        // Sem fallback Supabase: EP acima do limite / série trancada não pode vazar URL.
        try {
            val res = mediaSources.getEpisodeSources(tmdbId, season = season, episode = episode)
            if (res.code == "VIP_REQUIRED" || res.code == "AUTH_REQUIRED") return emptyList()
            if (res.sources.isNotEmpty()) return prioritize(res.sources)
            return emptyList()
        } catch (e: HttpException) {
            if (e.code() == 401 || e.code() == 403) return emptyList()
            return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }
    }'''

if old_movie not in t:
    raise SystemExit("movie block not found")
if old_ep not in t:
    raise SystemExit("episode block not found")

t = t.replace(old_movie, new_movie, 1).replace(old_ep, new_ep, 1)
p.write_text(t)
print("CatalogRepository patched - sem fallback")
print("DONE")
