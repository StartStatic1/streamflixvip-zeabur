// lib/stremio-addons.js
// Busca streams de add-ons no protocolo Stremio (manifest + /stream/...).

const SUPABASE_URL =
  process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';

const MAX_PER_ADDON = 8;
const MAX_TOTAL_ADDON = 56; // teto seguro: com R2/banco o painel fica em ~60
const ADDON_PRIORITY = 10;
const FETCH_TIMEOUT_MS = 4000;
// Prazo total da coleta (o app antigo desiste em 15s; o resto continua em
// segundo plano e entra no cache, entao a 2a abertura ja vem completa).
const COLLECT_DEADLINE_MS = 11000;
// FlixHub roda no proprio servidor e consulta varios paineis (ate 8s por painel).
const SLOW_SELF_ADDON_TIMEOUT_MS = 9500;
const MANIFEST_TIMEOUT_MS = 3500;
const MANIFEST_TTL_MS = 6 * 60 * 60 * 1000;
const MANIFEST_FAIL_TTL_MS = 10 * 60 * 1000;
const ADDONS_TTL_MS = 60 * 1000;
const ID_TTL_MS = 24 * 60 * 60 * 1000;
const ID_NULL_TTL_MS = 30 * 60 * 1000;
const RESULT_TTL_MS = 10 * 60 * 1000;
const RESULT_EMPTY_TTL_MS = 2 * 60 * 1000;
const CANDIDATE_WAVE = 3;
const BREAKER_FAILS = 3;
const BREAKER_PAUSE_MS = 10 * 60 * 1000;

// Cache simples com TTL + dedupe de chamadas em andamento.
function memoAsync(fn, keyFn, ttlOk, ttlNull) {
  const map = new Map();
  return function memoized(...args) {
    const key = keyFn(...args);
    const now = Date.now();
    const hit = map.get(key);
    if (hit && hit.exp > now) return hit.p;
    const p = Promise.resolve().then(() => fn(...args));
    const entry = { exp: now + ttlOk, p };
    map.set(key, entry);
    p.then(
      (v) => { if (v == null && map.get(key) === entry) entry.exp = Date.now() + ttlNull; },
      () => { if (map.get(key) === entry) map.delete(key); },
    );
    if (map.size > 5000) {
      for (const k of map.keys()) { map.delete(k); if (map.size <= 4000) break; }
    }
    return p;
  };
}

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
        { Accept: 'application/json', 'User-Agent': 'StreamFlixVIP/1.0 (addon-client)' },
        extraHeaders || {},
      ),
    });
    if (!r.ok) throw new Error(`HTTP ${r.status}`);
    return await r.json();
  } finally {
    clearTimeout(t);
  }
}

async function loadActiveAddonsRaw(serviceKey) {
  const url =
    `${SUPABASE_URL}/rest/v1/stremio_addons?is_active=eq.true` +
    `&select=id,name,manifest_url,base_url,priority&order=priority.asc`;
  const controller = new AbortController();
  const t = setTimeout(() => controller.abort(), 6000);
  try {
    const r = await fetch(url, { headers: sbHeaders(serviceKey), signal: controller.signal });
    if (!r.ok) {
      const body = await r.text();
      if (r.status === 404 || body.includes('does not exist')) return [];
      console.warn('[addons] load', r.status, body.slice(0, 120));
      return null;
    }
    const rows = await r.json();
    return Array.isArray(rows) ? rows : [];
  } catch (e) {
    console.warn('[addons] load', e && e.message);
    return null;
  } finally {
    clearTimeout(t);
  }
}

// Lista de add-ons ativos: cache de 60s (antes ia no Supabase a cada pedido).
let addonsCache = { at: 0, rows: null, inflight: null };
async function loadActiveAddons(serviceKey) {
  const now = Date.now();
  if (addonsCache.rows && now - addonsCache.at < ADDONS_TTL_MS) return addonsCache.rows;
  if (addonsCache.inflight) return addonsCache.inflight;
  const inflight = loadActiveAddonsRaw(serviceKey).then((rows) => {
    if (rows === null) {
      // erro: usa a lista anterior (se houver) e tenta de novo no proximo pedido
      addonsCache.inflight = null;
      return addonsCache.rows || [];
    }
    addonsCache = { at: Date.now(), rows, inflight: null };
    return rows;
  });
  addonsCache.inflight = inflight;
  return inflight;
}

async function resolveImdbIdRaw(tmdbId, mediaType) {
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

async function anilistQuery(payload) {
  const controller = new AbortController();
  const t = setTimeout(() => controller.abort(), 4000);
  try {
    const r = await fetch('https://graphql.anilist.co', {
      method: 'POST',
      signal: controller.signal,
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(payload),
    });
    if (!r.ok) return null;
    const j = await r.json();
    return j && j.data && j.data.Media ? j.data.Media : null;
  } catch (e) {
    console.warn('[addons] anilist', e.message);
    return null;
  } finally {
    clearTimeout(t);
  }
}

async function lookupKitsu(title) {
  if (!title) return null;
  try {
    const kitsu = await fetchJson(
      'https://kitsu.io/api/edge/anime?filter[text]=' + encodeURIComponent(title) + '&page[limit]=1',
      4000,
      { Accept: 'application/vnd.api+json' },
    );
    const row = kitsu && Array.isArray(kitsu.data) ? kitsu.data[0] : null;
    return row && row.id ? String(row.id) : null;
  } catch (_) {
    return null;
  }
}

async function resolveAnimeIdsRaw(tmdbId, mediaType) {
  const apiKey = process.env.TMDB_API_KEY;
  if (!tmdbId) return null;
  const kind = mediaType === 'movie' ? 'movie' : 'tv';
  let title = null;
  let looksAnime = mediaType === 'anime';
  if (apiKey) {
    try {
      const details = await fetchJson(
        'https://api.themoviedb.org/3/' + kind + '/' + tmdbId +
          '?api_key=' + encodeURIComponent(apiKey) + '&language=en-US',
        4000,
      );
      title = details.name || details.title || details.original_name || details.original_title;
      const lang = details.original_language || '';
      const genreIds = (details.genres || []).map((g) => Number(g.id));
      looksAnime = looksAnime || lang === 'ja' || genreIds.includes(16);
    } catch (e) {
      console.warn('[addons] tmdb anime', e.message);
    }
  }
  let anilistId = null;
  let malId = null;
  if (!title && mediaType === 'anime') {
    const m = await anilistQuery({
      query: 'query ($id: Int) { Media(id: $id, type: ANIME) { id idMal title { romaji english } } }',
      variables: { id: Number(tmdbId) },
    });
    if (m && m.id) {
      anilistId = String(m.id);
      malId = m.idMal ? String(m.idMal) : null;
      title = (m.title && (m.title.english || m.title.romaji)) || null;
      looksAnime = true;
    }
  } else if (title) {
    const m = await anilistQuery({
      query: 'query ($s: String) { Media(search: $s, type: ANIME) { id idMal title { romaji english } } }',
      variables: { s: title },
    });
    if (m && m.id) {
      anilistId = String(m.id);
      malId = m.idMal ? String(m.idMal) : null;
    }
  }
  if (!title || !looksAnime) return null;
  const kitsuId = await lookupKitsu(title);
  if (!kitsuId && !anilistId && !malId) return null;
  return { title: String(title), kitsuId: kitsuId || null, anilistId: anilistId || null, malId: malId || null };
}

const idKey = (tmdbId, mediaType) => String(mediaType) + ':' + String(tmdbId);
// IDs (IMDb, anime, ano) quase nunca mudam: cache de 24h, 30 min quando vazio.
const resolveImdbId = memoAsync(resolveImdbIdRaw, idKey, ID_TTL_MS, ID_NULL_TTL_MS);
const resolveAnimeIds = memoAsync(resolveAnimeIdsRaw, idKey, ID_TTL_MS, ID_NULL_TTL_MS);
const resolveTmdbYear = memoAsync(resolveTmdbYearRaw, idKey, ID_TTL_MS, ID_NULL_TTL_MS);

function buildStreamIds(mediaType, tmdbId, imdbId, season, episode, animeIds) {
  const ids = [];
  function pushAnime(prefix, idVal) {
    if (!idVal) return;
    ids.push({ type: 'anime', id: prefix + ':' + idVal });
    if (episode != null) {
      ids.push({ type: 'anime', id: prefix + ':' + idVal + ':' + episode });
      ids.push({ type: 'series', id: prefix + ':' + idVal + ':' + (season || 1) + ':' + episode });
    }
  }
  if (animeIds) {
    pushAnime('anilist', animeIds.anilistId);
    pushAnime('kitsu', animeIds.kitsuId);
    pushAnime('mal', animeIds.malId);
  }
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
  return ids;
}

function isHttpStreamUrl(u) {
  if (!u || typeof u !== 'string') return false;
  const s = u.trim();
  if (!/^https?:\/\//i.test(s)) return false;
  if (s.startsWith('magnet:')) return false;
  if (s.length < 12 || s.length > 4000) return false;
  if (/\s/.test(s)) return false;
  if (/\.(html?|php)(\?|$)/i.test(s) && !/\.m3u8|\.mpd|\.mp4|\.mkv|\.ts/i.test(s)) return false;
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
  if (/\b(2160p?|4k|uhd|full\s*uhd)\b/.test(t)) return '4K';
  if (/\b(1080p?|full\s*hd|fhd)\b/.test(t)) return '1080p';
  if (/\b(720p?|hd)\b/.test(t)) return '720p';
  if (/\b(480p?|360p?|sd)\b/.test(t)) return 'SD';
  return null;
}


function detectSize(text, stream) {
  const blob = String(text || '');
  const m = blob.match(/(\d+(?:[.,]\d+)?)\s*(GiB|GB|MiB|MB)\b/i);
  if (m) return m[1].replace(',', '.') + ' ' + m[2].toUpperCase().replace('GIB', 'GB').replace('MIB', 'MB');
  const bytes = stream && stream.behaviorHints && stream.behaviorHints.videoSize;
  const n = Number(bytes);
  if (Number.isFinite(n) && n > 0) {
    if (n >= 1073741824) return (n / 1073741824).toFixed(1) + ' GB';
    if (n >= 1048576) return Math.round(n / 1048576) + ' MB';
  }
  return null;
}

function detectOrigin(stream, addonName) {
  const name = String(addonName || '').toLowerCase();
  // Fontes xtream/painel/flixhub: origin nao ajuda (vira ID feio)
  if (/flixhub|xtream|cineduo|goldvip|sander|svent|bridge|m3u/.test(name)) return null;

  const hints = (stream && stream.behaviorHints) || {};
  function sn(s) { return String(s || '').replace(/\s+/g, ' ').trim(); }

  const rawBits = [
    stream && stream.name,
    stream && stream.title,
    stream && stream.description,
  ].map(sn).filter(Boolean);

  // bingeGroup costuma ser ID tecnico — so usa se parecer nome legivel
  const bg = sn(hints.bingeGroup || stream && stream.bingeGroup);
  if (bg && !/^-?s?\d+$/i.test(bg) && !/^\d+$/.test(bg) && bg.length >= 3 && bg.length <= 20) {
    rawBits.push(bg);
  }

  function isJunk(c) {
    if (!c || c.length < 2 || c.length > 22) return true;
    if (/^\d+$/.test(c)) return true;
    if (/^-?s\d+/i.test(c)) return true;           // -s1790108136706
    if (/^s\d{8,}/i.test(c)) return true;
    if (/stream|addon|http|https|null|undefined/i.test(c)) return true;
    if (/^[\[\(].*[\]\)]$/.test(c) && c.length > 18) return true;
    return false;
  }

  function tidy(s) {
    let x = sn(s);
    x = x.split(/[\n\r]/)[0];
    x = x.replace(/[\u00b7\u2022|]+/g, ' ');
    // tira qualidade/audio/codec
    x = x.replace(/\b(4k|uhd|2160p|1080p|720p|480p|360p|fhd|hd|sd|web-?dl|blu-?ray|bluray|dublado|dublada|legendado|legendada|dual|audio|pt-?br|subs?|dub|hevc|x264|x265|h\.?264|h\.?265|aac|ac3|dts)\b/ig, ' ');
    x = x.replace(/\b(server|servidor)\s*\d+\b/ig, ' ');
    x = x.replace(/\bflixhub\b/ig, ' ');
    // tira ids -s123...
    x = x.replace(/-?s\d{6,}/gi, ' ');
    x = x.replace(/\s+/g, ' ').trim();
    return x;
  }

  const hostLow = name.replace(/^.*\//, '').trim();

  for (const bit of rawBits) {
    const first = bit.split(/[\n\u00b7\u2022|,]/)[0];
    let c = tidy(first);
    if (isJunk(c)) continue;
    if (c.toLowerCase() === hostLow) continue;
    // limpa prefixos tipo [TB+] 
    c = c.replace(/^\[[^\]]+\]\s*/g, '').trim();
    if (isJunk(c)) continue;
    // origem curta e legivel
    if (c.length >= 2 && c.length <= 18) {
      // nao repetir nome do addon (Aniscraper / Aniscrap / Animsub)
      const cl = c.toLowerCase().replace(/[^a-z0-9]/g, '');
      const nl = hostLow.replace(/[^a-z0-9]/g, '');
      if (cl && nl && (cl === nl || cl.includes(nl) || nl.includes(cl))) continue;
      if (/^(aniscrap|aniscraper|animsub|animesub|nyaa|tordb|torrents?)$/i.test(c)) continue;
      if (/animesub|animsub|aniscrap|\btb\+?\b|torbox|\[tb/i.test(c)) continue;
      return c.slice(0, 16);
    }
  }
  return null;
}

function detectAudio(text, addonName) {
  const t = String(text || '').toLowerCase();
  const name = String(addonName || '').toLowerCase();

  // Português BR explícito
  const ptDub = /dublad[oa]s?|dual\s*[aá]udio|pt-?br\s*dub|\bdub\s*pt|\bpt\s*dub|áudio\s*pt|audio\s*pt-?br|\bpt-?br\b.*\bdub|\bdub\b.*\bpt-?br/.test(t);
  const leg = /legendad[oa]s?|soft\s*subs?|hard\s*subs?|pt-?br\s*subs?|\bsubtitled\b|\bsubtitles?\b|\bleg\b|\bsub\b/.test(t)
    || (/\bsubs?\b|\blegs?\b/.test(t) && !/\bdub/.test(t));
  const enDub = /\bdubbed\b|\beng(?:lish)?\s*dub\b|\bdub\s*eng\b|\ben\s*dub\b|\baudio\s*:?\s*eng|\beng(?:lish)?\s*audio/.test(t);

  // Addons de anime/scrape: nunca confiar em "dub" solto
  const isAnimeScrap = /aniscrap|ani[\s._-]?scrap|animesub|anime[\s._-]?sub|animetsu|consumet|enime/.test(name);

  if (isAnimeScrap) {
    if (ptDub) return 'Dublado';
    if (leg) return 'Legendado';
    if (enDub) return 'EN';
    // texto genérico "dub" sem PT → trata como EN, não Dublado
    if (/\bdub\b|\bdubbed\b/.test(t)) return 'EN';
    return null;
  }

  if (ptDub && !leg) return 'Dublado';
  if (leg && !ptDub) return 'Legendado';
  if (ptDub && leg) return 'Dublado';
  if (enDub && !ptDub) return 'EN';
  if (/anime\s*sub|animesub|subs?\s*br|legend/.test(name) && !ptDub) return 'Legendado';
  if (/\b(original|multi\s*audio|truehd|atmos)\b/.test(t) && !ptDub) return 'Original';
  return null;
}

function shortAddonName(name) {
  let n = String(name || 'Addon').trim();
  n = n.replace(/^Addon\s*[\u00b7\u2022\-]\s*/i, '').trim();
  if (!n) return 'Addon';
  if (n.length > 22) n = n.slice(0, 21) + '\u2026';
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


function yearOfBlob(s) {
  const m = String(s || '').match(/\b((?:19|20)\d{2})\b/);
  return m ? Number(m[1]) : null;
}
function streamMatchesQuery(stream, queryYear, season, episode, addon) {
  const blob = [stream && stream.name, stream && stream.title, stream && stream.description]
    .filter(Boolean)
    .join(' ');
  const y = yearOfBlob(blob);
  const isFlix = /flixhub/i.test(String((addon && (addon.name || addon.id || addon.manifestUrl || addon.manifest_url || addon.url)) || '') + ' ' + blob);
  // FlixHub/Xtream: so rejeita quando o titulo TRAZ ano e ele diverge.
  // Sem ano no nome, deixa passar (senao some quase tudo do painel).
  if (isFlix && queryYear) {
    if (y && Number(queryYear) !== Number(y)) return false;
  }
  if (queryYear && y && Number(queryYear) !== Number(y)) return false;
  if (season != null && episode != null) {
    const se = blob.match(/\bS\s*0*(\d+)\s*[EExx]\s*0*(\d+)\b/i) || blob.match(/\b(\d{1,2})x(\d{1,3})\b/i);
    if (se && (Number(se[1]) !== Number(season) || Number(se[2]) !== Number(episode))) return false;
    const epOnly = blob.match(/\b(?:EP|E|episodio|episode)\s*0*(\d{1,3})\b/i);
    if (!se && epOnly && Number(epOnly[1]) !== Number(episode)) return false;
  }
  return true;
}
async function resolveTmdbYearRaw(tmdbId, mediaType) {
  const apiKey = process.env.TMDB_API_KEY;
  if (!apiKey || !tmdbId) return null;
  try {
    const kind = mediaType === 'tv' ? 'tv' : 'movie';
    const j = await fetchJson('https://api.themoviedb.org/3/' + kind + '/' + tmdbId + '?api_key=' + encodeURIComponent(apiKey), 4000);
    const d = String((j && (j.release_date || j.first_air_date)) || '');
    const m = d.match(/^(\d{4})/);
    return m ? Number(m[1]) : null;
  } catch (_) {
    return null;
  }
}


function wrapProxyHeaders(url, stream) {
  if (!url) return url;
  const hints = stream && stream.behaviorHints && stream.behaviorHints.proxyHeaders;
  const reqH = hints && hints.request;
  let ref = reqH && (reqH.Referer || reqH.referer);
  try {
    const host = new URL(url).hostname || '';
    if (/hakunaymatata\.com$/i.test(host) || /mzfi\.me$/i.test(host)) {
      ref = ref || 'https://mzfi.me/';
    }
  } catch (_) {}
  if (!ref) return url;
  try {
    const u = new URL(url);
    const r = new URL(ref);
    if (u.hostname === r.hostname || u.hostname.endsWith('.' + r.hostname)) return url;
  } catch (_) {}
  if (/stream-proxy\?/.test(url)) return url;
  const base = String(process.env.PUBLIC_BASE_URL || 'https://streamflixvip.online').replace(/\/+$/, '');
  return base + '/api/stream-proxy?url=' + encodeURIComponent(url) + '&referer=' + encodeURIComponent(ref);
}

function streamToSource(stream, addonName, addonPriority) {
  return streamToSourceDetailed(stream, addonName, addonPriority).source;
}

function looksNumberedServer(name) {
  const n = String(name || '');
  return /server\s*\d+/i.test(n) || /servidor\s*\d+/i.test(n);
}
function pickStreamDisplayName(stream) {
  const title = stripNoise(stream && stream.title);
  const name = stripNoise(stream && stream.name);
  const blob = stripNoise([name, title, stream && stream.description].filter(Boolean).join(' '));
  // Prefixo do pack (FlixHub, StreamHub, etc.) — vem do stream.name no flixhub.js
  const brand = (name && name.length >= 2 && name.length <= 24 && !/server|servidor/i.test(name))
    ? name.replace(/\s+/g, ' ').trim()
    : null;
  // Captura "SmartHelp Server 2", "FlixHub Server 2", ou so "Server 2"
  const mFull = blob.match(/([A-Za-z0-9][A-Za-z0-9._-]{0,24}\s+)?(?:server|servidor)\s*\d+/i);
  if (mFull) {
    let s = mFull[0].replace(/\s+/g, ' ').trim();
    s = s.replace(/\bservidor\b/ig, 'Server').replace(/\bserver\b/ig, 'Server');
    // Se veio so "Server N", usa a marca do pack
    if (/^Server\s*\d+$/i.test(s)) {
      const prefix = brand || 'FlixHub';
      s = prefix + ' Server ' + (s.match(/\d+/) || [''])[0];
    }
    return s.slice(0, 48);
  }
  if (looksNumberedServer(name)) return name;
  return name || title;
}

function streamToSourceDetailed(stream, addonName, addonPriority) {
  const rawUrl = (stream.url || '').trim();
  const url = wrapProxyHeaders(rawUrl, stream);
  if (!isHttpStreamUrl(rawUrl)) return { source: null, reason: rawUrl ? 'url_not_http_playable' : 'missing_url' };
  if (stream.infoHash || (stream.sources && !url)) return { source: null, reason: 'torrent_infohash_not_resolved' };
  const streamName = pickStreamDisplayName(stream);
  const raw = stripNoise([stream.name, stream.title, stream.description, stream.quality].filter(Boolean).join(' '));
  const q = detectQuality(raw) || detectQuality(url);
  const a = detectAudio(raw, addonName);
  const origin = detectOrigin(stream, addonName);
  const size = detectSize(raw, stream);
  const host = shortAddonName(addonName);
  let label;
  const packBrand = (function () {
    const n = stripNoise(stream && stream.name);
    if (n && n.length >= 2 && n.length <= 24 && !/server|servidor/i.test(n)) return n;
    const h = shortAddonName(addonName);
    if (h && !/^addon$/i.test(h)) return h;
    return null;
  })();
  if (looksNumberedServer(streamName)) {
    let core = String(streamName || '');
    if (packBrand && core.toLowerCase().indexOf(packBrand.toLowerCase()) !== 0) {
      core = packBrand + ' \u00b7 ' + core;
    }
    label = core.slice(0, 52);
    if (q && core.toLowerCase().indexOf(String(q).toLowerCase()) < 0) label += ' \u00b7 ' + q;
    if (a && !/dublad|legendad/i.test(core)) label += ' \u00b7 ' + a;
  } else {
    const parts = [host];
    if (q) parts.push(q);
    if (a) parts.push(a);
    if (!q && !a) {
      const tail = raw.split(/[\u00b7\u2022\|\-\u2013]/).pop();
      const provider = stripNoise(tail || '').slice(0, 18);
      parts.push(provider && provider.length >= 2 ? provider : 'Stream');
    }
    label = parts.join(' \u00b7 ');
  }
  return { source: {
    source_url: url,
    source_label: label,
    priority: (function () {
    let p = Number.isFinite(Number(addonPriority)) ? Number(addonPriority) : ADDON_PRIORITY;
    p = p * 100;
    const sm = String(typeof streamName !== 'undefined' ? streamName : '').match(/(?:server|servidor)\s*(\d+)/i);
    if (sm) p = p + Number(sm[1]);
    return p;
  })(),
    _score: streamScore(q, a),
    meta: { quality: q || null, audio: a || null, origin: origin || null, size: size || null, description: (function () {
      const bits = [stream && stream.title, stream && stream.description, stream && stream.name].map(function (s) { return stripNoise(s); }).filter(Boolean);
      const best = bits.sort(function (a, b) { return b.length - a.length; })[0] || '';
      return best ? best.slice(0, 220) : null;
    })() },
  }, reason: null };
}

// --- Manifest: so pergunta streams a quem tem "stream" e so com IDs aceitos ---
function addonManifestUrl(addon, base) {
  return normalizeManifestUrl(addon.manifest_url) || (base ? base + '/manifest.json' : null);
}

async function addonStreamInfoRaw(url) {
  try {
    const data = await fetchJson(url, MANIFEST_TIMEOUT_MS);
    if (!data || typeof data !== 'object' || !Array.isArray(data.resources)) return { known: false };
    let hasStream = false;
    let hasSubtitles = false;
    let streamRes = null;
    for (const r of data.resources) {
      const rn = typeof r === 'string' ? r : (r && typeof r === 'object' ? r.name : null);
      if (rn === 'subtitles') hasSubtitles = true;
      if (r === 'stream') hasStream = true;
      else if (r && typeof r === 'object' && r.name === 'stream') { hasStream = true; streamRes = r; }
    }
    let idPrefixes = null;
    if (streamRes && Array.isArray(streamRes.idPrefixes)) idPrefixes = streamRes.idPrefixes;
    else if (Array.isArray(data.idPrefixes)) idPrefixes = data.idPrefixes;
    idPrefixes = (idPrefixes || []).map((x) => String(x || '')).filter(Boolean);
    return { known: true, hasStream, hasSubtitles, idPrefixes: idPrefixes.length ? idPrefixes : null };
  } catch (_) {
    return null; // desconhecido: cache curto, segue o fluxo antigo
  }
}
const addonStreamInfoCached = memoAsync(addonStreamInfoRaw, (u) => u, MANIFEST_TTL_MS, MANIFEST_FAIL_TTL_MS);
async function addonStreamInfo(addon, base) {
  const url = addonManifestUrl(addon, base);
  if (!url) return { known: false };
  return (await addonStreamInfoCached(url)) || { known: false };
}

// --- Disjuntor: add-on que nao responde 3x seguidas fica 10 min de fora ---
const addonHealth = new Map();
function addonKey(addon) {
  return String(addon.id || addon.manifest_url || addon.base_url || addon.name || '');
}
function addonPaused(addon) {
  const h = addonHealth.get(addonKey(addon));
  return !!(h && h.until > Date.now());
}
function recordAddonHealth(addon, responded) {
  const key = addonKey(addon);
  if (responded) { addonHealth.delete(key); return; }
  const h = addonHealth.get(key) || { fails: 0, until: 0 };
  h.fails += 1;
  if (h.fails >= BREAKER_FAILS) {
    h.fails = 0;
    h.until = Date.now() + BREAKER_PAUSE_MS;
    console.warn('[addons] sem resposta ' + BREAKER_FAILS + 'x, pausado 10 min:', addon.name || key);
  }
  addonHealth.set(key, h);
}

function requestTimeoutFor(base) {
  return /\/api\/flixhub\//i.test(base) ? SLOW_SELF_ADDON_TIMEOUT_MS : FETCH_TIMEOUT_MS;
}

// responded=true quando o add-on respondeu algo coerente (2xx, 400 ou 404).
async function fetchCandidate(base, c, timeoutMs) {
  const streamUrl = `${base}/stream/${encodeURIComponent(c.type)}/${encodeURIComponent(c.id)}.json`;
  const controller = new AbortController();
  const t = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const r = await fetch(streamUrl, {
      signal: controller.signal,
      headers: { Accept: 'application/json', 'User-Agent': 'StreamFlixVIP/1.0 (addon-client)' },
    });
    if (!r.ok) {
      try { await r.arrayBuffer(); } catch (_) {}
      return { responded: r.status === 404 || r.status === 400, streams: null };
    }
    const data = await r.json();
    return { responded: true, streams: Array.isArray(data && data.streams) ? data.streams : [] };
  } catch (_) {
    return { responded: false, streams: null };
  } finally {
    clearTimeout(t);
  }
}

// Add-ons "agregadores" (AIOStreams etc.): mostra TUDO o que vier, sem corte,
// sem reordenar e sem filtro de ano/episodio; rotulo descritivo a partir do texto.
function isRawAddon(addon) {
  const t = String((addon && (String(addon.name || '') + ' ' + String(addon.manifest_url || '') + ' ' + String(addon.base_url || ''))) || '');
  return /aio\s*streams|aiostreams/i.test(t);
}
const RAW_MAX = 40;
const RAW_TIMEOUT_MS = 10500; // agregador com debrid demora ~8s

function rawLabel(stream, addonName) {
  const clean = (x) => String(x || '').replace(/[\u200b-\u200d\u2060\ufeff]/g, '').replace(/\u00a0/g, ' ');
  const lines = (clean(stream && stream.name) + '\n' + clean(stream && (stream.title || stream.description)))
    .split(/\n+/).map((l) => l.replace(/[\u2605\u2606\u26a1\u270e\u25a3\u266c\u266f\u25c8\u26c9\u2713\u2726\u2727\u3008\u3009]/g, ' ').replace(/\s+/g, ' ').trim()).filter(Boolean);
  const blob = lines.join(' ');
  const bits = [shortAddonName(addonName)];
  const q = detectQuality(blob);
  if (q) bits.push(q);
  const src = (blob.match(/\b(remux|blu-?ray|web-?dl|webrip|hdtv|dvdrip)\b/i) || [])[0];
  if (src) bits.push(src.replace(/bluray/i, 'BluRay'));
  const a = detectAudio(blob, addonName);
  if (a) bits.push(a);
  const size = (blob.match(/(\d+(?:[.,]\d+)?)\s*(GB|MB|GiB|MiB)\b/i) || [])[0];
  if (size) bits.push(size);
  const prov = (blob.match(/\[(TB|RD|AD|PM|DL|ED|OC)\+?\]\s*([A-Za-z0-9 ._-]{2,18})/) || []);
  if (prov[2]) bits.push(prov[2].trim());
  return bits.join(' \u00b7 ').slice(0, 90);
}

async function fetchStreamsFromAddon(addon, mediaType, tmdbId, imdbId, season, episode, animeIds, queryYear) {
  const base = (addon.base_url || baseFromManifestUrl(addon.manifest_url) || '').replace(/\/+$/, '');
  if (!base) return [];
  if (addonPaused(addon)) return [];
  let candidates = buildStreamIds(mediaType, tmdbId, imdbId, season, episode, animeIds);
  const info = await addonStreamInfo(addon, base);
  if (info.known && !info.hasStream) return []; // add-on so de legenda/catalogo
  if (info.known && info.idPrefixes) {
    candidates = candidates.filter((c) => info.idPrefixes.some((p) => String(c.id).startsWith(p)));
  }
  if (!candidates.length) return [];
  const timeoutMs = isRawAddon(addon) ? RAW_TIMEOUT_MS : requestTimeoutFor(base);
  const out = [];
  const seenUrl = new Set();
  let responded = false;
  // Ondas de ate 3 IDs em paralelo; respeita a ordem (o 1o ID com resultado vence).
  for (let i = 0; i < candidates.length && !out.length; i += CANDIDATE_WAVE) {
    const wave = candidates.slice(i, i + CANDIDATE_WAVE).map((c) => fetchCandidate(base, c, timeoutMs));
    for (const pending of wave) {
      const r = await pending;
      if (r.responded) responded = true;
      if (!r.streams) continue;
      for (const s of r.streams) {
        const raw = isRawAddon(addon);
        if (!raw && !streamMatchesQuery(s, queryYear, season, episode, addon)) continue;
        const src = streamToSource(s, addon.name || 'Addon', addon.priority);
        if (!src) continue;
        if (raw) src.source_label = rawLabel(s, addon.name || 'Addon');
        if (seenUrl.has(src.source_url)) continue;
        seenUrl.add(src.source_url);
        out.push(src);
      }
      if (out.length) break;
    }
  }
  recordAddonHealth(addon, responded);
  if (isRawAddon(addon)) {
    return out.slice(0, RAW_MAX).map(({ source_url, source_label, priority, meta }) => ({ source_url, source_label, priority, meta }));
  }
  const numbered = out.some((s) => looksNumberedServer(s.source_label));
  if (!numbered) out.sort((a, b) => (b._score || 0) - (a._score || 0));
  // Dublado ganha no _score; sem isso o corte em MAX_PER_ADDON jogava fora todo
  // o Legendado quando o add-on manda muitos dublados. Garante ate 3 de cada audio.
  let picked = out.slice(0, MAX_PER_ADDON);
  const legOut = out.filter((s) => s.meta && s.meta.audio === 'Legendado');
  const legIn = picked.filter((s) => s.meta && s.meta.audio === 'Legendado').length;
  if (legIn < 3 && legOut.length > legIn) {
    const extra = legOut.filter((s) => picked.indexOf(s) < 0).slice(0, 3 - legIn);
    picked = picked.slice(0, Math.max(0, MAX_PER_ADDON - extra.length)).concat(extra);
  }
  return picked.map(({ source_url, source_label, priority, meta }) => ({ source_url, source_label, priority, meta }));
}

function mergeAddonSlots(slots) {
  // Intercala os add-ons (1 de cada por vez) para um agregador grande nao ocupar
  // o painel inteiro; corta em MAX_TOTAL_ADDON e garante ate 6 Legendados.
  const lists = (slots || []).filter(Array.isArray).map((l) => l.slice());
  const all = [];
  const seen = new Set();
  let more = true;
  for (let i = 0; more; i++) {
    more = false;
    for (const l of lists) {
      if (i >= l.length) continue;
      more = true;
      const s = l[i];
      if (!s || !s.source_url || seen.has(s.source_url)) continue;
      seen.add(s.source_url);
      all.push(s);
    }
  }
  if (all.length <= MAX_TOTAL_ADDON) return all;
  const isLeg = (s) => !!(s && s.meta && s.meta.audio === 'Legendado');
  let picked = all.slice(0, MAX_TOTAL_ADDON);
  const legIn = picked.filter(isLeg).length;
  if (legIn < 6) {
    const extra = all.slice(MAX_TOTAL_ADDON).filter(isLeg).slice(0, 6 - legIn);
    if (extra.length) picked = picked.slice(0, MAX_TOTAL_ADDON - extra.length).concat(extra);
  }
  return picked;
}

// Cache de resultado por titulo/episodio + uma unica coleta em andamento por chave.
const addonResultCache = new Map();
const addonInflight = new Map();

function startAddonCollect(serviceKey, tmdbId, mediaType, season, episode, key) {
  const job = { slots: [] };
  job.done = (async () => {
    try {
      const addons = await loadActiveAddons(serviceKey);
      if (!addons.length) return [];
      const [imdbId, animeIds, queryYear] = await Promise.all([
        resolveImdbId(tmdbId, mediaType),
        resolveAnimeIds(tmdbId, mediaType),
        resolveTmdbYear(tmdbId, mediaType),
      ]);
      job.slots = new Array(addons.length).fill(null);
      await Promise.all(addons.map((a, i) =>
        fetchStreamsFromAddon(a, mediaType, tmdbId, imdbId, season, episode, animeIds, queryYear)
          .then((v) => { job.slots[i] = v; }, () => { job.slots[i] = []; })));
      const merged = mergeAddonSlots(job.slots);
      addonResultCache.set(key, {
        exp: Date.now() + (merged.length ? RESULT_TTL_MS : RESULT_EMPTY_TTL_MS),
        value: merged,
      });
      if (addonResultCache.size > 3000) {
        for (const k of addonResultCache.keys()) { addonResultCache.delete(k); if (addonResultCache.size <= 2500) break; }
      }
      return merged;
    } catch (e) {
      console.warn('[addons] coleta', e && e.message);
      return mergeAddonSlots(job.slots);
    } finally {
      addonInflight.delete(key);
    }
  })();
  return job;
}

function clearAddonCaches() {
  addonsCache = { at: 0, rows: null, inflight: null };
  addonResultCache.clear();
  addonHealth.clear();
}

/**
 * Igual a collectAddonSources, mas diz se a coleta terminou.
 * { sources, complete }: complete=false quando o prazo acabou e ainda ha add-ons
 * respondendo (o resto continua em segundo plano; um novo pedido pega mais).
 */
async function collectAddonSourcesProgress(serviceKey, tmdbId, mediaType, season, episode, deadlineMs) {
  const deadline = Number(deadlineMs) > 0 ? Number(deadlineMs) : COLLECT_DEADLINE_MS;
  const key = [mediaType, tmdbId, season == null ? '' : season, episode == null ? '' : episode].join(':');
  const hit = addonResultCache.get(key);
  if (hit && hit.exp > Date.now()) return { sources: hit.value.slice(), complete: true };
  let job = addonInflight.get(key);
  if (!job) {
    job = startAddonCollect(serviceKey, tmdbId, mediaType, season, episode, key);
    addonInflight.set(key, job);
  }
  let timer;
  const timeout = new Promise((resolve) => { timer = setTimeout(() => resolve(null), deadline); });
  const full = await Promise.race([job.done, timeout]);
  clearTimeout(timer);
  // No prazo: devolve o que ja chegou; o resto termina em segundo plano e vai pro cache.
  if (full) return { sources: full.slice(), complete: true };
  return { sources: mergeAddonSlots(job.slots).slice(), complete: false };
}

async function collectAddonSources(serviceKey, tmdbId, mediaType, season, episode, deadlineMs) {
  return (await collectAddonSourcesProgress(serviceKey, tmdbId, mediaType, season, episode, deadlineMs)).sources;
}

async function diagnoseAddonSources(serviceKey, tmdbId, mediaType, season, episode, addonId) {
  const addons = await loadActiveAddons(serviceKey);
  const selected = addonId ? addons.filter((a) => String(a.id) === String(addonId)) : addons;
  const imdbId = await resolveImdbId(tmdbId, mediaType);
  const animeIds = await resolveAnimeIds(tmdbId, mediaType);
  const candidates = buildStreamIds(mediaType, tmdbId, imdbId, season, episode, animeIds);
  return Promise.all(selected.map(async (addon) => {
    const started = Date.now();
    const base = (addon.base_url || baseFromManifestUrl(addon.manifest_url) || '').replace(/\/+$/, '');
    const result = { id: addon.id, name: addon.name, manifest_url: addon.manifest_url, base_url: base,
      ok: false, elapsed_ms: 0, candidates, attempts: [], accepted: [], rejected: [], error: null };
    if (!base) {
      result.error = 'base_url_missing';
      result.elapsed_ms = Date.now() - started;
      return result;
    }
    for (const c of candidates) {
      const url = `${base}/stream/${encodeURIComponent(c.type)}/${encodeURIComponent(c.id)}.json`;
      const attempt = { type: c.type, id: c.id, url, http_status: null, stream_count: 0, error: null };
      try {
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS);
        const r = await fetch(url, { signal: controller.signal,
          headers: { Accept: 'application/json', 'User-Agent': 'StreamFlixVIP/1.0 (addon-diagnostics)' } });
        clearTimeout(timer);
        attempt.http_status = r.status;
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        const data = await r.json();
        const streams = Array.isArray(data && data.streams) ? data.streams : [];
        attempt.stream_count = streams.length;
        for (const stream of streams) {
          const converted = streamToSourceDetailed(stream, addon.name || 'Addon', addon.priority);
          if (converted.source) result.accepted.push(converted.source);
          else result.rejected.push({ name: stream.name || stream.title || null, url: stream.url || null,
            infoHash: stream.infoHash || null, reason: converted.reason });
        }
      } catch (e) {
        attempt.error = e && e.name === 'AbortError' ? 'timeout' : (e.message || 'request_failed');
      }
      result.attempts.push(attempt);
      if (result.accepted.length) break;
    }
    result.accepted = result.accepted.slice(0, MAX_PER_ADDON);
    result.ok = result.accepted.length > 0;
    result.elapsed_ms = Date.now() - started;
    return result;
  }));
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
  return { manifest_url: url, base_url: base, name: String(name), resources, types: data.types || [], catalogs: Array.isArray(data.catalogs) ? data.catalogs.length : 0, raw: data };
}

module.exports = {
  normalizeManifestUrl,
  baseFromManifestUrl,
  loadActiveAddons,
  collectAddonSources,
  collectAddonSourcesProgress,
  clearAddonCaches,
  addonStreamInfo,
  diagnoseAddonSources,
  resolveAnimeIds,
  probeManifest,
};
