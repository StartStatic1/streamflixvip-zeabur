#!/usr/bin/env python3
"""Primeira lista mais rapida + FlixHub nao some no corte de 24."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
p = root / "lib" / "stremio-addons.js"
t = p.read_text(encoding="utf-8")

t = t.replace("const MAX_PER_ADDON = 8;", "const MAX_PER_ADDON = 8;\nconst MAX_PER_FLIXHUB = 12;")
t = t.replace("const MAX_TOTAL_ADDON = 24;", "const MAX_TOTAL_ADDON = 40;")
t = t.replace("const FETCH_TIMEOUT_MS = 4000;", "const FETCH_TIMEOUT_MS = 4000;\nconst FLIX_TIMEOUT_MS = 9000;")

old = """async function collectAddonSources(serviceKey, tmdbId, mediaType, season, episode, deadlineMs) {
  const addons = await loadActiveAddons(serviceKey);
  if (!addons.length) return [];
  const imdbId = await resolveImdbId(tmdbId, mediaType);
  const animeIds = await resolveAnimeIds(tmdbId, mediaType);
  const queryYear = await resolveTmdbYear(tmdbId, mediaType);"""
new = """const sourceCache = new Map();
const SOURCE_CACHE_MS = 3 * 60 * 1000;

function isFlixAddon(addon) {
  const blob = String((addon && (addon.name || addon.manifest_url || addon.base_url)) || '').toLowerCase();
  return blob.includes('flixhub');
}

async function collectAddonSources(serviceKey, tmdbId, mediaType, season, episode, deadlineMs) {
  const cacheKey = [tmdbId, mediaType, season || '', episode || ''].join('|');
  const hit = sourceCache.get(cacheKey);
  if (hit && Date.now() - hit.at < SOURCE_CACHE_MS && hit.rows && hit.rows.length) {
    return hit.rows;
  }
  const addons = await loadActiveAddons(serviceKey);
  if (!addons.length) return [];
  const imdbId = await resolveImdbId(tmdbId, mediaType);
  const animeIds = mediaType === 'movie' ? null : await resolveAnimeIds(tmdbId, mediaType);
  const queryYear = await resolveTmdbYear(tmdbId, mediaType);"""
if old not in t:
    raise SystemExit('collectAddonSources header nao encontrado')
t = t.replace(old, new, 1)

old2 = """  return merged;
}"""
# only first return merged in collectAddonSources
idx = t.find("async function collectAddonSources")
idx2 = t.find("return merged;", idx)
if idx2 < 0:
    raise SystemExit('return merged nao encontrado')
t = t[:idx2] + """  if (merged.length) sourceCache.set(cacheKey, { at: Date.now(), rows: merged });
  return merged;
}""" + t[idx2+len("return merged;\n}"):]

# timeout maior so no FlixHub
old3 = "const data = await fetchJson(streamUrl, FETCH_TIMEOUT_MS);"
new3 = "const data = await fetchJson(streamUrl, isFlixAddon(addon) ? FLIX_TIMEOUT_MS : FETCH_TIMEOUT_MS);"
if old3 not in t:
    print('warn timeout line')
else:
    t = t.replace(old3, new3, 1)

old4 = "result.accepted = result.accepted.slice(0, MAX_PER_ADDON);"
# this is diagnose, skip
# slice in fetchStreamsFromAddon
old5 = "out.slice(0, MAX_PER_ADDON)"
if old5 in t:
    t = t.replace(old5, "out.slice(0, isFlixAddon(addon) ? MAX_PER_FLIXHUB : MAX_PER_ADDON)", 1)
    print('ok slice flixhub')
else:
    print('warn slice')

# prioriza flixhub no merge: sort addons flix first
old6 = "addons.map((a) => withDeadline("
new6 = "addons.slice().sort((a, b) => (isFlixAddon(a) ? 0 : 1) - (isFlixAddon(b) ? 0 : 1)).map((a) => withDeadline("
if old6 not in t:
    print('warn map')
else:
    t = t.replace(old6, new6, 1)
    print('ok flix first')

p.write_text(t, encoding='utf-8')

ms = root / "api" / "media-sources.js"
mt = ms.read_text(encoding='utf-8')
oldm = "return /fenix|frost|flix-streams|king\\s?vod|bscine|popplay|comet|nuvio|megasource|webstream|allinone|bridge|pengu/.test(t);"
newm = "return /fenix|frost|flixhub|flix-streams|king\\s?vod|bscine|popplay|comet|nuvio|megasource|webstream|allinone|bridge|pengu/.test(t);"
if oldm in mt:
    ms.write_text(mt.replace(oldm, newm, 1), encoding='utf-8')
    print('ok media-sources flixhub')
else:
    print('warn media-sources regex')
print('done')
