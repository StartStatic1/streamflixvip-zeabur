// api/media-sources.js
//
// Resolve fontes de filme/série (R2 + vip_sources) no SERVIDOR.
//
// GET /api/media-sources?tmdb_id=123&type=movie

const { resolveVipAccess } = require('../lib/vip-gate');
const { enforceMinAppVersion } = require('../lib/client-gate');
const { collectAddonSources, loadActiveAddons } = require('../lib/stremio-addons');

const SUPABASE_URL =
  process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';

const R2_PUBLIC_BASE_URL = String(
  process.env.R2_PUBLIC_BASE_URL ||
    'https://pub-90cce3ad488f4a52ac089b9496083787.r2.dev',
).replace(/\/+$/, '');
const R2_CATALOG_URL = `${R2_PUBLIC_BASE_URL}/movie-fetcher/candidates.json`;
const R2_CATALOG_TTL_MS = 30 * 1000;
let r2CatalogCache = { loadedAt: 0, rows: [] };

function isMediaAuthHard() {
  const v = String(process.env.REQUIRE_AUTH_MEDIA || '').trim().toLowerCase();
  return v === '1' || v === 'true' || v === 'yes' || v === 'on';
}

function sbHeaders(serviceKey) {
  return {
    apikey: serviceKey,
    Authorization: `Bearer ${serviceKey}`,
  };
}

function isVideoServerAddon(name) {
  const t = String(name || '').toLowerCase().trim();
  if (!t) return false;
  if (/subtitle|legendas|opensubtitles|caption|\bsubs?\b|community subtitles|catalog|nexio|anilist|torii|nagare/.test(t)) {
    return false;
  }
  if (t.startsWith('streamflix.')) return true;
  return /fenix|frost|flix-streams|king\s?vod|bscine|popplay|comet|nuvio|megasource|webstream|allinone|bridge|pengu/.test(t);
}

function labelKey(label) {
  return String(label || '')
    .toLowerCase()
    .replace(/streamflix|stremflix|addon/g, ' ')
    .replace(/[^a-z0-9]+/g, ' ')
    .trim();
}

function labelMatchesHost(label, hostName) {
  const a = labelKey(label);
  const b = labelKey(hostName);
  if (!a || !b) return false;
  if (a === b) return true;
  const a0 = a.split(' ')[0];
  const b0 = b.split(' ')[0];
  return a0.length >= 4 && b0.length >= 4 && (a0 === b0 || a.includes(b0) || b.includes(a0));
}

function isR2Source(row) {
  const url = String((row && row.source_url) || '').toLowerCase();
  const label = String((row && row.source_label) || '').toLowerCase();
  if (/r2\.dev|r2\.cloudflarestorage|cloudflarestorage\.com|\.r2\./.test(url)) return true;
  if (/\br2\b/.test(label)) return true;
  if (label.includes('cloud') && (label.includes('r2') || label.includes('streamflix'))) return true;
  return false;
}

function pickR2Url(row) {
  const candidates = [row && row.r2_url, row && row.source_url, row && row.url];
  for (const raw of candidates) {
    const url = String(raw || '').trim();
    if (!/^https?:\/\//i.test(url)) continue;
    if (/r2\.dev|r2\.cloudflarestorage|\.r2\./i.test(url)) return url;
  }
  return '';
}

async function loadR2Catalog() {
  const now = Date.now();
  if (now - r2CatalogCache.loadedAt < R2_CATALOG_TTL_MS) {
    return r2CatalogCache.rows;
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 2500);
  try {
    const r = await fetch(`${R2_CATALOG_URL}?v=${now}`, {
      signal: controller.signal,
      headers: { Accept: 'application/json' },
    });
    if (!r.ok) throw new Error(`catalogo R2 ${r.status}`);
    const data = await r.json();
    const rows = Array.isArray(data) ? data : [];
    r2CatalogCache = { loadedAt: now, rows };
    return rows;
  } catch (e) {
    console.warn('[media-sources] catalogo R2:', e.message);
    return [];
  } finally {
    clearTimeout(timer);
  }
}

async function loadR2Sources(tmdbId, mediaType, season, episode) {
  if (mediaType !== 'movie') return [];
  if (episode != null) return [];
  if (season != null && season !== 0) return [];

  const rows = await loadR2Catalog();
  const match = rows.find((row) => {
    if (!row) return false;
    if (String(row.tmdb_id) !== String(tmdbId)) return false;
    return !!pickR2Url(row);
  });
  if (!match) return [];

  return [{
    source_url: pickR2Url(match),
    source_label: 'StreamFlix R2 \u00b7 HD',
    priority: 0,
  }];
}

async function loadPausedIptvHosts(serviceKey) {
  try {
    const url =
      `${SUPABASE_URL}/rest/v1/iptv_sources?select=name,is_active,source_type`;
    const r = await fetch(url, { headers: sbHeaders(serviceKey) });
    if (!r.ok) return [];
    const rows = await r.json();
    return (Array.isArray(rows) ? rows : []).filter((row) => row && row.is_active === false);
  } catch (e) {
    console.warn('[media-sources] iptv_sources:', e.message);
    return [];
  }
}

function dropPausedHosts(sources, pausedHosts) {
  if (!pausedHosts.length) return sources;
  return sources.filter((s) => {
    if (isR2Source(s)) return true;
    const label = s && s.source_label;
    return !pausedHosts.some((h) => labelMatchesHost(label, h.name));
  });
}

function qualityRank(label) {
  const t = String(label || '').toLowerCase();
  if (/\b(2160p?|4k|uhd)\b/.test(t)) return 0;
  if (/\b1080p?\b/.test(t)) return 1;
  if (/\b720p?\b/.test(t)) return 2;
  if (/dublad|\bdub\b/.test(t)) return 3;
  return 4;
}

function sourceRank(row) {
  const label = String((row && row.source_label) || '');
  const p = row && row.priority != null && Number.isFinite(Number(row.priority))
    ? Number(row.priority)
    : 50;
  return { q: qualityRank(label), p };
}

async function loadVipTitleConfig(serviceKey, tmdbId, mediaType) {
  try {
    const url =
      `${SUPABASE_URL}/rest/v1/vip_titles?tmdb_id=eq.${encodeURIComponent(tmdbId)}` +
      `&media_type=eq.${encodeURIComponent(mediaType)}` +
      `&select=vip_lock,vip_free_episode_limit`;
    const r = await fetch(url, { headers: sbHeaders(serviceKey) });
    if (!r.ok) return null;
    const rows = await r.json();
    return Array.isArray(rows) && rows.length ? rows[0] : null;
  } catch (e) {
    console.warn('[media-sources] vip_titles:', e.message);
    return null;
  }
}

function titleRequiresVip(config, episodeNumber) {
  if (!config) return false;
  if (config.vip_lock === true) return true;
  const limit = config.vip_free_episode_limit;
  if (limit != null && episodeNumber != null && Number.isFinite(episodeNumber)) {
    return episodeNumber > Number(limit);
  }
  return false;
}

async function loadEpisodesWithSources(serviceKey, tmdbId, season) {
  const q =
    SUPABASE_URL + '/rest/v1/vip_sources?tmdb_id=eq.' + encodeURIComponent(tmdbId) +
    '&media_type=eq.tv&is_active=eq.true&season=eq.' + encodeURIComponent(season) +
    '&select=episode';
  const r = await fetch(q, { headers: sbHeaders(serviceKey) });
  if (!r.ok) throw new Error('vip_sources episodes ' + r.status);
  const rows = await r.json();
  const set = new Set();
  (Array.isArray(rows) ? rows : []).forEach((row) => {
    if (row.episode != null) set.add(Number(row.episode));
  });
  try {
    const addons = await loadActiveAddons(serviceKey);
    if (addons && addons.length) {
      const apiKey = process.env.TMDB_API_KEY;
      if (apiKey) {
        const u = 'https://api.themoviedb.org/3/tv/' + tmdbId + '/season/' + season +
          '?api_key=' + encodeURIComponent(apiKey);
        const tr = await fetch(u);
        if (tr.ok) {
          const data = await tr.json();
          const today = new Date().toISOString().slice(0, 10);
          (data.episodes || []).forEach((e) => {
            const n = Number(e.episode_number);
            const air = e.air_date || '';
            if (Number.isFinite(n) && (!air || air <= today)) set.add(n);
          });
        }
      } else {
        for (let i = 1; i <= 24; i++) set.add(i);
      }
    }
  } catch (e) {
    console.warn('[media-sources] addon episodes', e && e.message);
  }
  return [...set].filter((n) => Number.isFinite(n)).sort((a, b) => a - b);
}

async function loadSources(serviceKey, tmdbId, mediaType, season, episode) {
  let q =
    `${SUPABASE_URL}/rest/v1/vip_sources?tmdb_id=eq.${encodeURIComponent(tmdbId)}` +
    `&media_type=eq.${encodeURIComponent(mediaType)}` +
    `&is_active=eq.true` +
    `&select=source_url,source_label,priority` +
    `&order=priority.asc`;

  if (mediaType === 'tv') {
    if (season != null) q += `&season=eq.${season}`;
    if (episode != null) q += `&episode=eq.${episode}`;
  } else {
    q += `&season=is.null`;
  }

  const r = await fetch(q, { headers: sbHeaders(serviceKey) });
  if (!r.ok) {
    const body = await r.text();
    throw new Error(`vip_sources ${r.status}: ${body.slice(0, 180)}`);
  }
  const rows = await r.json();
  return Array.isArray(rows) ? rows : [];
}

async function lockedAddonStubs(serviceKey) {
  const addons = await loadActiveAddons(serviceKey);
  const seen = new Set();
  const out = [];
  for (const a of addons) {
    const name = String(a.name || '').trim();
    if (!isVideoServerAddon(name)) continue;
    const key = name.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    out.push({
      source_url: '',
      source_label: name,
      priority: 2,
      vip_only: true,
    });
    if (out.length >= 8) break;
  }
  return out;
}

function pushUnique(list, seen, dest) {
  for (const source of list || []) {
    const url = String((source && source.source_url) || '').trim();
    const key = url || ('label:' + String((source && source.source_label) || ''));
    if (!key || seen.has(key)) continue;
    seen.add(key);
    dest.push(source);
  }
}

async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader(
    'Access-Control-Allow-Headers',
    'Content-Type, Authorization, X-App-Version, X-App-Version-Code, X-User-Id, X-Device-Id',
  );
  if (req.method === 'OPTIONS') {
    res.status(200).end();
    return;
  }
  if (req.method !== 'GET') {
    res.status(405).json({ error: 'Método não permitido', sources: [] });
    return;
  }

  if (enforceMinAppVersion(req, res)) return;

  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!serviceKey) {
    res.status(500).json({ error: 'Servidor sem SUPABASE_SERVICE_ROLE_KEY', sources: [] });
    return;
  }

  const tmdbId = req.query.tmdb_id;
  const mediaType = String(req.query.type || req.query.media_type || '').toLowerCase();
  const season = req.query.season != null && req.query.season !== '' ? parseInt(req.query.season, 10) : null;
  const episode =
    req.query.episode != null && req.query.episode !== '' ? parseInt(req.query.episode, 10) : null;

  if (!tmdbId) {
    res.status(400).json({ error: 'tmdb_id obrigatório', sources: [] });
    return;
  }
  if (mediaType !== 'movie' && mediaType !== 'tv') {
    res.status(400).json({ error: 'type deve ser movie ou tv', sources: [] });
    return;
  }

  if (String(req.query.list_episodes || '') === '1' && mediaType === 'tv' && season != null) {
    try {
      const episodesWithSources = await loadEpisodesWithSources(serviceKey, tmdbId, season);
      res.status(200).json({ sources: [], episodesWithSources });
    } catch (err) {
      console.error('[media-sources] list_episodes', err);
      res.status(500).json({ error: err.message || 'Erro', sources: [], episodesWithSources: [] });
    }
    return;
  }

  const hard = isMediaAuthHard();
  const access = await resolveVipAccess(req, serviceKey, { feature: 'media-sources' });
  const loggedIn = access.source === 'jwt' || access.source === 'userId' || access.source === 'deviceId';

  const vipConfig = await loadVipTitleConfig(serviceKey, tmdbId, mediaType);
  const needsVip = titleRequiresVip(vipConfig, mediaType === 'tv' ? episode : null);

  if (needsVip && !access.isVip) {
    res.status(200).json({
      error: 'VIP necessário para este título/episódio.',
      code: 'VIP_REQUIRED',
      sources: [],
      vipConfig: vipConfig
        ? { vip_lock: !!vipConfig.vip_lock, vip_free_episode_limit: vipConfig.vip_free_episode_limit ?? null }
        : null,
      requiresVip: true,
      isVip: false,
    });
    return;
  }

  if (hard && !loggedIn) {
    res.status(401).json({ error: 'Login necessário para assistir.', code: 'AUTH_REQUIRED', sources: [] });
    return;
  }

  try {
    const dbSources = await loadSources(serviceKey, tmdbId, mediaType, season, episode);
    const catalogSources = await loadR2Sources(tmdbId, mediaType, season, episode);
    const seen = new Set();
    let sources = [];
    pushUnique(catalogSources, seen, sources);
    pushUnique(dbSources, seen, sources);

    const pausedHosts = await loadPausedIptvHosts(serviceKey);
    sources = dropPausedHosts(sources, pausedHosts);

    if (access.isVip) {
      try {
        pushUnique(
          await collectAddonSources(serviceKey, tmdbId, mediaType, season, episode),
          seen,
          sources,
        );
      } catch (addonErr) {
        console.warn('[media-sources] addons skip:', addonErr.message);
      }
    } else {
      try {
        pushUnique(await lockedAddonStubs(serviceKey), seen, sources);
      } catch (stubErr) {
        console.warn('[media-sources] addon stubs skip:', stubErr.message);
      }
    }

    sources = dropPausedHosts(sources, pausedHosts);

    sources = sources.slice().sort((a, b) => {
      const aR2 = isR2Source(a) ? 1 : 0;
      const bR2 = isR2Source(b) ? 1 : 0;
      if (bR2 !== aR2) return bR2 - aR2;
      const aVip = a.source_label === 'MegaEmbed VIP' ? 1 : 0;
      const bVip = b.source_label === 'MegaEmbed VIP' ? 1 : 0;
      if (bVip !== aVip) return bVip - aVip;
      const ra = sourceRank(a);
      const rb = sourceRank(b);
      if (ra.p !== rb.p) return ra.p - rb.p;
      return ra.q - rb.q;
    });

    res.status(200).json({
      sources,
      vipConfig: vipConfig
        ? { vip_lock: !!vipConfig.vip_lock, vip_free_episode_limit: vipConfig.vip_free_episode_limit ?? null }
        : null,
      requiresVip: needsVip,
      isVip: access.isVip,
    });
  } catch (err) {
    console.error('[media-sources]', err);
    res.status(500).json({ error: err.message || 'Erro ao carregar fontes', sources: [] });
  }
}

module.exports = handler;
