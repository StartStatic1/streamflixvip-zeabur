#!/usr/bin/env python3
from pathlib import Path

HELPER = r'''
const MAX_SOURCES_PER_EPISODE = 2;

async function episodeSourceCount(serviceKey, tmdbId, season, episode, cache) {
  const key = `tv:${tmdbId}:${season}:${episode}`;
  if (cache.has(key)) return cache.get(key);
  const q =
    `tmdb_id=eq.${encodeURIComponent(tmdbId)}` +
    `&media_type=eq.tv` +
    `&season=eq.${encodeURIComponent(season)}` +
    `&episode=eq.${encodeURIComponent(episode)}` +
    `&select=id&limit=10`;
  try {
    const rows = await sbSelect(serviceKey, 'vip_sources', q);
    const n = Array.isArray(rows) ? rows.length : 0;
    cache.set(key, n);
    return n;
  } catch (_) {
    return 0;
  }
}

function bumpEpisodeCount(cache, tmdbId, season, episode) {
  const key = `tv:${tmdbId}:${season}:${episode}`;
  cache.set(key, (cache.get(key) || 0) + 1);
}
'''

p = Path("sync-series-standalone.js")
t = p.read_text()

if "MAX_SOURCES_PER_EPISODE" in t and "episodeSourceCount" in t:
    print("series already patched")
else:
    marker = "function normalizeTitle(s)"
    if marker not in t:
        raise SystemExit("normalizeTitle not found")
    t = t.replace(marker, HELPER + "\n" + marker, 1)

    if "const countCache = new Map()" not in t:
        t = t.replace(
            "let vipSourcesRows = [];\n  const CONCURRENCY = 6;",
            "let vipSourcesRows = [];\n  const countCache = new Map();\n  const CONCURRENCY = 6;",
            1,
        )

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
        raise SystemExit("push block not found")
    t = t.replace(old_push, new_push, 1)
    p.write_text(t)
    print("series patched")

print("DONE")
