// lib/stremio-addons.js
// Busca streams de add-ons no protocolo Stremio (manifest + /stream/...).

const SUPABASE_URL =
  process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';

const MAX_PER_ADDON = 8;
const MAX_TOTAL_ADDON = 24;
const ADDON_PRIORITY = 10;
const FETCH_TIMEOUT_MS = 4000;
const COLLECT_DEADLINE_MS = 8000;

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

async function loadActiveAddons(serviceKey) {
  const url =
    `${SUPABASE_URL}/rest/v1/stremio_addons?is_active=eq.true` +
    `&select=id,name,manifest_url,base_url,priority&order=priority.asc`;
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

async function resolveAnimeIds(tmdbId, mediaType) {
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
  if (isFlix && queryYear) {
    if (!y || Number(queryYear) !== Number(y)) return false;
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
async function resolveTmdbYear(tmdbId, mediaType) {
  const apiKey = process.env.TMDB_API_KEY;
  if (!apiKey || !tmdbId) return null;
  try {
    const kind = mediaType === 'tv' ? 'tv' : 'movie';
    const r = await fetch('https://api.themoviedb.org/3/' + kind + '/' + tmdbId + '?api_key=' + apiKey);
    const j = await r.json();
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
  const m = blob.match(/((?:flixhub\s+)?(?:server|servidor)\s*\d+)/i);
  if (m) {
    let s = m[1].replace(/\s+/g, ' ').trim();
    if (/^server\s*\d+/i.test(s)) s = 'FlixHub ' + s.replace(/^server/i, 'Server');
    else if (/^servidor\s*\d+/i.test(s)) s = 'FlixHub Server ' + (s.match(/\d+/) || [''])[0];
    else s = s.replace(/flixhub/ig, 'FlixHub').replace(/\bserver\b/ig, 'Server');
    return s;
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
  if (looksNumberedServer(streamName)) {
    label = streamName.slice(0, 42);
    if (q && !streamName.toLowerCase().includes(q.toLowerCase())) label += ' \u00b7 ' + q;
    if (a && !/dublad|legendad/i.test(streamName)) label += ' \u00b7 ' + a;
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

async function fetchStreamsFromAddon(addon, mediaType, tmdbId, imdbId, season, episode, animeIds, queryYear) {
  const base = (addon.base_url || baseFromManifestUrl(addon.manifest_url) || '').replace(/\/+$/, '');
  if (!base) return [];
  const candidates = buildStreamIds(mediaType, tmdbId, imdbId, season, episode, animeIds);
  const out = [];
  const seenUrl = new Set();
  for (const c of candidates) {
    const streamUrl = `${base}/stream/${encodeURIComponent(c.type)}/${encodeURIComponent(c.id)}.json`;
    try {
      const data = await fetchJson(streamUrl, FETCH_TIMEOUT_MS);
      const streams = Array.isArray(data && data.streams) ? data.streams : [];
      for (const s of streams) {
        if (!streamMatchesQuery(s, queryYear, season, episode, addon)) continue;
        const src = streamToSource(s, addon.name || 'Addon', addon.priority);
        if (!src) continue;
        if (seenUrl.has(src.source_url)) continue;
        seenUrl.add(src.source_url);
        out.push(src);
      }
      if (out.length) break;
    } catch (_) {}
  }
  const numbered = out.some((s) => looksNumberedServer(s.source_label));
  if (!numbered) out.sort((a, b) => (b._score || 0) - (a._score || 0));
  return out.slice(0, MAX_PER_ADDON).map(({ source_url, source_label, priority, meta }) => ({ source_url, source_label, priority, meta }));
}

function withDeadline(promise, ms) {
  return Promise.race([promise, new Promise((resolve) => setTimeout(() => resolve([]), ms))]);
}

async function collectAddonSources(serviceKey, tmdbId, mediaType, season, episode, deadlineMs) {
  const addons = await loadActiveAddons(serviceKey);
  if (!addons.length) return [];
  const imdbId = await resolveImdbId(tmdbId, mediaType);
  const animeIds = await resolveAnimeIds(tmdbId, mediaType);
  const queryYear = await resolveTmdbYear(tmdbId, mediaType);
  const results = await Promise.allSettled(
    addons.map((a) => withDeadline(fetchStreamsFromAddon(a, mediaType, tmdbId, imdbId, season, episode, animeIds, queryYear), Number(deadlineMs) > 0 ? Number(deadlineMs) : COLLECT_DEADLINE_MS)),
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
  diagnoseAddonSources,
  resolveAnimeIds,
  probeManifest,
};
