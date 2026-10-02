#!/usr/bin/env python3
"""Free: nao espera IMDB/Anilist/ano em serie antes do addon."""
from pathlib import Path

p = Path(__file__).resolve().parents[1] / "lib/stremio-addons.js"
t = p.read_text()
old = """  const addons = await loadActiveAddons(serviceKey);
  if (!addons.length) return [];
  const imdbId = await resolveImdbId(tmdbId, mediaType);
  const animeIds = await resolveAnimeIds(tmdbId, mediaType);
  const queryYear = await resolveTmdbYear(tmdbId, mediaType);"""
new = """  const addons = await loadActiveAddons(serviceKey);
  if (!addons.length) return [];
  const fast = Number(deadlineMs) > 0 && Number(deadlineMs) <= 3000;
  let imdbId = null;
  let animeIds = null;
  let queryYear = null;
  if (fast) {
    imdbId = await withDeadline(resolveImdbId(tmdbId, mediaType), 700);
    if (Array.isArray(imdbId)) imdbId = null;
  } else {
    imdbId = await resolveImdbId(tmdbId, mediaType);
    animeIds = await resolveAnimeIds(tmdbId, mediaType);
    queryYear = await resolveTmdbYear(tmdbId, mediaType);
  }"""
if "const fast = Number(deadlineMs)" in t:
    print("ok ja")
elif old in t:
    p.write_text(t.replace(old, new, 1))
    print("ok skip lookups")
else:
    print("aviso bloco")
print("fim patch_free_skip_lookup")
