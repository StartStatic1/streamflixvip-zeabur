// api/bridge.js — add-on Stremio/Nuvio gerado pela aba Bridge.
const SUPABASE_URL =
  process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';

const cache = new Map();
const PAGE = 80;

function svc(key) {
  return { apikey: key, Authorization: 'Bearer ' + key };
}

function tmdbKey() {
  return process.env.TMDB_API_KEY || '';
}

function yearOf(s) {
  const m = String(s || '').match(/\b((?:19|20)\d{2})\b/);
  return m ? Number(m[1]) : null;
}

function tokens(s) {
  return norm(s).split(' ').filter((w) => w.length > 2);
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

function scoreOne(itemName, queryTitle, queryYear) {
  const t = norm(itemName);
  const n = norm(queryTitle);
  if (!t || !n) return 0;
  const tTok = tokens(itemName);
  const nTok = tokens(queryTitle);
  if (!tTok.length || !nTok.length) return 0;
  const tSet = new Set(tTok);
  const nSet = new Set(nTok);
  const interN = nTok.filter((w) => tSet.has(w)).length;
  const interT = tTok.filter((w) => nSet.has(w)).length;
  const coverN = interN / nTok.length;
  const coverT = interT / tTok.length;
  const itemYear = yearOf(itemName);

  if (queryYear && itemYear && queryYear !== itemYear) return 0;

  let score = 0;
  if (t === n) score = 100;
  else if (coverN >= 0.99 && nTok.length >= 2) score = 88;
  else if (coverN >= 0.8 && coverT >= 0.7 && nTok.length >= 3) score = 80;
  else if (coverT >= 0.99 && tTok.length >= nTok.length && nTok.length >= 3) score = 78;
  else return 0;

  if (queryYear && itemYear && queryYear === itemYear) score += 12;
  return score;
}

function pick(list, titles, queryYear) {
  const bag = (Array.isArray(titles) ? titles : [titles]).filter(Boolean);
  if (!bag.length) return null;
  let best = null;
  let bestScore = 0;
  for (const item of list) {
    const name = item.name || item.title || '';
    for (const q of bag) {
      const score = scoreOne(name, q, queryYear);
      if (score > bestScore) {
        bestScore = score;
        best = item;
      }
    }
  }
  return bestScore >= 78 ? best : null;
}

function metaFromTmdb(j) {
  const titles = [j.title, j.name, j.original_title, j.original_name].filter(Boolean);
  const y = yearOf(j.release_date || j.first_air_date || '');
  return { titles, year: y };
}

async function tmdbTitle(tmdbId, kind) {
  const apiKey = tmdbKey();
  if (!apiKey || !tmdbId) return { titles: [], year: null };
  const path = kind === 'tv' ? '/tv/' + tmdbId : '/movie/' + tmdbId;
  try {
    const r = await fetch(
      'https://api.themoviedb.org/3' + path +
        '?api_key=' + encodeURIComponent(apiKey) +
        '&language=pt-BR',
    );
    if (!r.ok) return { titles: [], year: null };
    return metaFromTmdb(await r.json());
  } catch (_) {
    return { titles: [], year: null };
  }
}

async function imdbTitles(imdbId, kind) {
  const apiKey = tmdbKey();
  if (!apiKey || !imdbId) return { titles: [], year: null };
  const k = 'imdb:' + imdbId + ':' + kind;
  const hit = cache.get(k);
  if (hit && Date.now() - hit.at < 12 * 60 * 60 * 1000) return hit.meta;
  try {
    const r = await fetch(
      'https://api.themoviedb.org/3/find/' + encodeURIComponent(imdbId) +
        '?api_key=' + encodeURIComponent(apiKey) +
        '&external_source=imdb_id&language=pt-BR',
    );
    if (!r.ok) return { titles: [], year: null };
    const j = await r.json();
    const rows = kind === 'tv' ? (j.tv_results || []) : (j.movie_results || []);
    const row = rows[0] || (j.tv_results && j.tv_results[0]) || (j.movie_results && j.movie_results[0]);
    const meta = row ? metaFromTmdb(row) : { titles: [], year: null };
    cache.set(k, { at: Date.now(), meta });
    return meta;
  } catch (_) {
    return { titles: [], year: null };
  }
}

function parseStreamPath(rest) {
  const m = String(rest || '').match(/^stream\/([^/]+)\/(.+)\.json$/i);
  if (!m) return null;
  const type = m[1].toLowerCase();
  const raw = decodeURIComponent(m[2]);
  const parts = raw.split(':');
  let tmdbId = null;
  let imdbId = null;
  let xtreamId = null;
  let season = null;
  let episode = null;
  if (parts[0] === 'tmdb') {
    tmdbId = parts[1];
    if (parts.length >= 4) {
      season = Number(parts[2]);
      episode = Number(parts[3]);
    }
  } else if (parts[0] === 'xtream' || parts[0] === 'sf') {
    xtreamId = parts[1];
    if (parts.length >= 4) {
      season = Number(parts[2]);
      episode = Number(parts[3]);
    }
  } else if (/^tt\d+$/i.test(parts[0])) {
    imdbId = parts[0];
    if (parts.length >= 3) {
      season = Number(parts[1]);
      episode = Number(parts[2]);
    }
  } else if (/^\d+$/.test(parts[0]) && parts.length >= 3) {
    tmdbId = parts[0];
    season = Number(parts[1]);
    episode = Number(parts[2]);
  }
  return { type, id: raw, tmdbId, imdbId, xtreamId, season, episode };
}

function parseCatalog(rest) {
  const m = String(rest || '').match(/^catalog\/([^/]+)\/([^/.]+)(?:\/(.*))?\.json$/i);
  if (!m) return null;
  const extra = {};
  String(m[3] || '').split('&').forEach((p) => {
    const i = p.indexOf('=');
    if (i > 0) extra[decodeURIComponent(p.slice(0, i))] = decodeURIComponent(p.slice(i + 1));
  });
  return { type: m[1].toLowerCase(), id: m[2], extra };
}

function posterOf(item) {
  const p = item.stream_icon || item.cover || item.cover_big || '';
  return p || null;
}

function pageSlice(rows, extra) {
  const skip = Math.max(0, Number(extra.skip || 0) || 0);
  const q = norm(extra.search || '');
  let list = rows;
  if (q) list = rows.filter((x) => norm(x.name || x.title).includes(q));
  const slice = list.slice(skip, skip + PAGE);
  return { slice, hasMore: skip + PAGE < list.length };
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
    const extras = [
      { name: 'search', isRequired: false },
      { name: 'skip', isRequired: false },
    ];
    const types = [];
    const catalogs = [];
    if (b.use_movies) {
      types.push('movie');
      catalogs.push({ type: 'movie', id: 'sf_movies', name: b.name + ' Filmes', extra: extras });
    }
    if (b.use_series) {
      types.push('series');
      catalogs.push({ type: 'series', id: 'sf_series', name: b.name + ' Series', extra: extras });
    }
    if (b.use_live) {
      types.push('tv');
      catalogs.push({ type: 'tv', id: 'sf_live', name: b.name + ' TV', extra: extras });
    }
    res.status(200).json({
      id: 'streamflix.bridge.' + b.id.slice(0, 8),
      name: b.name,
      version: '1.2.0',
      description: 'Ponte Xtream StreamFlixVIP',
      resources: ['catalog', 'stream'],
      types,
      catalogs,
      idPrefixes: ['tt', 'tmdb', 'xtream', 'sf'],
    });
    return;
  }

  const catReq = parseCatalog(rest);
  if (catReq) {
    const metas = [];
    if (catReq.type === 'movie' && b.use_movies) {
      const list = await vodList(b);
      const { slice } = pageSlice(list, catReq.extra);
      slice.forEach((item) => {
        const sid = item.stream_id;
        if (!sid) return;
        metas.push({
          id: 'xtream:' + sid,
          type: 'movie',
          name: item.name || item.title || 'Filme',
          poster: posterOf(item),
        });
      });
    }
    if ((catReq.type === 'series' || catReq.type === 'tv') && b.use_series) {
      const list = await seriesList(b);
      const { slice } = pageSlice(list, catReq.extra);
      slice.forEach((item) => {
        const sid = item.series_id || item.stream_id;
        if (!sid) return;
        metas.push({
          id: 'xtream:' + sid,
          type: 'series',
          name: item.name || item.title || 'Serie',
          poster: posterOf(item),
        });
      });
    }
    res.status(200).json({ metas });
    return;
  }

  const streamReq = parseStreamPath(rest);
  if (streamReq) {
    const streams = [];
    const kind = streamReq.type === 'movie' ? 'movie' : 'tv';
    let meta = { titles: [], year: null };
    if (streamReq.imdbId) meta = await imdbTitles(streamReq.imdbId, kind);
    if (!meta.titles.length && streamReq.tmdbId) meta = await tmdbTitle(streamReq.tmdbId, kind);
    const titles = meta.titles || [];
    const year = meta.year || null;

    if (streamReq.type === 'movie' && b.use_movies) {
      const list = await vodList(b);
      let hit = null;
      if (streamReq.xtreamId) {
        hit = list.find((x) => String(x.stream_id) === String(streamReq.xtreamId));
      }
      if (!hit && titles.length) hit = pick(list, titles, year);
      if (hit && hit.stream_id) {
        const ext = (hit.container_extension || 'mp4').replace(/^\./, '');
        streams.push({
          name: b.name,
          title: hit.name || titles[0] || 'Filme',
          url: hostOf(b) + '/movie/' + b.xtream_user + '/' + b.xtream_pass + '/' + hit.stream_id + '.' + ext,
        });
      }
    }

    if ((streamReq.type === 'series' || streamReq.type === 'tv') && b.use_series) {
      const list = await seriesList(b);
      let hit = null;
      if (streamReq.xtreamId) {
        hit = list.find((x) => String(x.series_id || x.stream_id) === String(streamReq.xtreamId));
      }
      if (!hit && titles.length) hit = pick(list, titles, year);
      if (hit && (hit.series_id || hit.stream_id)) {
        const sid = hit.series_id || hit.stream_id;
        const info = await xtream(b, 'get_series_info', { series_id: sid }).catch(() => null);
        const eps = (info && info.episodes) || {};
        const seasonKey = String(streamReq.season || 1);
        const bag = eps[seasonKey] || eps[String(Number(seasonKey))] || [];
        const wantEp = Number(streamReq.episode || 1);
        const ep = (Array.isArray(bag) ? bag : []).find(
          (e) => Number(e.episode_num || e.episode) === wantEp,
        );
        if (ep && (ep.id || ep.stream_id)) {
          const eid = ep.id || ep.stream_id;
          const ext = (ep.container_extension || 'mp4').replace(/^\./, '');
          streams.push({
            name: b.name,
            title: (hit.name || titles[0] || 'Serie') + ' S' + seasonKey + 'E' + wantEp,
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
