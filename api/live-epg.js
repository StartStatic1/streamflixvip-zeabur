// api/live-epg.js
// Grade agora/a seguir (XMLTV BR + PT). Nao bloqueia a lista de canais.
const zlib = require('zlib');
const { promisify } = require('util');
const gunzip = promisify(zlib.gunzip);

const EPG_TTL_MS = 30 * 60 * 1000;
let epgCache = { at: 0, programmes: [] };

const FEEDS = [
  'https://epg.lat/files/br.xml.gz',
  'https://epg.lat/files/pt.xml.gz',
];

function normalize(s) {
  return String(s || '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, ' ')
    .trim();
}

function parseXmlTvTime(raw) {
  const m = String(raw || '').match(/^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})/);
  if (!m) return null;
  return new Date(`${m[1]}-${m[2]}-${m[3]}T${m[4]}:${m[5]}:${m[6]}`);
}

function decodeXml(s) {
  return String(s || '')
    .replace(/&/g, '&')
    .replace(/</g, '<')
    .replace(/>/g, '>')
    .replace(/"/g, '"')
    .replace(/&#39;/g, "'");
}

async function fetchFeed(url) {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), 12000);
  try {
    const res = await fetch(url, {
      signal: ctrl.signal,
      headers: { 'User-Agent': 'StreamFlixVIP/1.0', Accept: '*/*' },
    });
    if (!res.ok) throw new Error(`${url} HTTP ${res.status}`);
    const buf = Buffer.from(await res.arrayBuffer());
    const xml = url.endsWith('.gz') ? (await gunzip(buf)).toString('utf8') : buf.toString('utf8');
    return xml;
  } finally {
    clearTimeout(t);
  }
}

function parseFeed(xml) {
  const namesById = new Map();
  const chRe = /<channel\s+id="([^"]+)"[\s\S]*?<\/channel>/g;
  let m;
  while ((m = chRe.exec(xml))) {
    const id = m[1];
    const block = m[0];
    const dn = [];
    const dnRe = /<display-name[^>]*>([^<]*)<\/display-name>/g;
    let d;
    while ((d = dnRe.exec(block))) dn.push(decodeXml(d[1]));
    namesById.set(id, dn.length ? dn : [id]);
  }

  const now = Date.now();
  const byKey = new Map();
  const pRe =
    /<programme\s+start="(\d{14})[^"]*"\s+stop="(\d{14})[^"]*"\s+channel="([^"]+)"[\s\S]*?<title[^>]*>([^<]*)<\/title>/g;
  while ((m = pRe.exec(xml))) {
    const start = parseXmlTvTime(m[1]);
    const stop = parseXmlTvTime(m[2]);
    if (!start || !stop) continue;
    const title = decodeXml(m[4]).trim();
    if (!title) continue;
    const labels = namesById.get(m[3]) || [m[3]];
    for (const label of labels) {
      const key = normalize(label);
      if (!key || key.length < 2) continue;
      let row = byKey.get(key);
      if (!row) {
        row = { name: label, now: '', next: '' };
        byKey.set(key, row);
      }
      if (start.getTime() <= now && stop.getTime() > now) row.now = title;
      else if (start.getTime() > now && !row.next) row.next = title;
    }
  }
  return Array.from(byKey.values()).filter((p) => p.now || p.next);
}

async function buildEpg() {
  const merged = new Map();
  for (const url of FEEDS) {
    try {
      const xml = await fetchFeed(url);
      for (const p of parseFeed(xml)) {
        const key = normalize(p.name);
        const prev = merged.get(key);
        if (!prev) merged.set(key, p);
        else {
          if (!prev.now && p.now) prev.now = p.now;
          if (!prev.next && p.next) prev.next = p.next;
        }
      }
    } catch (e) {
      console.warn('[live-epg] feed falhou', url, e.message);
    }
  }
  return Array.from(merged.values());
}

async function handler(req, res) {
  if (req.method !== 'GET') {
    res.status(405).json({ error: 'Metodo nao permitido' });
    return;
  }
  const now = Date.now();
  if (epgCache.programmes.length && now - epgCache.at < EPG_TTL_MS) {
    res.setHeader('X-Cache', 'HIT');
    res.status(200).json({
      programmes: epgCache.programmes,
      cached: true,
      cacheAgeSec: Math.round((now - epgCache.at) / 1000),
    });
    return;
  }
  try {
    const programmes = await buildEpg();
    if (programmes.length) epgCache = { at: Date.now(), programmes };
    res.setHeader('X-Cache', 'MISS');
    res.status(200).json({ programmes, cached: false });
  } catch (e) {
    if (epgCache.programmes.length) {
      res.status(200).json({ programmes: epgCache.programmes, cached: true, stale: true });
      return;
    }
    res.status(200).json({ programmes: [], error: e.message || 'epg indisponivel' });
  }
}

module.exports = handler;
