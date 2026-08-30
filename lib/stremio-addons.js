// lib/stremio-addons.js
// Busca streams de add-ons no protocolo Stremio (manifest + /stream/...).
// Labels limpos + limite por add-on. Prioridade baixa (abaixo do IPTV).
// Timeout global curto para nao travar series/animes no app.

const SUPABASE_URL =
  process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';

/** Max streams por add-on e no total (depois dos IPTV). */
const MAX_PER_ADDON = 4;
const MAX_TOTAL_ADDON = 10;

/** Priority baixo = aparece depois dos vip_sources (IPTV/manual). */
const ADDON_PRIORITY = 2;

/** Timeout por request e teto total da coleta (evita spinner infinito). */
const FETCH_TIMEOUT_MS = 4000;
const COLLECT_DEADLINE_MS = 5500;

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

async function fetchJson(url, timeoutMs = FETCH_TIMEOUT_MS) {
  const controller = new AbortController();
  const t = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const r = await fetch(url, {
      signal: controller.signal,
      headers: {
        Accept: 'application/json',
        'User-Agent': 'StreamFlixVIP/1.0 (addon-client)',
      },
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
  const path = mediaType === 'tv' ? `/tv/${tmdbId}/external_ids` : `/movie/${tmdbId}/external_ids`;
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

function buildStreamIds(mediaType, tmdbId, imdbId, season, episode) {
  const ids = [];
  const isSeries = mediaType === 'tv';
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
}

/** Aceita so HTTP(S) direto — rejeita magnet, infoHash, pagina HTML tipica. */
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

/**
 * Nome exibido no app = nome do painel (campo name).
 * Ex.: cadastre "StreamFlix.Fenix" → aparece "StreamFlix.Fenix · 720p · Dublado".
 * Qualidade e dublado/legendado continuam automaticos a partir do stream.
 */
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
    [stream.name, stream.title, stream.description, stream.quality]
      .filter(Boolean)
      .join(' '),
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
    if (provider && provider.length >= 2 && provider.length <= 18) {
      parts.push(provider);
    } else {
      parts.push('Stream');
    }
  }

  return {
    source_url: url,
    source_label: parts.join(' · '),
    priority: ADDON_PRIORITY,
    _score: streamScore(q, a),
  };
}

async function fetchStreamsFromAddon(addon, mediaType, tmdbId, imdbId, season, episode) {
  const base = (addon.base_url || baseFromManifestUrl(addon.manifest_url) || '').replace(/\/+$/, '');
  if (!base) return [];
  const candidates = buildStreamIds(mediaType, tmdbId, imdbId, season, episode);
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
        if (seenUrl.has(src.source_url)) continue;
        if (seenLabel.has(src.source_label)) continue;
        seenUrl.add(src.source_url);
        seenLabel.add(src.source_label);
        out.push(src);
      }
      if (out.length) break;
    } catch (_) {
      /* tenta proximo id */
    }
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

  const results = await Promise.allSettled(
    addons.map((a) =>
      withDeadline(
        fetchStreamsFromAddon(a, mediaType, tmdbId, imdbId, season, episode),
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
  return { manifest_url: url, base_url: base, name: String(name), raw: data };
}

module.exports = {
  normalizeManifestUrl,
  baseFromManifestUrl,
  loadActiveAddons,
  collectAddonSources,
  probeManifest,
};
