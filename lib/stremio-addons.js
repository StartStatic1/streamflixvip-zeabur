// lib/stremio-addons.js
// Busca streams de add-ons no protocolo Stremio (manifest + /stream/...).
// Inclui IDs TMDB/IMDb e, em anime, Kitsu.

const SUPABASE_URL =
  process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';

const MAX_PER_ADDON = 4;
const MAX_TOTAL_ADDON = 10;
const ADDON_PRIORITY = 2;
const FETCH_TIMEOUT_MS = 4000;
const COLLECT_DEADLINE_MS = 6500;

function sbHeaders(serviceKey) {
  return {
    apikey: serviceKey,
    Authorization: `Bearer ${serviceKey}`,
  };
}

function normalizeManifestUrl(raw) {
  let u = String(raw || '').trim();
  if (!u) return null;
  if (!/^https?:\/\//i.test(u)) u = 'https://' + u;
  u = u.replace(/\/+$/, '');
  if (!/manifest\.json$/i.test(u)) u = u + '/manifest.json';
  return u;
}

function baseFromManifestUrl(manifestUrl) {
  return String(manifestUrl).replace(/\/?manifest\.json$/i, '').replace(/\/+$/, '');
}

async function fetchJson(url, timeoutMs = FETCH_TIMEOUT_MS, extraHeaders) {
  const controller = new AbortController();
  const t = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const r = await fetch(url, {
      signal: controller.signal,
      headers: Object.assign(
        {
          Accept: 'application/json',
          'User-Agent': 'StreamFlixVIP/1.0 (addon-client)',
        },
        extraHeaders || {},
      ),
    });
    if (!r.ok) throw new Error(`HTTP ${r.status}`);
    return await r.json();
  } finally {
    clearTimeout(t);
  }
}

async function loadActiveAddons(serviceKey) {
  const url =
    `${SUPABASE_URL}/rest/v1/stremio_addons?is_active=eq.true` +
    `&select=id,name,manifest_url,base_url,priority&order=priority.desc`;
  const r = await fetch(url, { headers: sbHeaders(serviceKey) });
  if (!r.ok) {
    const body = await r.text();
    if (r.status === 404 || body.includes('does not exist')) return [];
    console.warn('[addons] load', r.status, body.slice(0, 120));
    return [];
  }
  const rows = await r.json();
  return Array.isArray(rows) ? rows : [];
}

async function resolveImdbId(tmdbId, mediaType) {
  const apiKey = process.env.TMDB_API_KEY;
  if (!apiKey) return null;
  const kind = mediaType === 'movie' ? 'movie' : 'tv';
  const path = kind === 'tv' ? `/tv/${tmdbId}/external_ids` : `/movie/${tmdbId}/external_ids`;
  try {
    const url = `https://api.themoviedb.org/3${path}?api_key=${encodeURIComponent(apiKey)}`;
    const data = await fetchJson(url, 4000);
    const imdb = data && data.imdb_id ? String(data.imdb_id).trim() : null;
    return imdb && imdb.startsWith('tt') ? imdb : null;
  } catch (e) {
    console.warn('[addons] imdb', e.message);
    return null;
  }
}

async function resolveAnimeIds(tmdbId, mediaType) {
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
}

function buildStreamIds(mediaType, tmdbId, imdbId, season, episode, animeIds) {
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
  if (animeIds && animeIds.kitsuId) {
    const k = animeIds.kitsuId;
    ids.push({ type: 'anime', id: `kitsu:${k}` });
    if (episode != null) {
      ids.push({ type: 'anime', id: `kitsu:${k}:${episode}` });
      ids.push({ type: 'series', id: `kitsu:${k}:${season || 1}:${episode}` });
    }
  }
  return ids;
}

function isHttpStreamUrl(u) {
  if (!u || typeof u !== 'string') return false;
  const s = u.trim();
  if (!/^https?:\/\//i.test(s)) return false;
  if (s.startsWith('magnet:')) return false;
  if (s.length < 12 || s.length > 4000) return false;
  if (/\s/.test(s)) return false;
  if (/\.(html?|php)(\?|$)/i.test(s) && !/\.m3u8|\.mpd|\.mp4|\.mkv|\.ts/i.test(s)) {
    return false;
  }
  return true;
}

function stripNoise(s) {
  return String(s || '')
    .replace(/[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}\u{FE00}-\u{FE0F}\u{200D}]/gu, '')
    .replace(/[\r\n]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function detectQuality(text) {
  const t = String(text || '').toLowerCase();
  if (/\b(2160p?|4k|uhd)\b/.test(t)) return '4K';
  if (/\b1080p?\b/.test(t)) return '1080p';
  if (/\b720p?\b/.test(t)) return '720p';
  if (/\b(480p?|360p?|sd)\b/.test(t)) return 'SD';
  return null;
}

function detectAudio(text) {
  const t = String(text || '').toLowerCase();
  if (/dublad|\bdub\b|pt-?br|dual\s*audio/.test(t)) return 'Dublado';
  if (/legendad|\bleg\b|subtitled|subtitle|\blegend\b/.test(t)) return 'Legendado';
  return null;
}

function shortAddonName(name) {
  let n = String(name || 'Addon').trim();
  n = n.replace(/^Addon\s*[·•\-]\s*/i, '').trim();
  if (!n) return 'Addon';
  if (n.length > 22) n = n.slice(0, 21) + '…';
  return n;
}

function streamScore(q, a) {
  const audio = a === 'Dublado' ? 100 : a === 'Legendado' ? 50 : 0;
  let quality = 0;
  if (q === '720p') quality = 40;
  else if (q === '1080p') quality = 30;
  else if (q === '4K') quality = 25;
  else if (q === 'SD') quality = 15;
  return audio + quality;
}

function streamToSource(stream, addonName) {
  const url = (stream.url || '').trim();
  if (!isHttpStreamUrl(url)) return null;
  if (stream.infoHash || (stream.sources && !url)) return null;

  const raw = stripNoise(
    [stream.name, stream.title, stream.description, stream.quality].filter(Boolean).join(' '),
  );
  const q = detectQuality(raw) || detectQuality(url);
  const a = detectAudio(raw);
  const host = shortAddonName(addonName);
  const parts = [host];
  if (q) parts.push(q);
  if (a) parts.push(a);
  if (!q && !a) {
    const tail = raw.split(/[·•\|\-–]/).pop();
    const provider = stripNoise(tail || '').slice(0, 18);
    parts.push(provider && provider.length >= 2 ? provider : 'Stream');
  }
  return {
    source_url: url,
    source_label: parts.join(' · '),
    priority: ADDON_PRIORITY,
    _score: streamScore(q, a),
  };
}

async function fetchStreamsFromAddon(addon, mediaType, tmdbId, imdbId, season, episode, animeIds) {
  const base = (addon.base_url || baseFromManifestUrl(addon.manifest_url) || '').replace(/\/+$/, '');
  if (!base) return [];
  const candidates = buildStreamIds(mediaType, tmdbId, imdbId, season, episode, animeIds);
  const out = [];
  const seenUrl = new Set();
  const seenLabel = new Set();

  for (const c of candidates) {
    const streamUrl = `${base}/stream/${encodeURIComponent(c.type)}/${encodeURIComponent(c.id)}.json`;
    try {
      const data = await fetchJson(streamUrl, FETCH_TIMEOUT_MS);
      const streams = Array.isArray(data && data.streams) ? data.streams : [];
      for (const s of streams) {
        const src = streamToSource(s, addon.name || 'Addon');
        if (!src) continue;
        if (seenUrl.has(src.source_url) || seenLabel.has(src.source_label)) continue;
        seenUrl.add(src.source_url);
        seenLabel.add(src.source_label);
        out.push(src);
      }
      if (out.length) break;
    } catch (_) {}
  }

  out.sort((a, b) => (b._score || 0) - (a._score || 0));
  return out.slice(0, MAX_PER_ADDON).map(({ source_url, source_label, priority }) => ({
    source_url,
    source_label,
    priority,
  }));
}

function withDeadline(promise, ms) {
  return Promise.race([
    promise,
    new Promise((resolve) => setTimeout(() => resolve([]), ms)),
  ]);
}

async function collectAddonSources(serviceKey, tmdbId, mediaType, season, episode) {
  const addons = await loadActiveAddons(serviceKey);
  if (!addons.length) return [];

  const imdbId = await resolveImdbId(tmdbId, mediaType);
  const animeIds = await resolveAnimeIds(tmdbId, mediaType);

  const results = await Promise.allSettled(
    addons.map((a) =>
      withDeadline(
        fetchStreamsFromAddon(a, mediaType, tmdbId, imdbId, season, episode, animeIds),
        COLLECT_DEADLINE_MS,
      ),
    ),
  );

  const merged = [];
  const seen = new Set();
  for (const r of results) {
    if (r.status !== 'fulfilled' || !Array.isArray(r.value)) continue;
    for (const s of r.value) {
      if (!s || seen.has(s.source_url)) continue;
      seen.add(s.source_url);
      merged.push(s);
      if (merged.length >= MAX_TOTAL_ADDON) break;
    }
    if (merged.length >= MAX_TOTAL_ADDON) break;
  }
  return merged;
}

async function probeManifest(manifestUrl) {
  const url = normalizeManifestUrl(manifestUrl);
  if (!url) throw new Error('URL invalida');
  const data = await fetchJson(url, 10000);
  if (!data || typeof data !== 'object') throw new Error('Manifest invalido');
  const name = data.name || data.id || 'Addon';
  const base = baseFromManifestUrl(url);
  const resources = Array.isArray(data.resources)
    ? data.resources.map((r) => (typeof r === 'string' ? r : r && r.name)).filter(Boolean)
    : [];
  return {
    manifest_url: url,
    base_url: base,
    name: String(name),
    resources,
    types: data.types || [],
    catalogs: Array.isArray(data.catalogs) ? data.catalogs.length : 0,
    raw: data,
  };
}

module.exports = {
  normalizeManifestUrl,
  baseFromManifestUrl,
  loadActiveAddons,
  collectAddonSources,
  resolveAnimeIds,
  probeManifest,
};
