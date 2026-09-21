// lib/torbox.js — resolve magnet/infoHash em URL HTTP via TorBox (cached only)
const SUPABASE_URL =
  process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';

const TB_BASE = 'https://api.torbox.app/v1/api';
const cache = new Map();
const CACHE_MS = 20 * 60 * 1000;
const MAX_RESOLVE = 4;

function svc(key) {
  return { apikey: key, Authorization: 'Bearer ' + key };
}

function hashFromMagnetOrUrl(raw) {
  const s = String(raw || '').trim();
  const m = s.match(/btih:([a-fA-F0-9]{40}|[a-zA-Z2-7]{32})/i);
  if (m) return m[1].toLowerCase();
  if (/^[a-f0-9]{40}$/i.test(s)) return s.toLowerCase();
  return null;
}

async function loadConfig(serviceKey) {
  const envKey = String(process.env.TORBOX_API_KEY || '').trim();
  let row = null;
  if (serviceKey) {
    try {
      const r = await fetch(
        SUPABASE_URL + '/rest/v1/torbox_settings?id=eq.1&select=api_key,is_enabled',
        { headers: svc(serviceKey) },
      );
      const rows = await r.json();
      row = Array.isArray(rows) && rows[0] ? rows[0] : null;
    } catch (_) {}
  }
  const apiKey = String((row && row.api_key) || envKey || '').trim();
  const enabled = row ? row.is_enabled !== false && !!apiKey : !!apiKey;
  return { apiKey, enabled };
}

async function tb(apiKey, method, path, { query, form } = {}) {
  const url = new URL(TB_BASE + path);
  Object.entries(query || {}).forEach(([k, v]) => {
    if (v != null && v !== '') url.searchParams.set(k, String(v));
  });
  const headers = { Authorization: 'Bearer ' + apiKey, Accept: 'application/json' };
  const opts = { method, headers };
  if (form) {
    const body = new URLSearchParams();
    Object.entries(form).forEach(([k, v]) => {
      if (v != null) body.set(k, String(v));
    });
    headers['Content-Type'] = 'application/x-www-form-urlencoded';
    opts.body = body.toString();
  }
  const ac = new AbortController();
  const t = setTimeout(() => ac.abort(), 18000);
  try {
    const r = await fetch(url.toString(), { ...opts, signal: ac.signal });
    const text = await r.text();
    let json = null;
    try {
      json = JSON.parse(text);
    } catch (_) {
      json = { success: false, detail: text.slice(0, 180) };
    }
    if (!r.ok) {
      const err = new Error((json && (json.detail || json.error)) || 'HTTP ' + r.status);
      err.status = r.status;
      err.body = json;
      throw err;
    }
    return json;
  } finally {
    clearTimeout(t);
  }
}

async function testKey(apiKey) {
  const j = await tb(apiKey, 'GET', '/user/me');
  const data = (j && j.data) || j || {};
  return {
    ok: j && j.success !== false,
    email: data.email || data.user || null,
    plan: data.plan || data.premium || data.user_plan || null,
  };
}

function pickVideoFile(files) {
  const list = Array.isArray(files) ? files : [];
  const video = list.filter((f) => {
    const n = String((f && (f.name || f.short_name || f.path)) || '').toLowerCase();
    return /\.(mkv|mp4|avi|m4v|webm|ts|mov)$/i.test(n);
  });
  const bag = video.length ? video : list;
  bag.sort((a, b) => Number(b.size || 0) - Number(a.size || 0));
  return bag[0] || null;
}

async function checkCached(apiKey, hashes) {
  const uniq = [...new Set(hashes.filter(Boolean))];
  if (!uniq.length) return {};
  const j = await tb(apiKey, 'GET', '/torrents/checkcached', {
    query: { hash: uniq.join(','), format: 'object', list_files: 'true' },
  });
  const data = (j && j.data) || {};
  return data && typeof data === 'object' ? data : {};
}

async function findExisting(apiKey, hash) {
  try {
    const j = await tb(apiKey, 'GET', '/torrents/mylist', { query: { bypass_cache: 'true' } });
    const list = Array.isArray(j && j.data) ? j.data : [];
    const hit = list.find((t) => String(t.hash || t.info_hash || '').toLowerCase() === hash);
    return hit || null;
  } catch (_) {
    return null;
  }
}

async function createIfCached(apiKey, hash) {
  const magnet = 'magnet:?xt=urn:btih:' + hash;
  const j = await tb(apiKey, 'POST', '/torrents/createtorrent', {
    form: { magnet, add_only_if_cached: 'true', seed: '3' },
  });
  return (j && j.data) || j;
}

async function requestDl(apiKey, torrentId, fileId) {
  const j = await tb(apiKey, 'GET', '/torrents/requestdl', {
    query: {
      token: apiKey,
      torrent_id: torrentId,
      file_id: fileId,
      redirect: 'false',
    },
  });
  const data = j && j.data;
  if (typeof data === 'string' && /^https?:\/\//i.test(data)) return data;
  if (data && typeof data === 'object') {
    return data.url || data.download_url || data.link || data.cdn || null;
  }
  return null;
}

async function resolveOne(apiKey, hash, label) {
  const ck = 'tb:' + hash;
  const hit = cache.get(ck);
  if (hit && Date.now() - hit.at < CACHE_MS && hit.url) return hit.source;

  let torrent = await findExisting(apiKey, hash);
  if (!torrent) {
    const created = await createIfCached(apiKey, hash);
    const tid = created && (created.torrent_id || created.id);
    if (!tid) return null;
    torrent = { id: tid, files: created.files || [] };
  }

  const tid = torrent.id || torrent.torrent_id;
  const files = torrent.files || torrent.file_list || [];
  const file = pickVideoFile(files);
  const fid = file ? file.id : 0;
  const url = await requestDl(apiKey, tid, fid);
  if (!url || !/^https?:\/\//i.test(url)) return null;

  const source = {
    source_url: url,
    source_label: 'TorBox \u00b7 ' + String(label || 'Torrent').replace(/^TorBox\s*\u00b7\s*/i, ''),
    priority: 12,
  };
  cache.set(ck, { at: Date.now(), url, source });
  return source;
}

async function resolveTorrentSources(serviceKey, candidates) {
  const cfg = await loadConfig(serviceKey);
  if (!cfg.enabled || !cfg.apiKey) return [];

  const items = [];
  const seen = new Set();
  for (const c of candidates || []) {
    const hash = c.info_hash || hashFromMagnetOrUrl(c.source_url);
    if (!hash || seen.has(hash)) continue;
    seen.add(hash);
    items.push({ hash, label: c.source_label || 'Torrent' });
    if (items.length >= MAX_RESOLVE) break;
  }
  if (!items.length) return [];

  try {
    const cachedMap = await checkCached(
      cfg.apiKey,
      items.map((i) => i.hash),
    );
    const cachedHashes = new Set(
      Object.keys(cachedMap || {}).map((k) => String(k).toLowerCase()),
    );
    const work = items.filter((i) => cachedHashes.has(i.hash) || cachedMap[i.hash]);

    const out = [];
    for (const item of work) {
      try {
        const src = await resolveOne(cfg.apiKey, item.hash, item.label);
        if (src) out.push(src);
      } catch (e) {
        console.warn('[torbox] hash', item.hash.slice(0, 8), e.message);
      }
    }
    return out;
  } catch (e) {
    console.warn('[torbox] resolve', e.message);
    return [];
  }
}

module.exports = {
  loadConfig,
  testKey,
  hashFromMagnetOrUrl,
  resolveTorrentSources,
};
