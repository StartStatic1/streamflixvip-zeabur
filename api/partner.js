// api/partner.js
// API pública para parceiros (token).
//
// GET  /api/partner?action=ping
// GET  /api/partner?action=sources&tmdb_id=550&type=movie
// GET  /api/partner?action=sources&tmdb_id=1396&type=tv&season=1&episode=1
// GET  /api/partner?action=catalog&type=movie&limit=50&offset=0
// GET  /api/partner?action=livetv   (se scope livetv)
//
// Auth: Authorization: Bearer sf_live_...   ou   X-API-Key: sf_live_...

const { resolvePartner, hasScope } = require('../lib/partner-auth');

const SUPABASE_URL =
  process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';

function sbHeaders(serviceKey) {
  return {
    apikey: serviceKey,
    Authorization: `Bearer ${serviceKey}`,
  };
}

function cors(res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-API-Key');
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

async function loadCatalog(serviceKey, mediaType, limit, offset) {
  const qs = [
    'select=tmdb_id,media_type,title,poster_path',
    'is_active=eq.true',
    'order=created_at.desc',
    `limit=${limit}`,
    `offset=${offset}`,
  ];
  if (mediaType === 'movie' || mediaType === 'tv') {
    qs.push(`media_type=eq.${mediaType}`);
  }
  // Agrupa no app do parceiro; aqui devolvemos linhas únicas aproximadas
  const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_sources?${qs.join('&')}`, {
    headers: sbHeaders(serviceKey),
  });
  if (!r.ok) throw new Error('catalog ' + r.status);
  const rows = await r.json();
  const seen = new Set();
  const items = [];
  for (const row of Array.isArray(rows) ? rows : []) {
    const key = `${row.media_type}:${row.tmdb_id}`;
    if (seen.has(key)) continue;
    seen.add(key);
    items.push({
      tmdb_id: row.tmdb_id,
      media_type: row.media_type,
      title: row.title || null,
      poster_path: row.poster_path || null,
    });
  }
  return items;
}

async function loadLiveTv(serviceKey) {
  const r = await fetch(
    `${SUPABASE_URL}/rest/v1/live_tv_manual_channels?is_active=eq.true&select=name,logo,group_title,stream_url,priority&order=priority.asc.nullslast`,
    { headers: sbHeaders(serviceKey) },
  );
  if (!r.ok) return { channels: [], note: 'live_tv_manual_channels indisponível' };
  const rows = await r.json();
  return {
    channels: (Array.isArray(rows) ? rows : []).map((c) => ({
      name: c.name,
      logo: c.logo,
      group: c.group_title,
      url: c.stream_url,
      priority: c.priority,
    })),
  };
}

async function handler(req, res) {
  cors(res);
  if (req.method === 'OPTIONS') {
    res.status(200).end();
    return;
  }
  if (req.method !== 'GET') {
    res.status(405).json({ error: 'Use GET' });
    return;
  }

  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!serviceKey) {
    res.status(500).json({ error: 'Servidor sem SUPABASE_SERVICE_ROLE_KEY' });
    return;
  }

  const auth = await resolvePartner(req, serviceKey);
  if (!auth.ok) {
    if (auth.retryAfterSec) res.setHeader('Retry-After', String(auth.retryAfterSec));
    res.status(auth.status || 401).json({ error: auth.error });
    return;
  }

  const action = String(req.query.action || 'ping').toLowerCase();
  const partner = auth.partner;

  if (action === 'ping' || action === 'me') {
    res.status(200).json({
      ok: true,
      partner: partner.name,
      scopes: partner.scopes,
      docs: {
        sources: 'GET /api/partner?action=sources&tmdb_id=550&type=movie',
        sources_tv: 'GET /api/partner?action=sources&tmdb_id=1396&type=tv&season=1&episode=1',
        catalog: 'GET /api/partner?action=catalog&type=movie&limit=50',
        livetv: 'GET /api/partner?action=livetv',
      },
    });
    return;
  }

  if (action === 'sources') {
    if (!hasScope(partner, 'sources')) {
      res.status(403).json({ error: 'Escopo sources não liberado para este token' });
      return;
    }
    const tmdbId = req.query.tmdb_id;
    const mediaType = String(req.query.type || req.query.media_type || '').toLowerCase();
    const season =
      req.query.season != null && req.query.season !== '' ? parseInt(req.query.season, 10) : null;
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

    try {
      const sources = await loadSources(serviceKey, tmdbId, mediaType, season, episode);
      res.status(200).json({
        tmdb_id: Number(tmdbId) || tmdbId,
        type: mediaType,
        season,
        episode,
        sources: sources.map((s) => ({
          url: s.source_url,
          label: s.source_label,
          priority: s.priority,
        })),
      });
    } catch (err) {
      console.error('[partner] sources', err);
      res.status(500).json({ error: err.message || 'Erro', sources: [] });
    }
    return;
  }

  if (action === 'catalog') {
    if (!hasScope(partner, 'catalog') && !hasScope(partner, 'sources')) {
      res.status(403).json({ error: 'Escopo catalog/sources não liberado' });
      return;
    }
    const mediaType = String(req.query.type || 'all').toLowerCase();
    const limit = Math.min(100, Math.max(1, parseInt(req.query.limit, 10) || 50));
    const offset = Math.max(0, parseInt(req.query.offset, 10) || 0);
    try {
      const items = await loadCatalog(
        serviceKey,
        mediaType === 'movie' || mediaType === 'tv' ? mediaType : 'all',
        limit,
        offset,
      );
      res.status(200).json({ items, limit, offset });
    } catch (err) {
      res.status(500).json({ error: err.message || 'Erro' });
    }
    return;
  }

  if (action === 'livetv') {
    if (!hasScope(partner, 'livetv')) {
      res.status(403).json({ error: 'Escopo livetv não liberado para este token' });
      return;
    }
    try {
      const data = await loadLiveTv(serviceKey);
      res.status(200).json(data);
    } catch (err) {
      res.status(500).json({ error: err.message || 'Erro' });
    }
    return;
  }

  res.status(400).json({
    error: 'action inválida',
    allowed: ['ping', 'sources', 'catalog', 'livetv'],
  });
}

module.exports = handler;
