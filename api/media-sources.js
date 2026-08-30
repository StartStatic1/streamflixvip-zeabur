// api/media-sources.js
//
// Resolve fontes de filme/série (vip_sources) no SERVIDOR.
// Soft/hard auth + vip_lock iguais a antes.
// VIP: anexa streams reais dos add-ons.
// Free: anexa os mesmos add-ons como cards PREMIUM (sem URL).
//
// GET /api/media-sources?tmdb_id=123&type=movie
// GET /api/media-sources?tmdb_id=123&type=tv&season=1&episode=2
// GET /api/media-sources?tmdb_id=123&type=tv&season=1&list_episodes=1

const { resolveVipAccess } = require('../lib/vip-gate');
const { collectAddonSources, loadActiveAddons } = require('../lib/stremio-addons');

const SUPABASE_URL =
  process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';

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
  return [...set].filter((n) => Number.isFinite(n)).sort((a, b) => a - b);
}

async function loadSources(serviceKey, tmdbId, mediaType, season, episode) {
  let q =
    `${SUPABASE_URL}/rest/v1/vip_sources?tmdb_id=eq.${encodeURIComponent(tmdbId)}` +
    `&media_type=eq.${encodeURIComponent(mediaType)}` +
    `&is_active=eq.true` +
    `&select=source_url,source_label,priority` +
    `&order=priority.desc`;

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
  return addons.map((a) => ({
    source_url: '',
    source_label: String(a.name || 'Servidor Premium').trim() || 'Servidor Premium',
    priority: 2,
    vip_only: true,
  }));
}

async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  if (req.method === 'OPTIONS') {
    res.status(200).end();
    return;
  }
  if (req.method !== 'GET') {
    res.status(405).json({ error: 'Método não permitido', sources: [] });
    return;
  }

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
  const access = await resolveVipAccess(req, serviceKey);
  const loggedIn = access.source === 'jwt' || access.source === 'userId' || access.source === 'deviceId';

  const vipConfig = await loadVipTitleConfig(serviceKey, tmdbId, mediaType);
  const needsVip = titleRequiresVip(vipConfig, mediaType === 'tv' ? episode : null);

  if (needsVip && !access.isVip) {
    console.warn(
      `[media-sources] BLOCK vip_lock tmdb=${tmdbId} ep=${episode} user=${access.userId || '-'}`,
    );
    res.status(200).json({
      error: 'VIP necessário para este título/episódio.',
      code: 'VIP_REQUIRED',
      sources: [],
      vipConfig: vipConfig
        ? {
            vip_lock: !!vipConfig.vip_lock,
            vip_free_episode_limit: vipConfig.vip_free_episode_limit ?? null,
          }
        : null,
      requiresVip: true,
      isVip: false,
    });
    return;
  }

  if (hard) {
    if (!loggedIn) {
      console.warn(`[media-sources] BLOCK no-auth tmdb=${tmdbId} type=${mediaType}`);
      res.status(401).json({
        error: 'Login necessário para assistir.',
        code: 'AUTH_REQUIRED',
        sources: [],
      });
      return;
    }
    console.log(
      `[media-sources] OK hard tmdb=${tmdbId} type=${mediaType} source=${access.source} vip=${access.isVip}`,
    );
  } else {
    console.log(
      `[media-sources] soft-pass tmdb=${tmdbId} type=${mediaType} source=${access.source} vip=${access.isVip}`,
    );
  }

  try {
    let sources = await loadSources(serviceKey, tmdbId, mediaType, season, episode);

    if (access.isVip) {
      try {
        const addonSources = await collectAddonSources(
          serviceKey,
          tmdbId,
          mediaType,
          season,
          episode,
        );
        if (addonSources.length) {
          const existing = new Set(sources.map((s) => s.source_url));
          for (const a of addonSources) {
            if (!existing.has(a.source_url)) {
              sources.push(a);
              existing.add(a.source_url);
            }
          }
          console.log(`[media-sources] addons +${addonSources.length} tmdb=${tmdbId} vip=1`);
        }
      } catch (addonErr) {
        console.warn('[media-sources] addons skip:', addonErr.message);
      }
    } else {
      try {
        const stubs = await lockedAddonStubs(serviceKey);
        sources = sources.concat(stubs);
        console.log(`[media-sources] addon stubs +${stubs.length} tmdb=${tmdbId} free`);
      } catch (stubErr) {
        console.warn('[media-sources] addon stubs skip:', stubErr.message);
      }
    }

    sources = sources.slice().sort((a, b) => {
      const aVip = a.source_label === 'MegaEmbed VIP' ? 1 : 0;
      const bVip = b.source_label === 'MegaEmbed VIP' ? 1 : 0;
      if (bVip !== aVip) return bVip - aVip;
      const ap = a.priority != null ? a.priority : 0;
      const bp = b.priority != null ? b.priority : 0;
      return bp - ap;
    });

    res.status(200).json({
      sources,
      vipConfig: vipConfig
        ? {
            vip_lock: !!vipConfig.vip_lock,
            vip_free_episode_limit: vipConfig.vip_free_episode_limit ?? null,
          }
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
