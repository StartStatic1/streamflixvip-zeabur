// lib/stremio-addons.js
// Busca streams de add-ons no protocolo Stremio (manifest + /stream/...).
// Labels limpos + limite por add-on para nao poluir a lista do player.

const SUPABASE_URL =
  process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';

/** Max streams por add-on e no total (depois dos IPTV). */
const MAX_PER_ADDON = 5;
const MAX_TOTAL_ADDON = 12;

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

async function fetchJson(url, timeoutMs = 8000) {
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
    const data = await fetchJson(url, 7000);
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

function isHttpStreamUrl(u) {
  if (!u || typeof u !== 'string') return false;
  const s = u.trim();
  return /^https?:\/\//i.test(s) && !s.startsWith('magnet:');
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
  if (/dublad|\bdub\b|pt-?br|dual\s*audio|\bbr\b/.test(t)) return 'Dublado';
  if (/legendad|\bleg\b|subtitled|subtitle|\blegend\b/.test(t)) return 'Legendado';
  return null;
}

function shortAddonName(name) {
  const n = String(name || 'Addon')
    .replace(/^StreamFlix\.?/i, '')
    .replace(/^Addon\s*[·•\-]\s*/i, '')
    .trim();
  if (/fenix/i.test(n)) return 'Fenix';
  if (/frost/i.test(n)) return 'Frost';
  if (/king/i.test(n)) return 'King';
  if (/bscine|bs\s*cine/i.test(n)) return 'BsCine';
  if (/popplay/i.test(n)) return 'PopPlay';
  // remove sufixos longos
  return n.split(/[·•\|\-]/)[0].trim().slice(0, 14) || 'Addon';
}

function qualityRank(q) {
  if (q === '4K') return 4;
  if (q === '1080p') return 3;
  if (q === '720p') return 2;
  if (q === 'SD') return 1;
  return 0;
}

function audioRank(a) {
  if (a === 'Dublado') return 2;
  if (a === 'Legendado') return 1;
  return 0;
}

function streamToSource(stream, addonName) {
  const url = stream.url;
  if (!isHttpStreamUrl(url)) return null;

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

  // Se nao detectou qualidade nem audio, evita jogar titulo inteiro do filme
  // (gera confusao tipo "Best Cop Ever" no The Best Man).
  if (!q && !a) {
    // tenta pegar so o provider curto no final (Hypex, Arth, etc.)
    const tail = raw.split(/[·•\|\-–]/).pop();
    const provider = stripNoise(tail || '').slice(0, 18);
    if (provider && provider.length >= 2 && provider.length <= 18) {
      parts.push(provider);
    } else {
      parts.push('Stream');
    }
  }

  return {
    source_url: url.trim(),
    source_label: parts.join(' · '),
    priority: 5,
    _qr: qualityRank(q),
    _ar: audioRank(a),
  };
}

async function fetchStreamsFromAddon(addon, mediaType, tmdbId, imdbId, season, episode) {
  const base = (addon.base_url || baseFromManifestUrl(addon.manifest_url) || '').replace(/\/+$/, '');
  if (!base) return [];
  const candidates = buildStreamIds(mediaType, tmdbId, imdbId, season, episode);
  const out = [];
  const seen = new Set();

  for (const c of candidates) {
    const streamUrl = `${base}/stream/${encodeURIComponent(c.type)}/${encodeURIComponent(c.id)}.json`;
    try {
      const data = await fetchJson(streamUrl, 9000);
      const streams = Array.isArray(data && data.streams) ? data.streams : [];
      for (const s of streams) {
        const src = streamToSource(s, addon.name || 'Addon');
        if (!src) continue;
        if (seen.has(src.source_url)) continue;
        seen.add(src.source_url);
        out.push(src);
      }
      if (out.length) break; // ja achou com este id
    } catch (_) {
      // tenta proximo id/prefixo
    }
  }

  // Ordena: dublado > legendado, depois qualidade; corta no max
  out.sort((a, b) => {
    if (b._ar !== a._ar) return b._ar - a._ar;
    if (b._qr !== a._qr) return b._qr - a._qr;
    return 0;
  });

  return out.slice(0, MAX_PER_ADDON).map(({ source_url, source_label, priority }) => ({
    source_url,
    source_label,
    priority,
  }));
}

/**
 * Retorna fontes no mesmo formato de vip_sources (source_url, source_label, priority).
 */
async function collectAddonSources(serviceKey, tmdbId, mediaType, season, episode) {
  const addons = await loadActiveAddons(serviceKey);
  if (!addons.length) return [];

  const imdbId = await resolveImdbId(tmdbId, mediaType);

  const results = await Promise.allSettled(
    addons.map((a) => fetchStreamsFromAddon(a, mediaType, tmdbId, imdbId, season, episode)),
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
