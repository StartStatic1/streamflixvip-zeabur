#!/usr/bin/env python3
"""Onda 2: descarta stream de addon com ano ou episodio diferente do TMDB."""
import re
from pathlib import Path

root = Path(__file__).resolve().parents[1]
p = root / "lib" / "stremio-addons.js"
t = p.read_text()
if "temporary loader" in t or len(t) < 3000:
    cache = root / "lib" / "stremio-addons.cache.js"
    if cache.exists() and cache.stat().st_size > 3000:
        t = cache.read_text()
        print("ok leu cache")
    else:
        raise SystemExit("stremio-addons stub sem cache")

HELPER = """
function yearOfBlob(s) {
  const m = String(s || '').match(/\\b((?:19|20)\\d{2})\\b/);
  return m ? Number(m[1]) : null;
}
function streamMatchesQuery(stream, queryYear, season, episode) {
  const blob = [stream && stream.name, stream && stream.title, stream && stream.description]
    .filter(Boolean)
    .join(' ');
  const y = yearOfBlob(blob);
  if (queryYear && y && Number(queryYear) !== Number(y)) return false;
  if (season != null && episode != null) {
    const se = blob.match(/\\bS\\s*0*(\\d+)\\s*[EExx]\\s*0*(\\d+)\\b/i) || blob.match(/\\b(\\d{1,2})x(\\d{1,3})\\b/i);
    if (se && (Number(se[1]) !== Number(season) || Number(se[2]) !== Number(episode))) return false;
    const epOnly = blob.match(/\\b(?:EP|E|episodio|episode)\\s*0*(\\d{1,3})\\b/i);
    if (!se && epOnly && Number(epOnly[1]) !== Number(episode)) return false;
  }
  return true;
}
async function resolveTmdbYear(tmdbId, mediaType) {
  const apiKey = process.env.TMDB_API_KEY;
  if (!apiKey || !tmdbId) return null;
  try {
    const kind = mediaType === 'tv' ? 'tv' : 'movie';
    const r = await fetch('https://api.themoviedb.org/3/' + kind + '/' + tmdbId + '?api_key=' + apiKey);
    const j = await r.json();
    const d = String((j && (j.release_date || j.first_air_date)) || '');
    const m = d.match(/^(\\d{4})/);
    return m ? Number(m[1]) : null;
  } catch (_) {
    return null;
  }
}

"""

if "function streamMatchesQuery" not in t:
    if "function streamToSource(stream, addonName, addonPriority)" in t:
        t = t.replace(
            "function streamToSource(stream, addonName, addonPriority)",
            HELPER + "function streamToSource(stream, addonName, addonPriority)",
            1,
        )
        print("ok helpers")
    else:
        print("aviso helpers")
else:
    print("ok helpers ja")

old_fn = "async function fetchStreamsFromAddon(addon, mediaType, tmdbId, imdbId, season, episode, animeIds) {"
new_fn = "async function fetchStreamsFromAddon(addon, mediaType, tmdbId, imdbId, season, episode, animeIds, queryYear) {"
if old_fn in t:
    t = t.replace(old_fn, new_fn, 1)
    print("ok fetch assinatura")
elif "queryYear" in t and "fetchStreamsFromAddon" in t:
    print("ok fetch ja")
else:
    print("aviso fetch assinatura")

old_loop = """      for (const s of streams) {
        const src = streamToSource(s, addon.name || 'Addon', addon.priority);
        if (!src) continue;"""
new_loop = """      for (const s of streams) {
        if (!streamMatchesQuery(s, queryYear, season, episode)) continue;
        const src = streamToSource(s, addon.name || 'Addon', addon.priority);
        if (!src) continue;"""
if old_loop in t:
    t = t.replace(old_loop, new_loop, 1)
    print("ok filtro loop")
elif "streamMatchesQuery(s, queryYear" in t:
    print("ok filtro ja")
else:
    print("aviso filtro loop")

old_col = """async function collectAddonSources(serviceKey, tmdbId, mediaType, season, episode) {
  const addons = await loadActiveAddons(serviceKey);
  if (!addons.length) return [];
  const imdbId = await resolveImdbId(tmdbId, mediaType);
  const animeIds = await resolveAnimeIds(tmdbId, mediaType);
  const results = await Promise.allSettled(
    addons.map((a) => withDeadline(fetchStreamsFromAddon(a, mediaType, tmdbId, imdbId, season, episode, animeIds), COLLECT_DEADLINE_MS)),
  );"""
new_col = """async function collectAddonSources(serviceKey, tmdbId, mediaType, season, episode) {
  const addons = await loadActiveAddons(serviceKey);
  if (!addons.length) return [];
  const imdbId = await resolveImdbId(tmdbId, mediaType);
  const animeIds = await resolveAnimeIds(tmdbId, mediaType);
  const queryYear = await resolveTmdbYear(tmdbId, mediaType);
  const results = await Promise.allSettled(
    addons.map((a) => withDeadline(fetchStreamsFromAddon(a, mediaType, tmdbId, imdbId, season, episode, animeIds, queryYear), COLLECT_DEADLINE_MS)),
  );"""
if old_col in t:
    t = t.replace(old_col, new_col, 1)
    print("ok collect year")
elif "resolveTmdbYear(tmdbId" in t:
    print("ok collect ja")
else:
    t2, n = re.subn(
        r"const animeIds = await resolveAnimeIds\(tmdbId, mediaType\);\n  const results",
        "const animeIds = await resolveAnimeIds(tmdbId, mediaType);\n  const queryYear = await resolveTmdbYear(tmdbId, mediaType);\n  const results",
        t,
        count=1,
    )
    if n:
        t = t2
        t = t.replace(
            "fetchStreamsFromAddon(a, mediaType, tmdbId, imdbId, season, episode, animeIds)",
            "fetchStreamsFromAddon(a, mediaType, tmdbId, imdbId, season, episode, animeIds, queryYear)",
        )
        print("ok collect year (flex)")
    else:
        print("aviso collect")

p.write_text(t)
cache = root / "lib" / "stremio-addons.cache.js"
cache.write_text(t)
print("fim apply_wave2_filter", p.stat().st_size)
print("ok conferido" if "streamMatchesQuery" in t and "resolveTmdbYear" in t else "FALHOU")
