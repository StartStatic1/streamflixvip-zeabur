// api/live-epg.js
// Grade agora/a seguir. Fontes:
//   1) XMLTV dos painéis Xtream (ex.: /xmltv.php)
//   2) LIVE_EPG_FEEDS no .env (urls separadas por vírgula)
//   3) feeds públicos BR/PT
const zlib = require('zlib');
const { promisify } = require('util');
const gunzip = promisify(zlib.gunzip);

const EPG_TTL_MS = 30 * 60 * 1000;
const MAX_FEED_BYTES = 45 * 1024 * 1024;
let epgCache = { at: 0, programmes: [] };

const DEFAULT_FEEDS = [
  'http://diex.fun/xmltv.php?username=maria1234&password=maria1234',
  'https://epg.lat/files/br.xml.gz',
  'https://epg.lat/files/pt.xml.gz',
  'https://epgshare01.online/epgshare01/epg_ripper_BR1.xml.gz',
];

function extraFeedsFromEnv() {
  const raw = String(process.env.LIVE_EPG_FEEDS || '').trim();
  if (!raw) return [];
  return raw.split(/[\n,;]+/).map((s) => s.trim()).filter((s) => /^https?:\/\//i.test(s));
}

function normalize(s) {
  return String(s || '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, ' ')
    .replace(/\b(hd|fhd|sd|4k|uhd|h264|h265|hevc|hdr|full hd|vip|premium)\b/g, ' ')
    .replace(/\b(br|pt|us|uk|ar|mx|lat|latam)\b/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

// "20261025120000 +0000" -> respeita o fuso do feed (sem fuso: horario de Brasilia).
function parseXmlTvTime(raw) {
  const m = String(raw || '').trim().match(/^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})(?:\s*([+-])(\d{2}):?(\d{2}))?/);
  if (!m) return null;
  const tz = m[7] ? `${m[7]}${m[8]}:${m[9]}` : '-03:00';
  const d = new Date(`${m[1]}-${m[2]}-${m[3]}T${m[4]}:${m[5]}:${m[6]}${tz}`);
  return Number.isNaN(d.getTime()) ? null : d;
}

// "São.Paulo/SP..SBT.HD.br" -> "SBT HD" (ids de feeds tipo epgshare/epg.lat)
function aliasFromId(id) {
  let s = String(id || '');
  if (s.includes('..')) s = s.slice(s.lastIndexOf('..') + 2);
  s = s.replace(/\.(br|pt|us|uk|ar|mx)$/i, '').replace(/[._]+/g, ' ').trim();
  return s;
}

function decodeXml(s) {
  return String(s || '')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&apos;/g, "'");
}

async function maybeGunzip(buf) {
  if (buf.length >= 2 && buf[0] === 0x1f && buf[1] === 0x8b) {
    return (await gunzip(buf)).toString('utf8');
  }
  return buf.toString('utf8');
}

async function fetchFeed(url) {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), 22000);
  try {
    const res = await fetch(url, {
      signal: ctrl.signal,
      headers: {
        'User-Agent': 'Mozilla/5.0 StreamFlixVIP/1.0',
        Accept: '*/*',
        'Accept-Encoding': 'identity',
      },
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const buf = Buffer.from(await res.arrayBuffer());
    if (buf.length > MAX_FEED_BYTES) throw new Error(`feed grande demais ${buf.length}`);
    const xml = await maybeGunzip(buf);
    if (!xml.includes('<programme') && !xml.includes('<tv')) {
      throw new Error('resposta nao e XMLTV');
    }
    return xml;
  } finally {
    clearTimeout(t);
  }
}

function attr(block, name) {
  const m = String(block).match(new RegExp(`${name}="([^"]+)"`));
  return m ? m[1] : '';
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
    const alias = aliasFromId(id);
    if (alias && !dn.some((x) => normalize(x) === normalize(alias))) dn.push(alias);
    namesById.set(id, dn.length ? dn : [id]);
  }

  const now = Date.now();
  const byKey = new Map();

  const pRe = /<programme\s+([^>]+)>([\s\S]*?)<\/programme>/g;
  while ((m = pRe.exec(xml))) {
    const start = parseXmlTvTime(attr(m[1], 'start'));
    const stop = parseXmlTvTime(attr(m[1], 'stop'));
    if (!start || !stop) continue;
    const chId = attr(m[1], 'channel');
    const titleM = m[2].match(/<title[^>]*>([^<]*)<\/title>/);
    const title = decodeXml(titleM ? titleM[1] : '').trim();
    if (!title) continue;
    const labels = namesById.get(chId) || [chId, aliasFromId(chId)].filter(Boolean);
    for (const label of labels) {
      const key = normalize(label);
      if (!key || key.length < 2) continue;
      let row = byKey.get(key);
      if (!row) {
        row = { name: label, now: '', next: '' };
        byKey.set(key, row);
      }
      if (start.getTime() <= now && stop.getTime() > now) row.now = title;
      else if (start.getTime() > now && (!row.next || start.getTime() < row.nextAt)) {
        row.next = title;
        row.nextAt = start.getTime();
      }
    }
  }
  return Array.from(byKey.values())
    .filter((p) => p.now || p.next)
    .map(({ name, now: n, next }) => ({ name, now: n, next }));
}

// Feed que falha ou vem vazio/desatualizado fica pausado (1 h, 2 h... ate 12 h).
const feedHealth = new Map();
const FEED_BACKOFF_BASE_MS = 60 * 60 * 1000;
const FEED_BACKOFF_MAX_MS = 12 * 60 * 60 * 1000;

function feedPaused(url, now = Date.now()) {
  const st = feedHealth.get(url);
  return !!(st && st.until > now);
}

function markFeed(url, ok) {
  if (ok) { feedHealth.delete(url); return; }
  const st = feedHealth.get(url) || { fails: 0, until: 0 };
  st.fails += 1;
  st.until = Date.now() + Math.min(FEED_BACKOFF_MAX_MS, FEED_BACKOFF_BASE_MS * 2 ** (st.fails - 1));
  feedHealth.set(url, st);
}

async function buildEpg() {
  const feeds = [...extraFeedsFromEnv(), ...DEFAULT_FEEDS];
  const seenUrl = new Set();
  const merged = new Map();
  for (const url of feeds) {
    if (seenUrl.has(url)) continue;
    seenUrl.add(url);
    if (feedPaused(url)) continue;
    try {
      let xml = await fetchFeed(url);
      const parsed = parseFeed(xml);
      xml = null;
      markFeed(url, parsed.length > 0);
      console.log(`[live-epg] ${url.split('?')[0]} -> ${parsed.length} canais${parsed.length ? '' : ' (pausado)'}`);
      for (const p of parsed) {
        const key = normalize(p.name);
        const prev = merged.get(key);
        if (!prev) merged.set(key, p);
        else {
          if (!prev.now && p.now) prev.now = p.now;
          if (!prev.next && p.next) prev.next = p.next;
        }
      }
    } catch (e) {
      markFeed(url, false);
      console.warn('[live-epg] feed falhou', url.split('?')[0], e.message);
    }
  }
  return Array.from(merged.values());
}

// Uma montagem por vez; grade vencida responde na hora e atualiza por tras.
let epgInflight = null;
function refreshEpg() {
  if (epgInflight) return epgInflight;
  epgInflight = buildEpg()
    .then((programmes) => {
      if (programmes.length) epgCache = { at: Date.now(), programmes };
      return programmes;
    })
    .finally(() => { epgInflight = null; });
  return epgInflight;
}

async function handler(req, res) {
  if (req.method !== 'GET') {
    res.status(405).json({ error: 'Metodo nao permitido' });
    return;
  }
  const now = Date.now();
  const age = now - epgCache.at;
  const has = epgCache.programmes.length > 0;
  const force = String(req.query?.refresh || '') === '1' && !(has && age < 5 * 60 * 1000);
  if (!force && has && age < EPG_TTL_MS) {
    res.setHeader('X-Cache', 'HIT');
    res.status(200).json({
      programmes: epgCache.programmes,
      cached: true,
      count: epgCache.programmes.length,
      cacheAgeSec: Math.round(age / 1000),
    });
    return;
  }
  if (!force && has) {
    refreshEpg().catch((e) => console.warn('[live-epg] atualizacao em segundo plano', e && e.message));
    res.setHeader('X-Cache', 'STALE');
    res.status(200).json({
      programmes: epgCache.programmes,
      cached: true,
      stale: true,
      count: epgCache.programmes.length,
      cacheAgeSec: Math.round(age / 1000),
    });
    return;
  }
  try {
    const programmes = await refreshEpg();
    if (!programmes.length && epgCache.programmes.length) {
      res.status(200).json({ programmes: epgCache.programmes, cached: true, stale: true, count: epgCache.programmes.length });
      return;
    }
    res.setHeader('X-Cache', force ? 'BYPASS' : 'MISS');
    res.status(200).json({ programmes, cached: false, count: programmes.length });
  } catch (e) {
    if (epgCache.programmes.length) {
      res.status(200).json({ programmes: epgCache.programmes, cached: true, stale: true });
      return;
    }
    res.status(200).json({ programmes: [], error: e.message || 'epg indisponivel', count: 0 });
  }
}

module.exports = handler;
module.exports._test = { parseXmlTvTime, parseFeed, aliasFromId, normalize };
