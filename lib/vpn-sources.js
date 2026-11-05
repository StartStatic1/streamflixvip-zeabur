// Nomes de pontes/fontes marcadas com requires_vpn, para carimbar
// source_label no payload de filmes/séries. Coluna ausente = lista vazia
// (e/ou arquivo local vpn-sources.json).
const fs = require('fs');
const path = require('path');

const SUPABASE_URL =
  process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';

const TTL_MS = 60 * 1000;
let cache = { at: 0, names: null, index: null, inflight: null };

function flagsPath() {
  if (process.env.VPN_FLAGS_PATH) return process.env.VPN_FLAGS_PATH;
  const dir = process.env.XTREAM_CLIENT_CACHE_DIR || '/root/streamflix-data/xtream-client-cache';
  return path.join(path.dirname(dir), 'vpn-sources.json');
}

function sbHeaders(serviceKey) {
  return {
    apikey: serviceKey,
    Authorization: `Bearer ${serviceKey}`,
  };
}

function labelKey(label) {
  return String(label || '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/streamflix|stremflix|addon/g, ' ')
    .replace(/[^a-z0-9]+/g, ' ')
    .trim();
}

function labelNeedsVpn(label, names) {
  const key = labelKey(label);
  if (!key || !names || !names.size) return false;
  for (const n of names) {
    const nk = labelKey(n);
    if (!nk || nk.length < 3) continue;
    if (key === nk) return true;
    if (key.startsWith(nk + ' ') || key.endsWith(' ' + nk) || key.includes(' ' + nk + ' ')) {
      return true;
    }
  }
  return false;
}

function readLocalFlags() {
  const empty = { names: [], bridgeIds: [], liveIds: [] };
  try {
    const raw = fs.readFileSync(flagsPath(), 'utf8');
    const j = JSON.parse(raw);
    if (!j || typeof j !== 'object') return empty;
    return {
      names: Array.isArray(j.names) ? j.names.map(String) : [],
      bridgeIds: Array.isArray(j.bridgeIds) ? j.bridgeIds.map(String) : [],
      liveIds: Array.isArray(j.liveIds) ? j.liveIds.map(String) : [],
    };
  } catch (_) {
    return empty;
  }
}

function writeLocalFlags(data) {
  const file = flagsPath();
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const payload = {
    names: Array.from(new Set((data.names || []).map(String).filter(Boolean))),
    bridgeIds: Array.from(new Set((data.bridgeIds || []).map(String).filter(Boolean))),
    liveIds: Array.from(new Set((data.liveIds || []).map(String).filter(Boolean))),
  };
  fs.writeFileSync(file, JSON.stringify(payload, null, 2));
  return payload;
}

function persistVpnFlag({ type, id, name, value }) {
  const cur = readLocalFlags();
  const names = new Set(cur.names);
  const bridgeIds = new Set(cur.bridgeIds);
  const liveIds = new Set(cur.liveIds);
  const idSet = type === 'live' ? liveIds : bridgeIds;
  const sid = id != null ? String(id) : '';
  const sname = String(name || '').trim();
  if (value) {
    if (sid) idSet.add(sid);
    if (sname) names.add(sname);
  } else {
    if (sid) idSet.delete(sid);
    if (sname) names.delete(sname);
  }
  const written = writeLocalFlags({
    names: Array.from(names),
    bridgeIds: Array.from(bridgeIds),
    liveIds: Array.from(liveIds),
  });
  clearVpnCache();
  return written;
}

async function fetchTableNames(serviceKey, table, extraSelect) {
  const names = new Set();
  const ids = new Set();
  const rowIds = new Set();
  try {
    const select = extraSelect ? `id,name,${extraSelect}` : 'id,name';
    const r = await fetch(
      `${SUPABASE_URL}/rest/v1/${table}?requires_vpn=eq.true&select=${select}`,
      { headers: sbHeaders(serviceKey) },
    );
    if (!r.ok) return { names, ids, rowIds };
    const rows = await r.json();
    for (const row of Array.isArray(rows) ? rows : []) {
      const n = String((row && row.name) || '').trim();
      if (n) names.add(n);
      if (row && row.id) rowIds.add(String(row.id));
      if (extraSelect && row && row[extraSelect]) ids.add(String(row[extraSelect]));
    }
  } catch (_) {}
  return { names, ids, rowIds };
}

function mergeLocal(index) {
  const local = readLocalFlags();
  for (const n of local.names) if (n) index.names.add(n);
  for (const id of local.bridgeIds) if (id) index.rowIds.add(id);
  for (const id of local.liveIds) if (id) index.rowIds.add(id);
  return index;
}

async function fetchVpnIndex(serviceKey) {
  const names = new Set();
  const ids = new Set();
  const rowIds = new Set();
  if (serviceKey) {
    const [bridges, live] = await Promise.all([
      fetchTableNames(serviceKey, 'iptv_bridges', 'addon_id'),
      fetchTableNames(serviceKey, 'live_tv_sources'),
    ]);
    for (const n of bridges.names) names.add(n);
    for (const n of live.names) names.add(n);
    for (const id of bridges.ids) ids.add(id);
    for (const id of live.ids) ids.add(id);
    for (const id of bridges.rowIds) rowIds.add(id);
    for (const id of live.rowIds) rowIds.add(id);
  }
  return mergeLocal({ names, ids, rowIds });
}

async function loadVpnIndex(serviceKey) {
  const now = Date.now();
  if (cache.names && cache.index && now - cache.at < TTL_MS) return cache.index;
  if (cache.inflight) return cache.inflight;
  const inflight = fetchVpnIndex(serviceKey)
    .then((index) => {
      cache = { at: Date.now(), names: index.names, index, inflight: null };
      return index;
    })
    .catch(() => {
      cache.inflight = null;
      return cache.index || mergeLocal({ names: new Set(), ids: new Set(), rowIds: new Set() });
    });
  cache.inflight = inflight;
  return inflight;
}

async function loadVpnSourceNames(serviceKey) {
  const index = await loadVpnIndex(serviceKey);
  return index.names;
}

function stampRequiresVpn(sources, names) {
  return (sources || []).map((s) => {
    if (!s || typeof s !== 'object') return s;
    if (s.requires_vpn === true) return Object.assign({}, s, { requires_vpn: true });
    return Object.assign({}, s, { requires_vpn: labelNeedsVpn(s.source_label, names) });
  });
}

function attachVpnFlags(addons, index) {
  const ids = (index && index.ids) || new Set();
  const names = (index && index.names) || new Set();
  for (const a of addons || []) {
    if (!a) continue;
    const id = a.id != null ? String(a.id) : '';
    a.requires_vpn = !!(id && ids.has(id)) || labelNeedsVpn(a.name, names);
  }
  return addons;
}

function applyVpnToRows(rows, index) {
  const names = (index && index.names) || new Set();
  const rowIds = (index && index.rowIds) || new Set();
  for (const row of rows || []) {
    if (!row) continue;
    if (row.requires_vpn === true) continue;
    const id = row.id != null ? String(row.id) : '';
    row.requires_vpn = !!(id && rowIds.has(id)) || labelNeedsVpn(row.name, names);
  }
  return rows;
}

function clearVpnCache() {
  cache = { at: 0, names: null, index: null, inflight: null };
}

module.exports = {
  labelKey,
  labelNeedsVpn,
  loadVpnIndex,
  loadVpnSourceNames,
  stampRequiresVpn,
  attachVpnFlags,
  applyVpnToRows,
  persistVpnFlag,
  readLocalFlags,
  flagsPath,
  clearVpnCache,
};
