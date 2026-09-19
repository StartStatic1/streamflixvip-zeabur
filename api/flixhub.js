// api/flixhub.js — add-on agregador estilo UnioFlix (stream + meta, 0 catalogos)
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
  const t = setTimeout(() => ac.abort(), 20000);
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

function enabledServers(pack) {
  return (Array.isArray(pack.servers) ? pack.servers : [])
    .filter((s) => s && s.enabled !== false && s.host && s.user && s.pass)
    .sort((a, b) => (a.priority || 0) - (b.priority || 0));
}

async function vodList(server, strict) {
  const k = 'fh:vod:' + server.host + ':' + server.user;
  const hit = cache.get(k);
  if (hit && Date.now() - hit.at < 25 * 60 * 1000) return hit.rows;
  try {
    const raw = await xtream(server, 'get_vod_streams');
    const rows = Array.isArray(raw) ? raw : [];
    cache.set(k, { at: Date.now(), rows });
    return rows;
  } catch (e) {
    if (strict) throw e;
    return [];
  }
}

async function seriesList(server) {
  const k = 'fh:ser:' + server.host + ':' + server.user;
  const hit = cache.get(k);
  if (hit && Date.now() - hit.at < 25 * 60 * 1000) return hit.rows;
  const raw = await xtream(server, 'get_series').catch(() => []);
  const rows = Array.isArray(raw) ? raw : [];
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
  else if (coverN >= 0.75 && nTok.length >= 2 && interN >= 2) score = 72;
  else if (t.includes(n) || n.includes(t)) score = 70;
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
  return bestScore >= 70 ? best : null;
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
      fetch('https://api.themoviedb.org/3' + path + '?api_key=' + encodeURIComponent(apiKey) + '&language=pt-BR').then((r) => r.ok ? r.json() : null).catch(() => null),
      fetch('https://api.themoviedb.org/3' + path + '?api_key=' + encodeURIComponent(apiKey) + '&language=en-US').then((r) => r.ok ? r.json() : null).catch(() => null),
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
      fetch('https://api.themoviedb.org/3/find/' + encodeURIComponent(imdbId) + '?api_key=' + encodeURIComponent(apiKey) + '&external_source=imdb_id&language=pt-BR').then((r) => r.ok ? r.json() : null).catch(() => null),
      fetch('https://api.themoviedb.org/3/find/' + encodeURIComponent(imdbId) + '?api_key=' + encodeURIComponent(apiKey) + '&external_source=imdb_id&language=en-US').then((r) => r.ok ? r.json() : null).catch(() => null),
    ]);
    function pickRow(j) {
      if (!j) return null;
      const rows = kind === 'tv' ? (j.tv_results || []) : (j.movie_results || []);
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
  } else if (/^\d+$/.test(parts[0]) && parts.length >= 3) {
    tmdbId = parts[0];
    season = Number(parts[1]);
    episode = Number(parts[2]);
  }
  return { type, id: raw, tmdbId, imdbId, season, episode };
}

function parseMeta(rest) {
  const m = String(rest || '').match(/^meta\/([^/]+)\/(.+)\.json$/i);
  if (!m) return null;
  return { type: m[1].toLowerCase(), id: decodeURIComponent(m[2]) };
}

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
  if (req.method === 'OPTIONS') { res.status(200).end(); return; }
  if (req.method !== 'GET') { res.status(405).json({ error: 'GET only' }); return; }

  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  const path = String(req.path || req.url || '').split('?')[0];
  const m = path.match(/\/api\/flixhub\/([^/]+)\/([^/]+)\/(.+)$/);
  if (!m) {
    res.status(401).json({ error: 'Use /api/flixhub/ID/TOKEN/manifest.json' });
    return;
  }
  const id = m[1];
  const token = m[2];
  const rest = m[3];

  let pack;
  try {
    pack = await loadPack(id, token, serviceKey);
  } catch (e) {
    if (e && e.code === 401) {
      res.status(401).json({ error: 'Token invalido' });
      return;
    }
    throw e;
  }
  if (!pack) {
    res.status(404).json({ error: 'Pack inativo ou inexistente' });
    return;
  }

  const brand = pack.name || 'FlixHub';
  const servers = enabledServers(pack);

  if (rest === 'debug.json' || rest.startsWith('debug/')) {
    const out = {
      ok: true,
      name: brand,
      version: '1.1.0',
      servers: servers.map((s) => ({
        name: s.name,
        host: hostOf(s),
        user: s.user ? String(s.user).slice(0, 3) + '***' : null,
        hasPass: !!s.pass,
        enabled: s.enabled !== false,
        use_movies: s.use_movies !== false,
        use_series: s.use_series !== false,
        priority: s.priority,
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
        const list = await vodList(server, true);
        row.vodCount = list.length;
        row.sample = list.slice(0, 3).map((x) => x.name || x.title || '?');
        if (meta.titles && meta.titles.length) {
          const hit = pick(list, meta.titles, meta.year);
          row.match = hit ? (hit.name || hit.title) : null;
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
      id: 'streamflix.flixhub.' + String(pack.id).slice(0, 8),
      name: brand,
      version: '1.1.0',
      description:
        'Agregador StreamFlixVIP — multiplos servidores em um add-on (estilo UnioFlix). Use com Nuvio Catalog / AIOMetadata.',
      logo: 'https://www.streamflixvip.online/favicon.ico',
      background: 'https://www.streamflixvip.online/favicon.ico',
      resources: ['stream', 'meta'],
      types: ['movie', 'series'],
      catalogs: [],
      idPrefixes: ['tt', 'tmdb'],
    });
    return;
  }

  const metaReq = parseMeta(rest);
  if (metaReq) {
    const kind = metaReq.type === 'movie' ? 'movie' : 'tv';
    const raw = String(metaReq.id || '');
    const parts = raw.split(':');
    let meta = { titles: [], year: null };
    if (/^tt\d+$/i.test(parts[0])) meta = await imdbTitles(parts[0], kind);
    else if (parts[0] === 'tmdb' && parts[1]) meta = await tmdbTitle(parts[1], kind);
    else if (/^\d+$/.test(parts[0])) meta = await tmdbTitle(parts[0], kind);

    if (!meta.titles || !meta.titles.length) {
      res.status(200).json({ meta: null });
      return;
    }
    res.status(200).json({
      meta: {
        id: raw,
        type: metaReq.type === 'movie' ? 'movie' : 'series',
        name: meta.titles[0],
        poster: meta.poster || undefined,
        background: meta.backdrop || undefined,
        description: meta.overview || meta.titles[0],
        releaseInfo: meta.year ? String(meta.year) : undefined,
      },
    });
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
            const list = await vodList(server);
            const hit = pick(list, titles, year);
            if (hit && hit.stream_id) {
              const ext = (hit.container_extension || 'mp4').replace(/^\./, '');
              const label = server.name || brand;
              const color = server.color || '⚡';
              streams.push({
                name: brand,
                title: color + ' ' + label + '\n🎬 ' + (hit.name || titles[0] || 'Filme') + '\n🎯 FULL HD 1080p',
                url: hostOf(server) + '/movie/' + server.user + '/' + server.pass + '/' + hit.stream_id + '.' + ext,
                behaviorHints: { bingeGroup: 'flixhub-' + (server.id || label) },
              });
            }
          }
          if (streamReq.type === 'series' && server.use_series !== false) {
            const list = await seriesList(server);
            const hit = pick(list, titles, year);
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
                const color = server.color || '⚡';
                streams.push({
                  name: brand,
                  title: color + ' ' + label + '\n📺 ' + (hit.name || titles[0] || 'Serie') + ' S' + seasonKey + 'E' + wantEp + '\n🎯 FULL HD',
                  url: hostOf(server) + '/series/' + server.user + '/' + server.pass + '/' + eid + '.' + ext,
                  behaviorHints: { bingeGroup: 'flixhub-' + (server.id || label) },
                });
              }
            }
          }
        } catch (_) {}
      }),
    );

    const orderTitles = servers.map((s) => (s.color || '⚡') + ' ' + (s.name || brand));
    streams.sort((a, b) => {
      const ia = orderTitles.findIndex((t) => (a.title || '').indexOf(t) === 0);
      const ib = orderTitles.findIndex((t) => (b.title || '').indexOf(t) === 0);
      return (ia < 0 ? 99 : ia) - (ib < 0 ? 99 : ib);
    });

    if (streams.length) {
      streams.push({
        name: '❤️ APOIE O PROJETO',
        title: 'Seu apoio mantém o FlixHub no ar 🙏\n💎 PIX / Infinity Pay — StreamFlixVIP\nToque para contribuir',
        externalUrl: 'https://www.streamflixvip.online',
        url: 'https://www.streamflixvip.online',
        behaviorHints: { notWebReady: true },
      });
    }

    res.status(200).json({ streams });
    return;
  }

  res.status(200).json({ streams: [], metas: [] });
};
