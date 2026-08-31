from pathlib import Path
p = Path('lib/stremio-addons.js')
t = p.read_text()
t = t.replace('const MAX_TOTAL_ADDON = 10;', 'const MAX_TOTAL_ADDON = 14;')
t = t.replace('const COLLECT_DEADLINE_MS = 6500;', 'const COLLECT_DEADLINE_MS = 8000;')
old = '''function buildStreamIds(mediaType, tmdbId, imdbId, season, episode, animeIds) {
  const ids = [];
  const isSeries = mediaType === 'tv' || mediaType === 'anime';
  if (isSeries && season != null && episode != null) {
    if (imdbId) ids.push({ type: 'series', id: `${imdbId}:${season}:${episode}` });
    ids.push({ type: 'series', id: `tmdb:${tmdbId}:${season}:${episode}` });
    if (imdbId) ids.push({ type: 'tv', id: `${imdbId}:${season}:${episode}` });
    ids.push({ type: 'tv', id: `tmdb:${tmdbId}:${season}:${episode}` });
  } else {
    if (imdbId) ids.push({ type: 'movie', id: imdbId });
    ids.push({ type: 'movie', id: `tmdb:${tmdbId}` });
  }
  function pushAnime(prefix, idVal) {
    if (!idVal) return;
    ids.push({ type: 'anime', id: prefix + ':' + idVal });
    if (episode != null) {
      ids.push({ type: 'anime', id: prefix + ':' + idVal + ':' + episode });
      ids.push({ type: 'series', id: prefix + ':' + idVal + ':' + (season || 1) + ':' + episode });
    }
  }
  if (animeIds) {
    pushAnime('kitsu', animeIds.kitsuId);
    pushAnime('anilist', animeIds.anilistId);
    pushAnime('mal', animeIds.malId);
  }
  return ids;
}'''
new = '''function buildStreamIds(mediaType, tmdbId, imdbId, season, episode, animeIds) {
  const ids = [];
  function pushAnime(prefix, idVal) {
    if (!idVal) return;
    ids.push({ type: 'anime', id: prefix + ':' + idVal });
    if (episode != null) {
      ids.push({ type: 'anime', id: prefix + ':' + idVal + ':' + episode });
      ids.push({ type: 'series', id: prefix + ':' + idVal + ':' + (season || 1) + ':' + episode });
    }
  }
  if (animeIds) {
    pushAnime('anilist', animeIds.anilistId);
    pushAnime('kitsu', animeIds.kitsuId);
    pushAnime('mal', animeIds.malId);
  }
  const isSeries = mediaType === 'tv' || mediaType === 'anime';
  if (isSeries && season != null && episode != null) {
    if (imdbId) ids.push({ type: 'series', id: `${imdbId}:${season}:${episode}` });
    ids.push({ type: 'series', id: `tmdb:${tmdbId}:${season}:${episode}` });
    if (imdbId) ids.push({ type: 'tv', id: `${imdbId}:${season}:${episode}` });
    ids.push({ type: 'tv', id: `tmdb:${tmdbId}:${season}:${episode}` });
  } else {
    if (imdbId) ids.push({ type: 'movie', id: imdbId });
    ids.push({ type: 'movie', id: `tmdb:${tmdbId}` });
  }
  return ids;
}'''
if old not in t:
    raise SystemExit('bloco nao encontrado')
p.write_text(t.replace(old, new, 1))
print('ok')
