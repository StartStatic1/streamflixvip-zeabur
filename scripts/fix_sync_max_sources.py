#!/usr/bin/env python3
"""Garante limite de fontes no sync de series (e filmes se aplicavel).

Por que voltava a encher: upsert com on_conflict em source_label permite
N linhas por EP (uma por servidor IPTV). Precisa checar count antes de inserir.
"""
from pathlib import Path

MAX_EP = 2
MAX_MOVIE = 3

HELPER = r'''
const MAX_SOURCES_PER_EPISODE = 2;
const MAX_SOURCES_PER_MOVIE = 3;

/** Contagem em memoria + 1 select sob demanda. Evita inserir alem do limite. */
async function episodeSourceCount(serviceKey, tmdbId, season, episode, cache) {
  const key = `tv:${tmdbId}:${season}:${episode}`;
  if (cache.has(key)) return cache.get(key);
  const q =
    `tmdb_id=eq.${encodeURIComponent(tmdbId)}` +
    `&media_type=eq.tv` +
    `&season=eq.${encodeURIComponent(season)}` +
    `&episode=eq.${encodeURIComponent(episode)}` +
    `&select=id&limit=${MAX_SOURCES_PER_EPISODE + 5}`;
  try {
    const rows = await sbSelect(serviceKey, 'vip_sources', q);
    const n = Array.isArray(rows) ? rows.length : 0;
    cache.set(key, n);
    return n;
  } catch (_
  ) {
    return 0;
  }
}

function bumpEpisodeCount(cache, tmdbId, season, episode) {
  const key = `tv:${tmdbId}:${season}:${episode}`;
  cache.set(key, (cache.get(key) || 0) + 1);
}
'''

def patch_series():
    p = Path("sync-series-standalone.js")
    t = p.read_text()
    if "MAX_SOURCES_PER_EPISODE" in t and "episodeSourceCount" in t:
        print("series already patched")
        return

    # Inject helpers after sbUpdate function block
    marker = "function normalizeTitle(s)"
    if marker not in t:
        raise SystemExit("normalizeTitle not found in series")
    if "episodeSourceCount" not in t:
        t = t.replace(marker, HELPER + "\n" + marker, 1)

    # Add countCache in processSource
    if "const countCache = new Map()" not in t:
        t = t.replace(
            "let vipSourcesRows = [];\n  const CONCURRENCY = 6;",
            "let vipSourcesRows = [];\n  const countCache = new Map();\n  const CONCURRENCY = 6;",
            1,
        )

    # Before push, check limit
    old_push = """      matched++;
      vipSourcesRows.push({
        tmdb_id: series.id,
        media_type: 'tv',
        season,
        episode,
        title: series.name || baseTitle,
        poster_path: series.poster_path || null,
        source_url: entry.url,
        source_label: source.name || DEFAULT_SOURCE_LABEL_PREFIX,
        priority: source.priority,
        is_active: true,
      });"""

    new_push = """      matched++;
      const existing = await episodeSourceCount(serviceKey, series.id, season, episode, countCache);
      if (existing >= MAX_SOURCES_PER_EPISODE) {
        continue;
      }
      vipSourcesRows.push({
        tmdb_id: series.id,
        media_type: 'tv',
        season,
        episode,
        title: series.name || baseTitle,
        poster_path: series.poster_path || null,
        source_url: entry.url,
        source_label: source.name || DEFAULT_SOURCE_LABEL_PREFIX,
        priority: source.priority,
        is_active: true,
      });
      bumpEpisodeCount(countCache, series.id, season, episode);"""

    if old_push not in t:
        raise SystemExit("push block not found in series")
    t = t.replace(old_push, new_push, 1)

    # for-loop must be async-capable: currently `for (const { entry, series, error } of results)`
    # with await inside - in async function processSource this is OK
    p.write_text(t)
    print("series patched")


def patch_movies():
    p = Path("sync-standalone.js")
    if not p.exists():
        print("no movie sync file")
        return
    t = p.read_text()
    if "MAX_SOURCES_PER_MOVIE" in t and "movieSourceCount" in t:
        print("movies already patched")
        return

    helper_m = r'''
const MAX_SOURCES_PER_MOVIE = 3;
async function movieSourceCount(serviceKey, tmdbId, cache) {
  const key = `movie:${tmdbId}`;
  if (cache.has(key)) return cache.get(key);
  const q = `tmdb_id=eq.${encodeURIComponent(tmdbId)}&media_type=eq.movie&select=id&limit=${MAX_SOURCES_PER_MOVIE + 5}`;
  try {
    const rows = await sbSelect(serviceKey, 'vip_sources', q);
    const n = Array.isArray(rows) ? rows.length : 0;
    cache.set(key, n);
    return n;
  } catch (_) {
    return 0;
  }
}
function bumpMovieCount(cache, tmdbId) {
  const key = `movie:${tmdbId}`;
  cache.set(key, (cache.get(key) || 0) + 1);
}
'''
    # Try inject before normalizeTitle if exists
    if "function normalizeTitle" in t and "movieSourceCount" not in t:
        t = t.replace("function normalizeTitle", helper_m + "\nfunction normalizeTitle", 1)
        p.write_text(t)
        print("movie helpers injected (limit check may need manual wire if push pattern differs)")
    else:
        print("movie sync skip or already has helpers")


patch_series()
patch_movies()
print("DONE")
