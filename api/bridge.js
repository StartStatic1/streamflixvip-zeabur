// api/bridge.js — add-on Stremio gerado pela aba Bridge.
const SUPABASE_URL =
  process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';

const cache = new Map();

function svc(key) {
  return { apikey: key, Authorization: 'Bearer ' + key };
}

function norm(s) {
  return String(s || '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/\b(19|20)\d{2}\b/g, ' ')
    .replace(/\b(4k|uhd|1080p|720p|2160p|fhd|hd|sd|bluray|webrip|dublado|legendado|leg|dub)\b/g, ' ')
    .replace(/[^a-z0-9]+/g, ' ')
    .trim();
}

function hostOf(b) {
  return String(b.xtream_host || '').replace(/\/+$/, '');
}

async function xtream(b, action, extra) {
  const url = new URL(hostOf(b) + '/player_api.php');
  url.searchParams.set('username', b.xtream_user);
  url.searchParams.set('password', b.xtream_pass);
  if (action) url.searchParams.set('action', action);
  Object.entries(extra || {}).forEach(([k, v]) => {
    if (v != null) url.searchParams.set(k, String(v));
  });
  const ac = new AbortController();
  const t = setTimeout(() => ac.abort(), 20000);
  try {
    const r = await fetch(url.toString(), {
      signal: ac.signal,
      headers: { 'User-Agent': 'IPTVSmarters/1.0', Accept: 'application/json' },
    });
    if (!r.ok) throw new Error('HTTP ' + r.status);
    return await r.json();
  } finally {
    clearTimeout(t);
  }
}

async function loadBridge(id, token, key) {
  const r = await fetch(
    SUPABASE_URL + '/rest/v1/iptv_bridges?id=eq.' + encodeURIComponent(id) + '&is_active=eq.true&select=*',
    { headers: svc(key) },
  );
  const rows = await r.json();
  const row = Array.isArray(rows) && rows[0] ? rows[0] : null;
  if (!row) return null;
  const expected = String(row.access_token || '').trim();
  if (!expected || String(token || '').trim() !== expected) {
    const err = new Error('token');
    err.code = 401;
    throw err;
  }
  return row;
}

function catIds(arr) {
  return new Set((Array.isArray(arr) ? arr : []).map((c) => String(c.id || c)));
}

async function vodList(b) {
  const k = 'vod:' + b.id;
  const hit = cache.get(k);
  if (hit && Date.now() - hit.at < 30 * 60 * 1000) return hit.rows;
  const raw = await xtream(b, 'get_vod_streams').catch(() => []);
  const allow = catIds(b.vod_cats);
  const rows = (Array.isArray(raw) ? raw : []).filter((x) => !allow.size || allow.has(String(x.category_id)));
  cache.set(k, { at: Date.now(), rows });
  return rows;
}

async function seriesList(b) {
  const k = 'ser:' + b.id;
  const hit = cache.get(k);
  if (hit && Date.now() - hit.at < 30 * 60 * 1000) return hit.rows;
  const raw = await xtream(b, 'get_series').catch(() => []);
  const allow = catIds(b.series_cats);
  const rows = (Array.isArray(raw) ? raw : []).filter((x) => !allow.size || allow.has(String(x.category_id)));
  cache.set(k, { at: Date.now(), rows });
  return rows;
}

function pick(list, title) {
  const n = norm(title);
  if (!n) return null;
  let best = null;
  let bestScore = 0;
  for (const item of list) {
    const t = norm(item.name || item.title);
    if (!t) continue;
    let score = 0;
    if (t === n) score = 100;
    else if (t.includes(n) || n.includes(t)) score = 70;
    else {
      const a = new Set(n.split(' '));
      const btok = t.split(' ');
      const inter = btok.filter((w) => a.has(w) && w.length > 2).length;
      if (inter >= 2) score = 40 + inter;
    }
    if (score > bestScore) {
      bestScore = score;
      best = item;
    }
  }
  return bestScore >= 40 ? best : null;
}

async function tmdbTitle(tmdbId, kind) {
  const apiKey = process.env.TMDB_API_KEY;
  if (!apiKey || !tmdbId) return null;
  const path = kind === 'tv' ? '/tv/' + tmdbId : '/movie/' + tmdbId;
  try {
    const r = await fetch(
      'https://api.themoviedb.org/3' + path + '?api_key=' + encodeURIComponent(apiKey) + '&language=pt-BR',
    );
    if (!r.ok) return null;
    const j = await r.json();
    return j.title || j.name || j.original_title || j.original_name || null;
  } catch (_) {
    return null;
  }
}

function parseStreamPath(rest) {
  const m = String(rest || '').match(/^stream\/([^/]+)\/(.+)\.json$/i);
  if (!m) return null;
  const type = m[1].toLowerCase();
  const id = decodeURIComponent(m[2]);
  const parts = id.split(':');
  let tmdbId = null;
  let season = null;
  let episode = null;
  if (parts[0] === 'tmdb') {
    tmdbId = parts[1];
    if (parts.length >= 4) {
      season = Number(parts[2]);
      episode = Number(parts[3]);
    }
  } else if (/^\d+$/.test(parts[0]) && parts.length >= 3) {
    tmdbId = parts[0];
    season = Number(parts[1]);
    episode = Number(parts[2]);
  }
  return { type, id, tmdbId, season, episode };
}

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
  if (req.method === 'OPTIONS') { res.status(200).end(); return; }
  if (req.method !== 'GET') { res.status(405).json({ error: 'GET only' }); return; }

  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  const path = String(req.path || req.url || '').split('?')[0];
  const m = path.match(/\/api\/bridge\/([^/]+)\/([^/]+)\/(.+)$/);
  if (!m) {
    res.status(401).json({ error: 'Token obrigatorio. Use /api/bridge/ID/TOKEN/manifest.json' });
    return;
  }
  const id = m[1];
  const token = m[2];
  const rest = m[3];

  let b;
  try {
    b = await loadBridge(id, token, serviceKey);
  } catch (e) {
    if (e && e.code === 401) {
      res.status(401).json({ error: 'Token invalido ou revogado' });
      return;
    }
    throw e;
  }
  if (!b) {
    res.status(404).json({ error: 'Bridge inativa ou inexistente' });
    return;
  }

  if (rest === 'manifest.json') {
    const types = [];
    const catalogs = [];
    const idPrefixes = ['tmdb'];
    if (b.use_movies) {
      types.push('movie');
      catalogs.push({ type: 'movie', id: 'sf_movies', name: b.name + ' Filmes' });
    }
    if (b.use_series) {
      types.push('series');
      catalogs.push({ type: 'series', id: 'sf_series', name: b.name + ' Series' });
    }
    if (b.use_live) {
      types.push('tv');
      catalogs.push({ type: 'tv', id: 'sf_live', name: b.name + ' TV' });
    }
    res.status(200).json({
      id: 'streamflix.bridge.' + b.id.slice(0, 8),
      name: b.name,
      version: '1.0.0',
      description: 'Ponte Xtream StreamFlixVIP',
      resources: ['catalog', 'stream'],
      types,
      catalogs,
      idPrefixes,
    });
    return;
  }

  const streamReq = parseStreamPath(rest);
  if (streamReq) {
    const streams = [];
    const kind = streamReq.type === 'movie' ? 'movie' : 'tv';
    const title = streamReq.tmdbId ? await tmdbTitle(streamReq.tmdbId, kind) : null;
    if (title && streamReq.type === 'movie' && b.use_movies) {
      const list = await vodList(b);
      const hit = pick(list, title);
      if (hit && hit.stream_id) {
        const ext = (hit.container_extension || 'mp4').replace(/^\./, '');
        streams.push({
          name: b.name,
          title: hit.name || title,
          url: hostOf(b) + '/movie/' + b.xtream_user + '/' + b.xtream_pass + '/' + hit.stream_id + '.' + ext,
        });
      }
    }
    if (title && (streamReq.type === 'series' || streamReq.type === 'tv') && b.use_series && streamReq.episode) {
      const list = await seriesList(b);
      const hit = pick(list, title);
      if (hit && (hit.series_id || hit.stream_id)) {
        const sid = hit.series_id || hit.stream_id;
        const info = await xtream(b, 'get_series_info', { series_id: sid }).catch(() => null);
        const eps = (info && info.episodes) || {};
        const seasonKey = String(streamReq.season || 1);
        const bag = eps[seasonKey] || eps[String(Number(seasonKey))] || [];
        const ep = (Array.isArray(bag) ? bag : []).find(
          (e) => Number(e.episode_num || e.episode) === Number(streamReq.episode),
        );
        if (ep && (ep.id || ep.stream_id)) {
          const eid = ep.id || ep.stream_id;
          const ext = (ep.container_extension || 'mp4').replace(/^\./, '');
          streams.push({
            name: b.name,
            title: (hit.name || title) + ' S' + seasonKey + 'E' + streamReq.episode,
            url: hostOf(b) + '/series/' + b.xtream_user + '/' + b.xtream_pass + '/' + eid + '.' + ext,
          });
        }
      }
    }
    res.status(200).json({ streams });
    return;
  }

  res.status(200).json({ metas: [], streams: [] });
};
