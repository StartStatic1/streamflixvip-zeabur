// api/flixhub.js — fontes HD, indice rapido, evita CAM
const SUPABASE_URL =
  process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';

const cache = new Map();

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
function tokens(s) {
  return norm(s).split(' ').filter((w) => w.length > 2);
}
function hostOf(s) {
  return String(s.host || '').replace(/\/+$/, '');
}

const XTREAM_UAS = [
  'IPTVSmartersPro/1.0',
  'IPTVSmarters/1.0',
  'okhttp/4.12.0',
  'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/122.0.0.0 Mobile Safari/537.36',
  'VLC/3.0.20 LibVLC/3.0.20',
];

async function xtreamOnce(server, action, extra, ua) {
  const url = new URL(hostOf(server) + '/player_api.php');
  url.searchParams.set('username', server.user);
  url.searchParams.set('password', server.pass);
  if (action) url.searchParams.set('action', action);
  Object.entries(extra || {}).forEach(([k, v]) => {
    if (v != null) url.searchParams.set(k, String(v));
  });
  const ac = new AbortController();
  const t = setTimeout(() => ac.abort(), 12000);
  try {
    const r = await fetch(url.toString(), {
      signal: ac.signal,
      redirect: 'follow',
      headers: {
        'User-Agent': ua,
        Accept: 'application/json,text/plain,*/*',
        'Accept-Language': 'pt-BR,pt;q=0.9,en;q=0.8',
      },
    });
    const text = await r.text();
    if (!r.ok) {
      const err = new Error('HTTP ' + r.status + (text ? ' ' + text.slice(0, 80) : ''));
      err.status = r.status;
      throw err;
    }
    try {
      return JSON.parse(text);
    } catch (_) {
      throw new Error('Resposta sem JSON: ' + text.slice(0, 80));
    }
  } finally {
    clearTimeout(t);
  }
}

async function xtream(server, action, extra) {
  let last = null;
  for (const ua of XTREAM_UAS) {
    try {
      return await xtreamOnce(server, action, extra, ua);
    } catch (e) {
      last = e;
      if (e && e.status && e.status !== 403 && e.status !== 401 && e.status !== 406) break;
    }
  }
  throw last || new Error('Falha Xtream');
}

async function loadPack(id, token, key) {
  const r = await fetch(
    SUPABASE_URL + '/rest/v1/flixhub_packs?id=eq.' + encodeURIComponent(id) + '&is_active=eq.true&select=*',
    { headers: svc(key) },
  );
  const rows = await r.json();
  const row = Array.isArray(rows) && rows[0] ? rows[0] : null;
  if (!row) return null;
  if (String(row.access_token || '').trim() !== String(token || '').trim()) {
    const err = new Error('token');
    err.code = 401;
    throw err;
  }
  return row;
}

async function loadPackBySlug(slug, key) {
  if (!slug) return null;
  const r = await fetch(
    SUPABASE_URL +
      '/rest/v1/flixhub_packs?public_slug=eq.' +
      encodeURIComponent(slug) +
      '&public_enabled=eq.true&is_active=eq.true&select=*',
    { headers: svc(key) },
  );
  const rows = await r.json();
  return Array.isArray(rows) && rows[0] ? rows[0] : null;
}

function enabledServers(pack) {
  return (Array.isArray(pack.servers) ? pack.servers : [])
    .filter((s) => s && s.enabled !== false && s.host && s.user && s.pass)
    .sort((a, b) => (a.priority || 0) - (b.priority || 0));
}

function filterByCats(rows, ids) {
  if (!Array.isArray(ids) || !ids.length) return rows;
  const set = new Set(ids.map(String));
  return rows.filter((r) => set.has(String(r.category_id)));
}

function buildIndex(rows) {
  const byNorm = new Map();
  for (const item of rows) {
    const key = norm(item.name || item.title || '');
    if (!key) continue;
    if (!byNorm.has(key)) byNorm.set(key, []);
    byNorm.get(key).push(item);
  }
  return byNorm;
}

async function vodList(server, strict) {
  const catKey = (server.vod_category_ids || []).slice().sort().join(',');
  const k = 'fh:vod:' + hostOf(server) + ':' + server.user + ':' + catKey;
  const hit = cache.get(k);
  if (hit && Date.now() - hit.at < 45 * 60 * 1000) return hit;
  try {
    const raw = await xtream(server, 'get_vod_streams');
    let rows = Array.isArray(raw) ? raw : [];
    rows = filterByCats(rows, server.vod_category_ids);
    const pack = { at: Date.now(), rows, byNorm: buildIndex(rows) };
    cache.set(k, pack);
    return pack;
  } catch (e) {
    if (strict) throw e;
    return { at: Date.now(), rows: [], byNorm: new Map() };
  }
}

async function seriesList(server) {
  const catKey = (server.series_category_ids || []).slice().sort().join(',');
  const k = 'fh:ser:' + hostOf(server) + ':' + server.user + ':' + catKey;
  const hit = cache.get(k);
  if (hit && Date.now() - hit.at < 45 * 60 * 1000) return hit;
  const raw = await xtream(server, 'get_series').catch(() => []);
  let rows = Array.isArray(raw) ? raw : [];
  rows = filterByCats(rows, server.series_category_ids);
  const pack = { at: Date.now(), rows, byNorm: buildIndex(rows) };
  cache.set(k, pack);
  return pack;
}

const LOW_Q = /\b(cinema|cam|hdcam|hqcam|telesync|telecine|ts\b|tc\b|r5|scr|screener|camrip|hdts|hd-ts)\b/i;

function isLowQuality(name) {
  return LOW_Q.test(String(name || ''));
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
  else if (coverN >= 0.75 && nTok.length >= 2 && interN >= 2) score = 72;
  else if (t.includes(n) || n.includes(t)) score = 70;
  else return 0;
  if (queryYear && itemYear && queryYear === itemYear) score += 12;
  if (nTok.length >= 3 && tTok.length <= 2 && coverN < 0.99) return 0;
  if (nTok.length >= 4 && interN < 3) return 0;
  return score;
}

function pickFromPack(pack, titles, queryYear) {
  const list = (pack && pack.rows) || (Array.isArray(pack) ? pack : []);
  const byNorm = pack && pack.byNorm;
  const bag = (Array.isArray(titles) ? titles : [titles]).filter(Boolean);
  if (!bag.length || !list.length) return null;
  if (byNorm && byNorm.size) {
    for (const q of bag) {
      const key = norm(q);
      const exact = byNorm.get(key);
      if (exact && exact.length) {
        const good = exact.find((it) => !isLowQuality(it.name || it.title || ''));
        if (good) return good;
        if (exact[0]) return exact[0];
      }
    }
  }
  const qToks = new Set();
  for (const q of bag) tokens(q).forEach((t) => qToks.add(t));
  const candidates = [];
  for (const item of list) {
    const name = item.name || item.title || '';
    const tTok = tokens(name);
    let shared = 0;
    for (const t of tTok) if (qToks.has(t)) shared++;
    if (shared >= 2 || (shared >= 1 && tTok.length <= 3)) candidates.push(item);
    if (candidates.length > 800) break;
  }
  const pool = candidates.length ? candidates : list.slice(0, 2000);
  let best = null;
  let bestScore = 0;
  let bestLow = null;
  let bestLowScore = 0;
  for (const item of pool) {
    const name = item.name || item.title || '';
    const low = isLowQuality(name);
    for (const q of bag) {
      let score = scoreOne(name, q, queryYear);
      if (score < 70) continue;
      if (low) score -= 40;
      if (!low && score > bestScore) {
        bestScore = score;
        best = item;
      } else if (low && score > bestLowScore) {
        bestLowScore = score;
        bestLow = item;
      }
    }
  }
  if (best && bestScore >= 70) return best;
  if (bestLow && bestLowScore >= 70) return bestLow;
  return null;
}

function pick(listOrPack, titles, queryYear) {
  return pickFromPack(listOrPack, titles, queryYear);
}

function metaFromTmdb(j) {
  const titles = [j.title, j.name, j.original_title, j.original_name].filter(Boolean);
  const y = yearOf(j.release_date || j.first_air_date || '');
  return {
    titles,
    year: y,
    overview: j.overview || '',
    poster: j.poster_path ? 'https://image.tmdb.org/t/p/w500' + j.poster_path : null,
    backdrop: j.backdrop_path ? 'https://image.tmdb.org/t/p/w1280' + j.backdrop_path : null,
  };
}

async function tmdbTitle(tmdbId, kind) {
  const apiKey = tmdbKey();
  if (!apiKey || !tmdbId) return { titles: [], year: null };
  const path = kind === 'tv' ? '/tv/' + tmdbId : '/movie/' + tmdbId;
  try {
    const [pt, en] = await Promise.all([
      fetch('https://api.themoviedb.org/3' + path + '?api_key=' + encodeURIComponent(apiKey) + '&language=pt-BR').then((r) => (r.ok ? r.json() : null)).catch(() => null),
      fetch('https://api.themoviedb.org/3' + path + '?api_key=' + encodeURIComponent(apiKey) + '&language=en-US').then((r) => (r.ok ? r.json() : null)).catch(() => null),
    ]);
    const a = pt ? metaFromTmdb(pt) : { titles: [], year: null };
    const b = en ? metaFromTmdb(en) : { titles: [], year: null };
    const titles = [];
    for (const t of (a.titles || []).concat(b.titles || [])) {
      if (t && titles.indexOf(t) < 0) titles.push(t);
    }
    return {
      titles,
      year: a.year || b.year || null,
      overview: a.overview || b.overview || '',
      poster: a.poster || b.poster || null,
      backdrop: a.backdrop || b.backdrop || null,
    };
  } catch (_) {
    return { titles: [], year: null };
  }
}

async function imdbTitles(imdbId, kind) {
  const apiKey = tmdbKey();
  if (!apiKey || !imdbId) return { titles: [], year: null };
  const k = 'fh:imdb:' + imdbId + ':' + kind;
  const hit = cache.get(k);
  if (hit && Date.now() - hit.at < 12 * 60 * 60 * 1000) return hit.meta;
  try {
    const [pt, en] = await Promise.all([
      fetch(
        'https://api.themoviedb.org/3/find/' +
          encodeURIComponent(imdbId) +
          '?api_key=' +
          encodeURIComponent(apiKey) +
          '&external_source=imdb_id&language=pt-BR',
      )
        .then((r) => (r.ok ? r.json() : null))
        .catch(() => null),
      fetch(
        'https://api.themoviedb.org/3/find/' +
          encodeURIComponent(imdbId) +
          '?api_key=' +
          encodeURIComponent(apiKey) +
          '&external_source=imdb_id&language=en-US',
      )
        .then((r) => (r.ok ? r.json() : null))
        .catch(() => null),
    ]);
    function pickRow(j) {
      if (!j) return null;
      const rows = kind === 'tv' ? j.tv_results || [] : j.movie_results || [];
      return rows[0] || (j.tv_results && j.tv_results[0]) || (j.movie_results && j.movie_results[0]) || null;
    }
    const a = pickRow(pt) ? metaFromTmdb(pickRow(pt)) : { titles: [], year: null };
    const b = pickRow(en) ? metaFromTmdb(pickRow(en)) : { titles: [], year: null };
    const titles = [];
    for (const t of (a.titles || []).concat(b.titles || [])) {
      if (t && titles.indexOf(t) < 0) titles.push(t);
    }
    const meta = {
      titles,
      year: a.year || b.year || null,
      overview: a.overview || b.overview || '',
      poster: a.poster || b.poster || null,
      backdrop: a.backdrop || b.backdrop || null,
    };
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
  let season = null;
  let episode = null;
  if (parts[0] === 'tmdb') {
    tmdbId = parts[1];
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
  } else if (/^\d+$/.test(parts[0])) {
    tmdbId = parts[0];
    if (parts.length >= 3) {
      season = Number(parts[1]);
      episode = Number(parts[2]);
    }
  } else if (/^imdb$/i.test(parts[0]) && parts.length >= 2 && /^tt\d+$/i.test(parts[1])) {
    imdbId = parts[1];
    if (parts.length >= 4) {
      season = Number(parts[2]);
      episode = Number(parts[3]);
    }
  }
  return { type, id: raw, tmdbId, imdbId, season, episode };
}

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
  if (req.method === 'OPTIONS') {
    res.status(200).end();
    return;
  }
  if (req.method !== 'GET') {
    res.status(405).json({ error: 'GET only' });
    return;
  }

  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  const path = String(req.path || req.url || '').split('?')[0];

  let pack = null;
  let rest = null;
  const pub = path.match(/\/api\/flixhub\/p\/([^/]+)\/(.+)$/);
  const priv = path.match(/\/api\/flixhub\/([^/]+)\/([^/]+)\/(.+)$/);

  try {
    if (pub) {
      rest = pub[2];
      pack = await loadPackBySlug(pub[1], serviceKey);
      if (!pack) {
        res.status(404).json({ error: 'Pack publico inativo ou inexistente' });
        return;
      }
    } else if (priv && priv[1] !== 'p') {
      rest = priv[3];
      pack = await loadPack(priv[1], priv[2], serviceKey);
      if (!pack) {
        res.status(404).json({ error: 'Pack inativo ou inexistente' });
        return;
      }
    } else {
      res.status(401).json({
        error: 'Use /api/flixhub/ID/TOKEN/manifest.json ou /api/flixhub/p/SLUG/manifest.json',
      });
      return;
    }
  } catch (e) {
    if (e && e.code === 401) {
      res.status(401).json({ error: 'Token invalido' });
      return;
    }
    throw e;
  }

  const brand = pack.name || 'FlixHub';
  const servers = enabledServers(pack);

  if (rest === 'debug.json' || rest.startsWith('debug/')) {
    const out = {
      ok: true,
      name: brand,
      version: '1.3.9',
      servers: servers.map((s) => ({
        name: s.name,
        host: hostOf(s),
        user: s.user ? String(s.user).slice(0, 3) + '***' : null,
        hasPass: !!s.pass,
        enabled: s.enabled !== false,
        use_movies: s.use_movies !== false,
        use_series: s.use_series !== false,
        priority: s.priority,
        vod_cats: (s.vod_category_ids || []).length,
        series_cats: (s.series_category_ids || []).length,
      })),
      tests: [],
    };
    const testId = (rest.split('/')[1] || 'tt0137523').replace(/\.json$/, '');
    let meta = { titles: [], year: null };
    if (/^tt\d+$/i.test(testId)) meta = await imdbTitles(testId, 'movie');
    else if (/^tmdb/i.test(testId)) meta = await tmdbTitle(testId.replace(/^tmdb:?/i, ''), 'movie');
    out.resolved = meta;
    for (const server of servers) {
      const row = { name: server.name, host: hostOf(server) };
      try {
        const packV = await vodList(server, true);
        const list = packV.rows || [];
        row.vodCount = list.length;
        row.sample = list.slice(0, 3).map((x) => x.name || x.title || '?');
        if (meta.titles && meta.titles.length) {
          const hit = pick(packV, meta.titles, meta.year);
          row.match = hit ? hit.name || hit.title : null;
          row.matchId = hit ? hit.stream_id : null;
        }
      } catch (e) {
        row.error = String(e && e.message ? e.message : e);
        row.vodCount = 0;
      }
      out.tests.push(row);
    }
    res.status(200).json(out);
    return;
  }

  if (rest === 'manifest.json') {
    res.status(200).json({
      id: pack.public_slug
        ? 'com.streamflixvip.flixhub.' + String(pack.public_slug).slice(0, 12)
        : 'com.streamflixvip.flixhub.' + String(pack.id).replace(/-/g, '').slice(0, 12),
      name: brand,
      version: '1.3.9',
      description:
        'Varias fontes em HD para filmes e series. Simples, rapido e estavel no Stremio.',
      logo: 'https://www.streamflixvip.online/logo.png',
      background: 'https://www.streamflixvip.online/logo.png',
      resources: ['stream'],
      types: ['movie', 'series'],
      catalogs: [],
      idPrefixes: ['tt', 'tmdb'],
      behaviorHints: { configurable: false, configurationRequired: false },
    });
    return;
  }

  if (/^meta\//i.test(rest)) {
    res.status(200).json({ meta: null });
    return;
  }

  const streamReq = parseStreamPath(rest);
  if (streamReq) {
    const kind = streamReq.type === 'movie' ? 'movie' : 'tv';
    let meta = { titles: [], year: null };
    if (streamReq.imdbId) meta = await imdbTitles(streamReq.imdbId, kind);
    if (!meta.titles.length && streamReq.tmdbId) meta = await tmdbTitle(streamReq.tmdbId, kind);
    const titles = meta.titles || [];
    const year = meta.year || null;
    const streams = [];

    if (!titles.length) {
      res.status(200).json({ streams: [] });
      return;
    }

    await Promise.all(
      servers.map(async (server) => {
        try {
          if (streamReq.type === 'movie' && server.use_movies !== false) {
            const packV = await vodList(server);
            const hit = pick(packV, titles, year);
            if (hit && hit.stream_id) {
              const ext = (hit.container_extension || 'mp4').replace(/^\./, '');
              const label = server.name || brand;
              const qLabel = isLowQuality(hit.name || '')
                ? '⚠️ CAM / CINEMA (qualidade baixa)'
                : '🎯 FULL HD 1080p';
              streams.push({
                name: brand,
                title:
                  '🎬 ' +
                  (hit.name || titles[0] || 'Filme') +
                  '\n' +
                  (server.color || '⚡') +
                  ' ' +
                  label +
                  '\n' +
                  qLabel,
                url:
                  hostOf(server) +
                  '/movie/' +
                  server.user +
                  '/' +
                  server.pass +
                  '/' +
                  hit.stream_id +
                  '.' +
                  ext,
                behaviorHints: { bingeGroup: 'flixhub-' + (server.id || label) },
                _priority: Number(server.priority) || 0,
              });
            }
          }
          if (streamReq.type === 'series' && server.use_series !== false) {
            const packS = await seriesList(server);
            const hit = pick(packS, titles, year);
            if (hit && (hit.series_id || hit.stream_id)) {
              const sid = hit.series_id || hit.stream_id;
              const info = await xtream(server, 'get_series_info', { series_id: sid }).catch(() => null);
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
                const label = server.name || brand;
                streams.push({
                  name: brand,
                  title:
                    '📺 ' +
                    (hit.name || titles[0] || 'Serie') +
                    ' S' +
                    seasonKey +
                    'E' +
                    wantEp +
                    '\n' +
                    (server.color || '⚡') +
                    ' ' +
                    label +
                    '\n🎯 FULL HD',
                  url:
                    hostOf(server) +
                    '/series/' +
                    server.user +
                    '/' +
                    server.pass +
                    '/' +
                    eid +
                    '.' +
                    ext,
                  behaviorHints: { bingeGroup: 'flixhub-' + (server.id || label) },
                  _priority: Number(server.priority) || 0,
                });
              }
            }
          }
        } catch (_) {}
      }),
    );

    streams.sort((a, b) => {
      const la = /CAM|CINEMA/i.test(a.title || '') ? 1 : 0;
      const lb = /CAM|CINEMA/i.test(b.title || '') ? 1 : 0;
      if (la !== lb) return la - lb;
      return (a._priority || 0) - (b._priority || 0);
    });
    for (const s of streams) delete s._priority;

    if (streams.length) {
      const supportUrl = process.env.FLIXHUB_SUPPORT_URL || 'https://pay.infinitepay.io/streamflixvip';
      streams.push({
        name: '❤️ APOIE O PROJETO',
        title:
          'Seu apoio mantém o FlixHub no ar 🙏\n💎 PIX ou cartão via InfinitePay\nToque para abrir o pagamento',
        externalUrl: supportUrl,
        behaviorHints: { notWebReady: true },
      });
    }

    res.status(200).json({ streams });
    return;
  }

  res.status(200).json({ streams: [], metas: [] });
};
