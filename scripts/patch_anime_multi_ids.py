from pathlib import Path
p = Path('lib/stremio-addons.js')
t = p.read_text()
old = '''async function resolveAnimeIds(tmdbId, mediaType) {
  const apiKey = process.env.TMDB_API_KEY;
  if (!apiKey || !tmdbId) return null;
  const kind = mediaType === 'movie' ? 'movie' : 'tv';
  try {
    const details = await fetchJson(
      `https://api.themoviedb.org/3/${kind}/${tmdbId}?api_key=${encodeURIComponent(apiKey)}&language=en-US`,
      4000,
    );
    const title = details.name || details.title || details.original_name || details.original_title;
    const lang = details.original_language || '';
    const genreIds = (details.genres || []).map((g) => Number(g.id));
    const force = mediaType === 'anime';
    const looksAnime = force || lang === 'ja' || genreIds.includes(16);
    if (!title || !looksAnime) return null;
    const kitsu = await fetchJson(
      `https://kitsu.io/api/edge/anime?filter[text]=${encodeURIComponent(title)}&page[limit]=1`,
      4000,
      { Accept: 'application/vnd.api+json' },
    );
    const row = kitsu && Array.isArray(kitsu.data) ? kitsu.data[0] : null;
    if (!row || !row.id) return null;
    return { kitsuId: String(row.id), title: String(title) };
  } catch (e) {
    console.warn('[addons] kitsu', e.message);
    return null;
  }
}'''
new = '''async function anilistQuery(payload) {
  const controller = new AbortController();
  const t = setTimeout(() => controller.abort(), 4000);
  try {
    const r = await fetch('https://graphql.anilist.co', {
      method: 'POST',
      signal: controller.signal,
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(payload),
    });
    if (!r.ok) return null;
    const j = await r.json();
    return j && j.data && j.data.Media ? j.data.Media : null;
  } catch (e) {
    console.warn('[addons] anilist', e.message);
    return null;
  } finally {
    clearTimeout(t);
  }
}

async function lookupKitsu(title) {
  if (!title) return null;
  try {
    const kitsu = await fetchJson(
      'https://kitsu.io/api/edge/anime?filter[text]=' + encodeURIComponent(title) + '&page[limit]=1',
      4000,
      { Accept: 'application/vnd.api+json' },
    );
    const row = kitsu && Array.isArray(kitsu.data) ? kitsu.data[0] : null;
    return row && row.id ? String(row.id) : null;
  } catch (_) {
    return null;
  }
}

async function resolveAnimeIds(tmdbId, mediaType) {
  const apiKey = process.env.TMDB_API_KEY;
  if (!tmdbId) return null;
  const kind = mediaType === 'movie' ? 'movie' : 'tv';
  let title = null;
  let looksAnime = mediaType === 'anime';
  if (apiKey) {
    try {
      const details = await fetchJson(
        'https://api.themoviedb.org/3/' + kind + '/' + tmdbId +
          '?api_key=' + encodeURIComponent(apiKey) + '&language=en-US',
        4000,
      );
      title = details.name || details.title || details.original_name || details.original_title;
      const lang = details.original_language || '';
      const genreIds = (details.genres || []).map((g) => Number(g.id));
      looksAnime = looksAnime || lang === 'ja' || genreIds.includes(16);
    } catch (e) {
      console.warn('[addons] tmdb anime', e.message);
    }
  }
  let anilistId = null;
  let malId = null;
  if (!title && mediaType === 'anime') {
    const m = await anilistQuery({
      query: 'query ($id: Int) { Media(id: $id, type: ANIME) { id idMal title { romaji english } } }',
      variables: { id: Number(tmdbId) },
    });
    if (m && m.id) {
      anilistId = String(m.id);
      malId = m.idMal ? String(m.idMal) : null;
      title = (m.title && (m.title.english || m.title.romaji)) || null;
      looksAnime = true;
    }
  } else if (title) {
    const m = await anilistQuery({
      query: 'query ($s: String) { Media(search: $s, type: ANIME) { id idMal title { romaji english } } }',
      variables: { s: title },
    });
    if (m && m.id) {
      anilistId = String(m.id);
      malId = m.idMal ? String(m.idMal) : null;
    }
  }
  if (!title || !looksAnime) return null;
  const kitsuId = await lookupKitsu(title);
  if (!kitsuId && !anilistId && !malId) return null;
  return {
    title: String(title),
    kitsuId: kitsuId || null,
    anilistId: anilistId || null,
    malId: malId || null,
  };
}'''
if old not in t:
    raise SystemExit('resolveAnimeIds nao encontrado')
t = t.replace(old, new, 1)
old2 = '''  if (animeIds && animeIds.kitsuId) {
    const k = animeIds.kitsuId;
    ids.push({ type: 'anime', id: `kitsu:${k}` });
    if (episode != null) {
      ids.push({ type: 'anime', id: `kitsu:${k}:${episode}` });
      ids.push({ type: 'series', id: `kitsu:${k}:${season || 1}:${episode}` });
    }
  }'''
new2 = '''  function pushAnime(prefix, idVal) {
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
  }'''
if old2 not in t:
    raise SystemExit('buildStreamIds bloco nao encontrado')
t = t.replace(old2, new2, 1)
p.write_text(t)
print('ok')
