// /api/subtitles.js
//
// Busca + download de legendas. Prioridade: pt-BR / pob (português BR).
// Se a API oficial vier vazia, tenta o add-on OpenSubtitles v3 (Stremio)
// filtrando só pob/por/pt.
//
// GET /api/subtitles?action=search&tmdb_id=<id>&season=<n>&episode=<n>&imdb_id=tt...
// GET /api/subtitles?action=download&file_id=<id>&tmdb_id=<id>&media_type=movie|tv&season=<n>&episode=<n>
// GET /api/subtitles?action=download&url=<https...>  (fallback Stremio — URL direta)

const SUPABASE_URL = 'https://gkujbjpvphuvrejpvvtz.supabase.co';
const STREMIO_OS_BASE = 'https://opensubtitles-v3.strem.io';

// ════════════════════════════════════════════════════════════
// BUSCA
// ════════════════════════════════════════════════════════════
async function searchOfficial(apiKey, tmdbId, season, episode) {
  const params = new URLSearchParams({
    // pt-br primeiro; pob é o código BR no ecossistema OS/Stremio
    languages: 'pt-br,pob',
    tmdb_id: String(tmdbId),
  });
  if (season) params.set('season_number', season);
  if (episode) params.set('episode_number', episode);

  const r = await fetch(`https://api.opensubtitles.com/api/v1/subtitles?${params.toString()}`, {
    headers: {
      'Api-Key': apiKey,
      'User-Agent': 'streamflixvip v1',
    },
  });
  const data = await r.json();
  if (!r.ok) throw new Error(data.message || `OS HTTP ${r.status}`);

  return (data.data || [])
    .map((item) => ({
      id: item.id,
      release: item.attributes?.release || item.attributes?.feature_details?.title || 'Legenda PT-BR',
      downloads: item.attributes?.download_count || 0,
      fps: item.attributes?.fps || null,
      hd: item.attributes?.hd || false,
      file_id: item.attributes?.files?.[0]?.file_id,
      lang: item.attributes?.language || 'pt-br',
      source: 'opensubtitles',
    }))
    .filter((x) => x.file_id)
    .sort((a, b) => b.downloads - a.downloads);
}

async function resolveImdbFromTmdb(tmdbId, mediaType) {
  const apiKey = process.env.TMDB_API_KEY;
  if (!apiKey) return null;
  const path = mediaType === 'tv' ? `/tv/${tmdbId}/external_ids` : `/movie/${tmdbId}/external_ids`;
  try {
    const r = await fetch(
      `https://api.themoviedb.org/3${path}?api_key=${encodeURIComponent(apiKey)}`,
      { signal: AbortSignal.timeout(6000) },
    );
    if (!r.ok) return null;
    const d = await r.json();
    const imdb = d && d.imdb_id ? String(d.imdb_id).trim() : null;
    return imdb && imdb.startsWith('tt') ? imdb : null;
  } catch (_) {
    return null;
  }
}

/** Fallback: add-on Stremio OpenSubtitles v3 — só pob/por/pt (nunca eng). */
async function searchStremioFallback(imdbId, season, episode) {
  if (!imdbId) return [];
  const isSeries = season != null && episode != null && Number(season) > 0;
  const id = isSeries ? `${imdbId}:${season}:${episode}` : imdbId;
  const type = isSeries ? 'series' : 'movie';
  const url = `${STREMIO_OS_BASE}/subtitles/${type}/${encodeURIComponent(id)}.json`;
  try {
    const r = await fetch(url, {
      headers: { Accept: 'application/json', 'User-Agent': 'StreamFlixVIP/1.0' },
      signal: AbortSignal.timeout(8000),
    });
    if (!r.ok) return [];
    const data = await r.json();
    const list = Array.isArray(data.subtitles) ? data.subtitles : [];
    const prefer = new Set(['pob', 'por', 'pt', 'pt-br', 'pb']);
    return list
      .filter((s) => s && s.url && prefer.has(String(s.lang || '').toLowerCase()))
      .map((s, i) => ({
        id: `stremio-${s.id || i}`,
        release: s.subtitleFileName || s.movieReleaseName || 'Legenda PT-BR (online)',
        downloads: 0,
        fps: s.fpsMilli ? s.fpsMilli / 1000 : null,
        hd: false,
        file_id: null,
        // URL direta do Stremio — download via action=download&url=
        url: s.url,
        lang: s.lang || 'pob',
        source: 'stremio-os',
      }));
  } catch (_) {
    return [];
  }
}

async function handleSearch(req, res) {
  const apiKey = process.env.OPENSUBTITLES_API_KEY;
  const { tmdb_id, season, episode, imdb_id, media_type } = req.query;
  if (!tmdb_id) {
    res.status(400).json({ error: 'Parâmetro tmdb_id obrigatório.' });
    return;
  }

  let results = [];
  if (apiKey) {
    try {
      results = await searchOfficial(apiKey, tmdb_id, season, episode);
    } catch (e) {
      console.warn('[subtitles] official search', e.message);
    }
  }

  // Fallback Stremio se vazio ou poucas opções PT
  if (results.length < 2) {
    let imdb = imdb_id && String(imdb_id).startsWith('tt') ? String(imdb_id) : null;
    if (!imdb) {
      const mt = media_type === 'tv' || season ? 'tv' : 'movie';
      imdb = await resolveImdbFromTmdb(tmdb_id, mt);
    }
    const extra = await searchStremioFallback(
      imdb,
      season != null && season !== '' ? Number(season) : null,
      episode != null && episode !== '' ? Number(episode) : null,
    );
    // evita duplicar por nome parecido
    const seen = new Set(results.map((r) => r.release));
    for (const e of extra) {
      if (seen.has(e.release)) continue;
      results.push(e);
      seen.add(e.release);
    }
  }

  res.status(200).json({ results, prefer: 'pt-br' });
}

// ════════════════════════════════════════════════════════════
// DOWNLOAD
// ════════════════════════════════════════════════════════════
async function getCachedSubtitle({ tmdbId, mediaType, season, episode, language, svcHeaders }) {
  const params = new URLSearchParams({
    tmdb_id: `eq.${tmdbId}`,
    media_type: `eq.${mediaType}`,
    season: `eq.${season}`,
    episode: `eq.${episode}`,
    language: `eq.${language}`,
    select: 'vtt_content,release_name',
    limit: '1',
  });
  const r = await fetch(`${SUPABASE_URL}/rest/v1/subtitle_cache?${params.toString()}`, { headers: svcHeaders });
  if (!r.ok) return null;
  const rows = await r.json();
  return rows[0] || null;
}

async function saveCachedSubtitle({ tmdbId, mediaType, season, episode, language, vttContent, releaseName, svcHeaders }) {
  try {
    await fetch(`${SUPABASE_URL}/rest/v1/subtitle_cache`, {
      method: 'POST',
      headers: {
        ...svcHeaders,
        'Content-Type': 'application/json',
        Prefer: 'resolution=merge-duplicates',
      },
      body: JSON.stringify([
        {
          tmdb_id: tmdbId,
          media_type: mediaType,
          season,
          episode,
          language,
          vtt_content: vttContent,
          release_name: releaseName || null,
        },
      ]),
    });
  } catch (e) {
    console.error('Falha ao salvar cache de legenda:', e.message);
  }
}

async function loginOpenSubtitles(apiKey) {
  const username = process.env.OPENSUBTITLES_USERNAME;
  const password = process.env.OPENSUBTITLES_PASSWORD;
  if (!username || !password) return null;

  try {
    const r = await fetch('https://api.opensubtitles.com/api/v1/login', {
      method: 'POST',
      headers: {
        'Api-Key': apiKey,
        'User-Agent': 'streamflixvip v1',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ username, password }),
    });
    if (!r.ok) return null;
    const data = await r.json();
    return data.token || null;
  } catch (e) {
    return null;
  }
}

function toVtt(text) {
  const t = String(text || '');
  if (t.trim().toLowerCase().startsWith('webvtt')) return t;
  return (
    'WEBVTT\n\n' +
    t
      .replace(/\r+/g, '')
      .replace(/^\d+\n/gm, '')
      .replace(/(\d{2}:\d{2}:\d{2}),(\d{3})/g, '$1.$2')
  );
}

async function handleDownload(req, res) {
  const apiKey = process.env.OPENSUBTITLES_API_KEY;
  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;

  const { file_id, tmdb_id, media_type, season, episode, url } = req.query;

  // Download direto por URL (fallback Stremio — já vem filtrado PT)
  if (url && /^https?:\/\//i.test(String(url))) {
    try {
      const fileRes = await fetch(String(url), {
        headers: { 'User-Agent': 'StreamFlixVIP/1.0' },
        signal: AbortSignal.timeout(15000),
      });
      if (!fileRes.ok) {
        res.status(502).json({ error: `Falha ao baixar legenda (${fileRes.status})` });
        return;
      }
      const buffer = Buffer.from(await fileRes.arrayBuffer());
      let text;
      try {
        text = new TextDecoder('utf-8', { fatal: true }).decode(buffer);
      } catch (e) {
        text = new TextDecoder('windows-1252').decode(buffer);
      }
      res.status(200).json({ content: toVtt(text), remaining: null, from_cache: false });
      return;
    } catch (e) {
      res.status(502).json({ error: 'Falha ao baixar legenda: ' + e.message });
      return;
    }
  }

  if (!apiKey) {
    res.status(500).json({ error: 'OPENSUBTITLES_API_KEY não configurada no servidor.' });
    return;
  }
  if (!file_id) {
    res.status(400).json({ error: 'Parâmetro file_id ou url obrigatório.' });
    return;
  }

  const mediaType = media_type === 'tv' || media_type === 'anime' ? 'tv' : 'movie';
  const seasonNum = mediaType === 'tv' ? Number(season) || 0 : 0;
  const episodeNum = mediaType === 'tv' ? Number(episode) || 0 : 0;
  const language = 'pt-br';
  const canUseCache = !!(serviceKey && tmdb_id);

  const svcHeaders = serviceKey
    ? {
        apikey: serviceKey,
        Authorization: `Bearer ${serviceKey}`,
      }
    : null;

  if (canUseCache) {
    try {
      const cached = await getCachedSubtitle({
        tmdbId: Number(tmdb_id),
        mediaType,
        season: seasonNum,
        episode: episodeNum,
        language,
        svcHeaders,
      });
      if (cached) {
        res.status(200).json({ content: cached.vtt_content, remaining: null, from_cache: true });
        return;
      }
    } catch (e) {
      console.error('Erro ao consultar cache de legenda:', e.message);
    }
  }

  try {
    const token = await loginOpenSubtitles(apiKey);
    const downloadHeaders = {
      'Api-Key': apiKey,
      'User-Agent': 'streamflixvip v1',
      'Content-Type': 'application/json',
    };
    if (token) downloadHeaders.Authorization = `Bearer ${token}`;

    const linkRes = await fetch('https://api.opensubtitles.com/api/v1/download', {
      method: 'POST',
      headers: downloadHeaders,
      body: JSON.stringify({ file_id: Number(file_id) }),
    });
    const linkData = await linkRes.json();

    if (!linkRes.ok) {
      res.status(linkRes.status).json({ error: linkData.message || 'Erro ao gerar link de download.' });
      return;
    }

    const fileRes = await fetch(linkData.link);
    const buffer = Buffer.from(await fileRes.arrayBuffer());

    let text;
    try {
      text = new TextDecoder('utf-8', { fatal: true }).decode(buffer);
    } catch (e) {
      text = new TextDecoder('windows-1252').decode(buffer);
    }

    const vttContent = toVtt(text);

    if (canUseCache) {
      await saveCachedSubtitle({
        tmdbId: Number(tmdb_id),
        mediaType,
        season: seasonNum,
        episode: episodeNum,
        language,
        vttContent,
        releaseName: linkData.file_name || null,
        svcHeaders,
      });
    }

    res.status(200).json({ content: vttContent, remaining: linkData.remaining ?? null, from_cache: false });
  } catch (e) {
    res.status(502).json({ error: 'Falha ao baixar a legenda: ' + e.message });
  }
}

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  if (req.method === 'OPTIONS') {
    res.status(200).end();
    return;
  }
  const action = req.query.action;
  if (action === 'search') {
    await handleSearch(req, res);
    return;
  }
  if (action === 'download') {
    await handleDownload(req, res);
    return;
  }
  res.status(400).json({ error: 'Informe action=search ou action=download' });
};
