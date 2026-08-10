#!/usr/bin/env python3
"""1) API devolve 200 + requiresVip (em vez de 403) para o app ler vipConfig.
   2) DetailViewModel atualiza vipConfig e expoe gate.
   3) Clique em EP bloqueado abre sheet VIP."""
from pathlib import Path

# --- API media-sources: 403 -> 200 com sources vazias ---
ms = Path("api/media-sources.js")
mt = ms.read_text()
old403 = """  if (needsVip && !access.isVip) {
    console.warn(
      `[media-sources] BLOCK vip_lock tmdb=${tmdbId} ep=${episode} user=${access.userId || '-'}`,
    );
    res.status(403).json({
      error: 'VIP necessário para este título/episódio.',
      code: 'VIP_REQUIRED',
      sources: [],
      vipConfig: vipConfig
        ? {
            vip_lock: !!vipConfig.vip_lock,
            vip_free_episode_limit: vipConfig.vip_free_episode_limit ?? null,
          }
        : null,
      requiresVip: true,
      isVip: false,
    });
    return;
  }"""

new200 = """  if (needsVip && !access.isVip) {
    // 200 (nao 403): app precisa ler vipConfig no body para mostrar cadeado/CTA.
    console.warn(
      `[media-sources] BLOCK vip_lock tmdb=${tmdbId} ep=${episode} user=${access.userId || '-'}`,
    );
    res.status(200).json({
      error: 'VIP necessário para este título/episódio.',
      code: 'VIP_REQUIRED',
      sources: [],
      vipConfig: vipConfig
        ? {
            vip_lock: !!vipConfig.vip_lock,
            vip_free_episode_limit: vipConfig.vip_free_episode_limit ?? null,
          }
        : null,
      requiresVip: true,
      isVip: false,
    });
    return;
  }"""

if old403 in mt:
    mt = mt.replace(old403, new200, 1)
    ms.write_text(mt)
    print("API 200 ok")
elif "200).json" in mt and "VIP_REQUIRED" in mt and "ep=${episode}" in mt:
    print("API already 200")
else:
    print("WARN: API block pattern not found")

# --- CatalogRepository: return vipConfig via side channel is hard; update getVipTitleConfig to probe API ---
cat = Path("android/app/src/main/java/com/streamflixvip/app/data/CatalogRepository.kt")
ct = cat.read_text()

if "probeVipConfigFromApi" not in ct:
    helper = '''
    /** Busca vip_titles via API media-sources (body sempre legivel). */
    suspend fun probeVipConfigFromApi(tmdbId: Int, mediaType: String): com.streamflixvip.app.network.VipTitleConfig? {
        return try {
            val res = if (mediaType == "tv") {
                mediaSources.getEpisodeSources(tmdbId, season = 1, episode = 1)
            } else {
                mediaSources.getMovieSources(tmdbId)
            }
            res.vipConfig
        } catch (_: Exception) {
            null
        }
    }
'''
    # insert before getVipTitleConfig
    if "suspend fun getVipTitleConfig" in ct:
        ct = ct.replace(
            "suspend fun getVipTitleConfig",
            helper + "\n    suspend fun getVipTitleConfig",
            1,
        )
        # enhance getVipTitleConfig to fallback probe
        old_g = '''    suspend fun getVipTitleConfig(tmdbId: Int, mediaType: String): com.streamflixvip.app.network.VipTitleConfig? =
        try {
            supabase.getVipTitleConfig(
                apiKey = anonKey,
                tmdbIdFilter = PostgrestFilter.eq(tmdbId),
                mediaTypeFilter = PostgrestFilter.eq(mediaType),
            ).firstOrNull()
        } catch (e: Exception) {
            null
        }'''
        new_g = '''    suspend fun getVipTitleConfig(tmdbId: Int, mediaType: String): com.streamflixvip.app.network.VipTitleConfig? {
        val fromDb = try {
            supabase.getVipTitleConfig(
                apiKey = anonKey,
                tmdbIdFilter = PostgrestFilter.eq(tmdbId),
                mediaTypeFilter = PostgrestFilter.eq(mediaType),
            ).firstOrNull()
        } catch (_: Exception) {
            null
        }
        if (fromDb != null) return fromDb
        return probeVipConfigFromApi(tmdbId, mediaType)
    }'''
        if old_g in ct:
            ct = ct.replace(old_g, new_g, 1)
            print("getVipTitleConfig enhanced")
        cat.write_text(ct)
        print("CatalogRepository probe ok")
    else:
        print("getVipTitleConfig not found")
else:
    print("probe already")

# --- DetailScreen: onSelectEpisode if locked show premium ---
ds = Path("android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt")
dt = ds.read_text()

if "episodeIsLocked" in dt and "showPremiumFromEpisode" not in dt:
    # Find onSelectEpisode callback in outer DetailScreen composable - complex
    # Patch CineverseEpisodeRow onPlay to check isLocked first - already has isLocked
    # Problem is isLocked false. Config fix should help.
    # Also when isLocked, onPlay should call onUpgrade not onSelectEpisode
    old_play = "onPlay = { onSelectEpisode(state.expandedSeason ?: 1, ep.episode_number, title, posterPath) }"
    # Need access to onUpgradeClick and isVip in scope - already have onUpgradeClick
    # Looking at structure - onPlay is only play. Change rows to:
    new_play = "onPlay = { if (state.episodeIsLocked(ep.episode_number, isVip)) onUpgradeClick() else onSelectEpisode(state.expandedSeason ?: 1, ep.episode_number, title, posterPath) }"
    if old_play in dt:
        dt = dt.replace(old_play, new_play, 1)
        print("Cineverse onPlay gated")
    old_simple = "onClick = { onSelectEpisode(state.expandedSeason ?: 1, epNum, title, posterPath) }"
    new_simple = "onClick = { if (state.episodeIsLocked(epNum, isVip)) onUpgradeClick() else onSelectEpisode(state.expandedSeason ?: 1, epNum, title, posterPath) }"
    if old_simple in dt:
        dt = dt.replace(old_simple, new_simple, 1)
        print("Simple onClick gated")
    ds.write_text(dt)
else:
    print("DetailScreen skip or already")

print("DONE")
