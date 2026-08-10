#!/usr/bin/env python3
from pathlib import Path

p = Path("android/app/src/main/java/com/streamflixvip/app/data/CatalogRepository.kt")
t = p.read_text()

if "requiresVip(config" in t and "fallback so se titulo NAO exige VIP" in t:
    print("already patched")
    raise SystemExit(0)

# imports
if "import com.streamflixvip.app.network.requiresVip" not in t:
    t = t.replace(
        "import com.streamflixvip.app.network.VipSource",
        "import com.streamflixvip.app.network.VipSource\nimport com.streamflixvip.app.network.requiresVip\nimport com.streamflixvip.app.data.VipStatusHolder",
        1,
    )
if "import retrofit2.HttpException" not in t:
    t = t.replace(
        "import com.streamflixvip.app.network.VipSource",
        "import com.streamflixvip.app.network.VipSource\nimport retrofit2.HttpException",
        1,
    )

old_movie = '''    suspend fun getSourcesForMovie(tmdbId: Int): List<VipSource> {
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

new_movie = '''    suspend fun getSourcesForMovie(tmdbId: Int): List<VipSource> {
        // 1) API (respeita VIP). 2) Fallback Supabase so se titulo NAO exige VIP.
        try {
            val res = mediaSources.getMovieSources(tmdbId)
            if (res.code == "VIP_REQUIRED" || res.code == "AUTH_REQUIRED") return emptyList()
            if (res.sources.isNotEmpty()) return prioritize(res.sources)
        } catch (e: HttpException) {
            if (e.code() == 401 || e.code() == 403) return emptyList()
        } catch (_: Exception) {
        }
        val config = getVipTitleConfig(tmdbId, "movie")
        if (requiresVip(config, null) && !VipStatusHolder.isVipNow()) return emptyList()
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

old_ep = '''    suspend fun getSourcesForEpisode(tmdbId: Int, season: Int, episode: Int): List<VipSource> {
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

new_ep = '''    suspend fun getSourcesForEpisode(tmdbId: Int, season: Int, episode: Int): List<VipSource> {
        // API primeiro; fallback Supabase so se EP NAO exige VIP (respeita free ate N).
        try {
            val res = mediaSources.getEpisodeSources(tmdbId, season = season, episode = episode)
            if (res.code == "VIP_REQUIRED" || res.code == "AUTH_REQUIRED") return emptyList()
            if (res.sources.isNotEmpty()) return prioritize(res.sources)
        } catch (e: HttpException) {
            if (e.code() == 401 || e.code() == 403) return emptyList()
        } catch (_: Exception) {
        }
        val config = getVipTitleConfig(tmdbId, "tv")
        if (requiresVip(config, episode) && !VipStatusHolder.isVipNow()) return emptyList()
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

if old_movie not in t:
    raise SystemExit("movie block not found - check current file")
if old_ep not in t:
    raise SystemExit("episode block not found")
t = t.replace(old_movie, new_movie, 1).replace(old_ep, new_ep, 1)
p.write_text(t)
print("CatalogRepository OK")
print("DONE")
