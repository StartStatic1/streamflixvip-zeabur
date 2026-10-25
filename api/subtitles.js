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
const { loadActiveAddons, addonStreamInfo, baseFromManifestUrl } = require('../lib/stremio-addons');
const { decodeSubtitleBuffer, toVtt } = require('../lib/subtitle-text');

// Cache em memoria (limitado): busca por titulo/episodio e arquivos baixados por URL.
const SEARCH_TTL_MS = 30 * 60 * 1000;
const SEARCH_EMPTY_TTL_MS = 5 * 60 * 1000;
const FILE_TTL_MS = 6 * 60 * 60 * 1000;
function boundedCache(max) {
  const map = new Map();
  return {
    get(k) {
      const e = map.get(k);
      if (!e) return null;
      if (e.exp < Date.now()) { map.delete(k); return null; }
      map.delete(k); map.set(k, e); // LRU
      return e.v;
    },
    set(k, v, ttl) {
      map.delete(k);
      map.set(k, { v, exp: Date.now() + ttl });
      while (map.size > max) map.delete(map.keys().next().value);
    },
  };
}
const searchCache = boundedCache(400);
const fileCache = boundedCache(60);

// pt-BR primeiro, depois portugues de Portugal
function langRank(lang) {
  const l = String(lang || '').toLowerCase();
  if (l === 'pob' || l === 'pt-br' || l === 'pb' || l === 'ptbr' || l === 'pt_br') return 0;
  if (l === 'por' || l === 'pt' || l === 'pt-pt') return 1;
  return 2;
}

function sbHeaders(serviceKey) {
  return { apikey: serviceKey, Authorization: `Bearer ${serviceKey}` };
}

async function fetchJson(url, timeout = 5000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeout);
  try {
    const r = await fetch(url, {
      signal: controller.signal,
      headers: { Accept: 'application/json', 'User-Agent': 'StreamFlixVIP/1.0' },
    });
    if (!r.ok) return null;
    return await r.json();
  } catch (_) {
    return null;
  } finally {
    clearTimeout(timer);
  }
}

// So add-ons que declaram 'subtitles' no manifest (manifest em cache de 6h).
// Se o manifest nao abrir, usa o nome como antes (legenda/opensubtitles/subs...).
async function loadSubtitleAddons(serviceKey) {
  if (!serviceKey) return [];
  try {
    const rows = await loadActiveAddons(serviceKey);
    const checked = await Promise.all((rows || []).map(async (a) => {
      const base = String(a.base_url || baseFromManifestUrl(a.manifest_url) || '').replace(/\/+$/, '');
      const info = await addonStreamInfo(a, base);
      const byName = /subtitle|legenda|opensubtitle|caption|subsense|subsource|subsync|subdl/i.test(String(a.name || ''));
      const isSubtitle = info && info.known ? !!info.hasSubtitles : byName;
      return isSubtitle ? a : null;
    }));
    return checked.filter(Boolean);
  } catch (_) {
    return [];
  }
}

async function searchConfiguredAddons(serviceKey, imdbId, season, episode) {
  if (!imdbId) return [];
  const addons = await loadSubtitleAddons(serviceKey);
  const isSeries = season != null && episode != null && Number(season) > 0;
  const type = isSeries ? 'series' : 'movie';
  const id = isSeries ? `${imdbId}:${season}:${episode}` : imdbId;
  const preferred = new Set(['pob', 'por', 'pt', 'pt-br', 'pb']);
  const results = [];
  await Promise.all(addons.map(async (addon) => {
    const base = String(addon.base_url || String(addon.manifest_url || '').replace(/\/?manifest\.json$/i, '')).replace(/\/+$/, '');
    if (!base) return;
    const data = await fetchJson(`${base}/subtitles/${type}/${encodeURIComponent(id)}.json`, 7000);
    const list = Array.isArray(data?.subtitles) ? data.subtitles : [];
    list.filter((s) => s && s.url && preferred.has(String(s.lang || '').toLowerCase())).forEach((s, i) => {
      results.push({
        id: `addon-${addon.name || 'subtitle'}-${s.id || i}`,
        release: s.subtitleFileName || s.movieReleaseName || `${addon.name || 'Addon'} · Legenda PT-BR`,
        downloads: Number(s.downloads || 0),
        fps: s.fpsMilli ? s.fpsMilli / 1000 : (s.fps || null),
        hd: false,
        file_id: null,
        url: s.url,
        lang: s.lang || 'pob',
        source: addon.name || 'addon-subtitles',
      });
    });
  }));
  return results;
}

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
    signal: AbortSignal.timeout(7000),
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
  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  const { tmdb_id, season, episode, imdb_id, media_type } = req.query;
  if (!tmdb_id) {
    res.status(400).json({ error: 'Parâmetro tmdb_id obrigatório.' });
    return;
  }

  const cacheKey = [tmdb_id, season || '', episode || '', imdb_id || ''].join(':');
  const cached = searchCache.get(cacheKey);
  if (cached) {
    res.status(200).json({ results: cached, prefer: 'pt-br', cached: true });
    return;
  }

  // Busca oficial e IMDb em paralelo (antes era um depois do outro).
  let imdb = imdb_id && String(imdb_id).startsWith('tt') ? String(imdb_id) : null;
  const mt = media_type === 'tv' || season ? 'tv' : 'movie';
  const [official, imdbResolved] = await Promise.all([
    apiKey
      ? searchOfficial(apiKey, tmdb_id, season, episode).catch((e) => {
        console.warn('[subtitles] official search', e.message);
        return [];
      })
      : Promise.resolve([]),
    imdb ? Promise.resolve(imdb) : resolveImdbFromTmdb(tmdb_id, mt),
  ]);
  let results = official;
  imdb = imdbResolved;

  // Addons Stremio de legendas cadastrados no painel têm prioridade sobre
  // o fallback fixo: eles podem ter fontes PT-BR específicas para anime,
  // releases e áudio original.
  const configured = await searchConfiguredAddons(
    serviceKey,
    imdb,
    season != null && season !== '' ? Number(season) : null,
    episode != null && episode !== '' ? Number(episode) : null,
  );
  const seenConfigured = new Set(results.map((r) => `${r.release}|${r.lang}`));
  configured.forEach((item) => {
    const key = `${item.release}|${item.lang}`;
    if (!seenConfigured.has(key)) {
      results.push(item);
      seenConfigured.add(key);
    }
  });

  // Fallback Stremio se vazio ou poucas opções PT
  if (results.length < 2) {
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

  // pt-BR na frente, depois pt; dentro do grupo mantem a ordem (mais baixadas primeiro)
  results = results
    .map((r, i) => ({ r, i }))
    .sort((a, b) => langRank(a.r.lang) - langRank(b.r.lang) || a.i - b.i)
    .map((x) => x.r);
  searchCache.set(cacheKey, results, results.length ? SEARCH_TTL_MS : SEARCH_EMPTY_TTL_MS);
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

async function handleDownload(req, res) {
  const apiKey = process.env.OPENSUBTITLES_API_KEY;
  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;

  const { file_id, tmdb_id, media_type, season, episode, url } = req.query;

  // Download direto por URL (fallback Stremio — já vem filtrado PT)
  if (url && /^https?:\/\//i.test(String(url))) {
    try {
      const hit = fileCache.get(String(url));
      if (hit) {
        res.status(200).json({ content: hit, remaining: null, from_cache: true });
        return;
      }
      const fileRes = await fetch(String(url), {
        headers: { 'User-Agent': 'StreamFlixVIP/1.0' },
        signal: AbortSignal.timeout(15000),
      });
      if (!fileRes.ok) {
        res.status(502).json({ error: `Falha ao baixar legenda (${fileRes.status})` });
        return;
      }
      const buffer = Buffer.from(await fileRes.arrayBuffer());
      const content = toVtt(decodeSubtitleBuffer(buffer));
      if (content.length < 2 * 1024 * 1024) fileCache.set(String(url), content, FILE_TTL_MS);
      res.status(200).json({ content, remaining: null, from_cache: false });
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
      // O cache guarda uma legenda por episodio; so serve se for o MESMO arquivo escolhido
      // (antes trocar de legenda devolvia sempre a primeira baixada).
      if (cached && String(cached.release_name || '').startsWith(`fid:${Number(file_id)}|`)) {
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

    const fileRes = await fetch(linkData.link, { signal: AbortSignal.timeout(15000) });
    const buffer = Buffer.from(await fileRes.arrayBuffer());
    const vttContent = toVtt(decodeSubtitleBuffer(buffer));

    if (canUseCache) {
      await saveCachedSubtitle({
        tmdbId: Number(tmdb_id),
        mediaType,
        season: seasonNum,
        episode: episodeNum,
        language,
        vttContent,
        releaseName: `fid:${Number(file_id)}|${linkData.file_name || ''}`,
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
