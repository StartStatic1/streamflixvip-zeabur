// api/live-tv.js
//
// Lista canais ao vivo. Preferência:
//   1) tabela live_tv_sources (fontes só de TV — não misturam com sync VOD)
//   2) fallback: iptv_sources (comportamento antigo)
// Fontes (live_tv_sources) + pontes (iptv_bridges) + canais manuais, URLs
// agrupadas por nome para fallback no player. Mesmo servidor+usuario so e
// consultado uma vez; fonte que falha fica pausada (ate 6 h) e a ultima lista
// boa dela continua valendo.
// Cache em memória no VPS (TTL 20 min). Vencido: responde na hora com a lista
// anterior e atualiza por tras (uma atualizacao por vez).
// Force refresh: GET /api/live-tv?refresh=1 (no maximo 1x por minuto)
//
// SEGURANÇA VIP (lib/vip-gate.js):
//   Por padrão NÃO bloqueia (app mobile/TV ainda não mandam JWT).
//   Com REQUIRE_VIP_LIVE_TV=1 no ambiente: só responde canais se houver
//   VIP válido (JWT Supabase, userId com vip_status ativo, ou deviceId TV).

const { enforceVipOrReject } = require('../lib/vip-gate');
const liveCategories = require('../lib/live-categories');

const SUPABASE_URL = process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';
const MAX_SOURCES = 8;
const ADULT_CATEGORY_ID = '000';
const ADULT_CATEGORY_NAME = '000';

/** Cache da lista de canais (mesma para todos — catálogo público). */
const CACHE_TTL_MS = Math.max(
  60_000,
  Number(process.env.LIVE_TV_CACHE_TTL_MS || 20 * 60 * 1000) || 20 * 60 * 1000,
);
let liveTvCache = { at: 0, payload: null };

const ADULT_RE =
  /\b(adult|adulto|adultos|xxx|xx|x18|18\+|\+18|porn|porno|pornografia|sex|sexo|sexy|erot|erotica|erotico|nsfw|onlyfans|hot\s*live|canal\s*hot|playboy|sexshop|anal|lesbian|gay\s*xxx)\b/i;

function supabaseHeaders(serviceKey) {
  return {
    apikey: serviceKey,
    Authorization: `Bearer ${serviceKey}`,
    'Content-Type': 'application/json',
  };
}

async function sbSelect(serviceKey, table, query) {
  const r = await fetch(`${SUPABASE_URL}/rest/v1/${table}?${query}`, {
    headers: supabaseHeaders(serviceKey),
  });
  if (!r.ok) {
    const body = await r.text().catch(() => '');
    const err = new Error(`Supabase ${table}: ${r.status} ${body.slice(0, 200)}`);
    err.status = r.status;
    throw err;
  }
  return r.json();
}

async function xtreamFetch(baseUrl, username, password, action, params = {}) {
  const url = new URL(`${String(baseUrl).replace(/\/+$/, '')}/player_api.php`);
  url.searchParams.set('username', username);
  url.searchParams.set('password', password);
  if (action) url.searchParams.set('action', action);
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null) url.searchParams.set(k, String(v));
  });

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 14000);
  try {
    const res = await fetch(url.toString(), {
      signal: controller.signal,
      headers: {
        'User-Agent': 'IPTVSmarters/1.0 (Linux; Android)',
        Accept: 'application/json, */*',
      },
    });
    if (!res.ok) throw new Error(`Xtream ${action}: HTTP ${res.status}`);
    return res.json();
  } finally {
    clearTimeout(timeoutId);
  }
}

// "HBO 2" = "HBO2", "SporTV ²" / "RECORD RS FHD2" / "HBO XTREMEFHD": numeros
// sobrescritos e etiquetas de qualidade grudadas no nome nao viram canal novo.
const SUPERSCRIPT_RE = /[\u00b9\u00b2\u00b3\u2070-\u209f]/g;

function channelMergeKey(name) {
  let n = normalizeName(String(name || '').replace(SUPERSCRIPT_RE, ' '));
  if (!n) return '';
  n = n.replace(/\b(fhd|uhd|hd|sd|4k)\d\b/g, ' ').trim().replace(/\b([a-z]{3,}?)(fhd|uhd|hd|4k)$/, '$1');
  n = n.replace(/^\d+\s+/, '');
  n = n.replace(/^(br|pt|us|uk|ar|mx|lat|latam|sp|es|fr|de|it|cl|pe|co|uy|py)\s+/, '');
  n = n.replace(/\b(sd|hd|fhd|uhd|4k|8k|h264|h265|hevc|hdr|lq|hq|full\s*hd|vip|premium|raw|aac|ac3)\b/g, ' ');
  n = n.replace(/\b(canais?|leg|legenda|legendado|legendada|legend|sub|subs|subtitle|legendas|dublado|dublada|multi|audio|server|opcao|opt|option|backup|alt)\b/g, ' ');
  n = n.replace(/\s+/g, ' ').trim();
  return n;
}

function displayBaseName(name) {
  let s = String(name || '').replace(/\s*\([^)]*\)\s*/g, ' ');
  s = s.replace(/^\s*\[(BR|PT|BRA)\]\s*/i, '').replace(/^\s*(BR|PT|BRA)\s*[:|\-»]+\s*/i, '');
  s = s.replace(/\b(SD|HD|FHD|UHD|4K|8K|H264|H265|HEVC|HDR|FULL\s*HD|VIP)\b/gi, ' ');
  s = s.replace(/\b(LEG|LEGENDADO|LEGENDADA|SUB|DUB|DUBLADO)\b/gi, ' ');
  s = s.replace(/\s+/g, ' ').trim();
  return s || String(name || '').trim();
}

function qualityLabelFromScore(q) {
  if (q >= 50) return '4K';
  if (q >= 40) return 'FHD';
  if (q >= 30) return 'HD';
  if (q <= 5) return 'SD';
  return 'Padrao';
}

function isLegendadoName(name) {
  const n = normalizeName(name);
  return /\b(leg|legenda|legendado|legendada|legend|sub|subs|subtitle|legendas)\b/.test(n);
}

function qualityScore(name) {
  const n = normalizeName(name);
  if (/\b(4k|uhd)\b/.test(n)) return 50;
  if (/\bfhd\b/.test(n) || /full\s*hd/.test(n)) return 40;
  if (/\bhd\b/.test(n) && !/\bsd\b/.test(n)) return 30;
  if (/\bsd\b/.test(n)) return 5;
  return 20;
}

function normalizeName(name) {
  return String(name || '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, ' ')
    .trim();
}

function isAdultText(text) {
  const n = normalizeName(text);
  if (!n) return false;
  if (n === '000' || n === '00' || n === '0') return true;
  return ADULT_RE.test(n);
}

function buildLiveUrl(host, user, pass, streamId, extension) {
  const base = String(host).replace(/\/+$/, '');
  const ext = (extension || 'm3u8').toLowerCase().replace(/^\./, '');
  const useExt = ext === 'ts' ? 'm3u8' : (ext || 'm3u8');
  return `${base}/live/${user}/${pass}/${streamId}.${useExt}`;
}


function settleWithBudget(promises, budgetMs) {
  if (!promises.length) return Promise.resolve([]);
  return new Promise((resolve) => {
    const collected = new Array(promises.length);
    let done = 0;
    const finish = () => resolve(collected.filter(Boolean));
    const timer = setTimeout(finish, budgetMs);
    promises.forEach((p, i) => {
      Promise.resolve(p)
        .then((v) => { collected[i] = v; })
        .catch((err) => {
          collected[i] = {
            sourceName: 'fonte',
            priority: 100,
            categories: [],
            streams: [],
            skipReason: (err && err.message) || 'timeout',
          };
        })
        .finally(() => {
          done += 1;
          if (done >= promises.length) {
            clearTimeout(timer);
            finish();
          }
        });
    });
  });
}

function hasXtreamCreds(source) {
  return !!(source.xtream_host && source.xtream_user && source.xtream_pass);
}

async function loadFromSource(source) {
  const host = source.xtream_host;
  const user = source.xtream_user;
  const pass = source.xtream_pass;
  if (!host || !user || !pass) {
    return { sourceName: source.name || 'Fonte', priority: source.priority ?? 100, categories: [], streams: [], skipReason: 'sem credenciais xtream' };
  }

  let streamErr = null;
  const [categories, streams] = await Promise.all([
    xtreamFetch(host, user, pass, 'get_live_categories').catch((e) => {
      console.error('[live-tv] categorias', source.name, e.message);
      return [];
    }),
    xtreamFetch(host, user, pass, 'get_live_streams').catch((e) => {
      console.error('[live-tv] streams', source.name, e.message);
      streamErr = e.name === 'AbortError' ? 'tempo esgotado' : e.message;
      return [];
    }),
  ]);

  const catList = Array.isArray(categories) ? categories : [];
  const streamList = Array.isArray(streams) ? streams : [];
  console.log(`[live-tv] ${source.name}: ${catList.length} cats, ${streamList.length} streams`);

  return {
    sourceName: source.name || 'Fonte',
    priority: source.priority ?? 100,
    skipReason: streamList.length ? null : (streamErr || '0 canais'),
    categories: catList.map((c) => ({
      id: String(c.category_id),
      name: c.category_name || 'Outros',
    })),
    streams: streamList.map((s) => ({
      streamId: s.stream_id,
      name: s.name || `Canal ${s.stream_id}`,
      logo: s.stream_icon || null,
      categoryId: String(s.category_id ?? ''),
      url: buildLiveUrl(host, user, pass, s.stream_id, s.container_extension),
      sourceLabel: source.name || 'Fonte',
    })),
  };
}

const MAX_BRIDGES = 16;

async function loadBridgeRows(serviceKey) {
  try {
    const rows = await sbSelect(
      serviceKey,
      'iptv_bridges',
      'is_active=eq.true&use_live=eq.true&select=id,name,xtream_host,xtream_user,xtream_pass,live_cats&order=created_at.desc&limit=20',
    );
    return Array.isArray(rows) ? rows : [];
  } catch (e) {
    console.warn('[live-tv] iptv_bridges:', e.message);
    return [];
  }
}

function filterBridgeLive(loaded, liveCats) {
  const allow = new Set((Array.isArray(liveCats) ? liveCats : []).map((c) => String(c && c.id != null ? c.id : c)));
  if (!allow.size) return loaded;
  loaded.categories = (loaded.categories || []).filter((c) => allow.has(String(c.id)));
  loaded.streams = (loaded.streams || []).filter((s) => allow.has(String(s.categoryId)));
  return loaded;
}

async function loadFromBridge(bridge) {
  const loaded = await loadFromSource({
    name: bridge.name || 'Bridge',
    xtream_host: bridge.xtream_host,
    xtream_user: bridge.xtream_user,
    xtream_pass: bridge.xtream_pass,
    priority: 80,
  });
  return filterBridgeLive(loaded, bridge.live_cats);
}

async function loadManualChannels(serviceKey) {
  try {
    const rows = await sbSelect(
      serviceKey,
      'live_tv_manual_channels',
      'is_active=eq.true&select=id,name,logo,group_title,stream_url,priority&order=priority.asc.nullslast&limit=500',
    );
    return Array.isArray(rows) ? rows : [];
  } catch (e) {
    console.warn('[live-tv] manual channels:', e.message);
    return [];
  }
}

async function loadSourceRows(serviceKey) {
  try {
    let rows = await sbSelect(
      serviceKey,
      'live_tv_sources',
      'is_active=eq.true&select=id,name,priority,xtream_host,xtream_user,xtream_pass,is_active&order=priority.asc.nullslast&limit=20',
    );
    if (Array.isArray(rows) && rows.length) {
      return { rows, origin: 'live_tv_sources' };
    }
    rows = await sbSelect(
      serviceKey,
      'live_tv_sources',
      'select=id,name,priority,xtream_host,xtream_user,xtream_pass,is_active&order=priority.asc.nullslast&limit=20',
    );
    if (Array.isArray(rows) && rows.filter(hasXtreamCreds).length) {
      return { rows, origin: 'live_tv_sources' };
    }
  } catch (e) {
    console.warn('[live-tv] live_tv_sources indisponível, fallback iptv_sources:', e.message);
  }

  let sources = await sbSelect(
    serviceKey,
    'iptv_sources',
    'is_active=eq.true&select=id,name,priority,xtream_host,xtream_user,xtream_pass,source_type,is_active&order=priority.asc.nullslast&limit=20',
  );
  if (!Array.isArray(sources) || !sources.length) {
    sources = await sbSelect(
      serviceKey,
      'iptv_sources',
      'select=id,name,priority,xtream_host,xtream_user,xtream_pass,source_type,is_active&order=priority.asc.nullslast&limit=20',
    );
  }
  return { rows: Array.isArray(sources) ? sources : [], origin: 'iptv_sources' };
}

// ---------------------------------------------------------------------------
// Saude das fontes: fonte que falha (erro, 401/503, 0 canais) fica pausada com
// espera crescente (10 min, 20, 40... ate 6 h) em vez de ser consultada a cada
// atualizacao. Se ela ja funcionou antes (ate 6 h atras), a ultima lista boa
// dela continua sendo usada.
// ---------------------------------------------------------------------------
const SOURCE_BACKOFF_BASE_MS = 10 * 60 * 1000;
const SOURCE_BACKOFF_MAX_MS = 6 * 60 * 60 * 1000;
const LAST_GOOD_MAX_AGE_MS = 6 * 60 * 60 * 1000;
const sourceHealth = new Map();

function sourceKey(s) {
  const user = String(s.xtream_user || '').trim().toLowerCase();
  try {
    const u = new URL(String(s.xtream_host || '').trim());
    return `${u.host.toLowerCase()}${u.pathname.replace(/\/+$/, '').toLowerCase()}|${user}`;
  } catch (_) {
    return `${String(s.xtream_host || '').trim().toLowerCase()}|${user}`;
  }
}

function backoffMs(fails) {
  return Math.min(SOURCE_BACKOFF_MAX_MS, SOURCE_BACKOFF_BASE_MS * 2 ** Math.max(0, fails - 1));
}

function emptyResult(entry, reason) {
  return { sourceName: entry.name, priority: entry.priority, categories: [], streams: [], skipReason: reason };
}

async function loadWithHealth(entry, now = Date.now()) {
  const st = sourceHealth.get(entry.key) || { fails: 0, until: 0, lastGood: null, lastGoodAt: 0, lastReason: null };
  sourceHealth.set(entry.key, st);
  st.name = entry.name;
  const lastGoodOk = st.lastGood && now - st.lastGoodAt < LAST_GOOD_MAX_AGE_MS;

  if (st.until > now) {
    if (lastGoodOk) return { ...st.lastGood, reused: true, skipReason: 'pausada (usando ultima lista boa)' };
    return emptyResult(entry, `pausada ${Math.ceil((st.until - now) / 60000)} min: ${st.lastReason || 'falhou'}`);
  }

  let r;
  try {
    r = await entry.load();
  } catch (e) {
    r = emptyResult(entry, (e && e.message) || 'falhou');
  }
  if (r && r.streams && r.streams.length) {
    st.fails = 0;
    st.until = 0;
    st.lastReason = null;
    st.lastGood = r;
    st.lastGoodAt = Date.now();
    return r;
  }
  st.fails += 1;
  st.until = Date.now() + backoffMs(st.fails);
  st.lastReason = (r && r.skipReason) || '0 canais';
  if (lastGoodOk) return { ...st.lastGood, reused: true, skipReason: `falhou agora (${st.lastReason}), usando ultima lista boa` };
  return r || emptyResult(entry, st.lastReason);
}

function pruneSourceHealth(activeKeys) {
  for (const k of sourceHealth.keys()) {
    if (!activeKeys.has(k)) sourceHealth.delete(k);
  }
}

/** Lista unica de fontes + pontes: mesmo servidor+usuario so entra uma vez. */
function buildSourceEntries(sources, bridges) {
  const entries = [];
  const seen = new Map();
  const duplicates = [];
  for (const s of sources.filter(hasXtreamCreds).slice(0, MAX_SOURCES)) {
    const key = sourceKey(s);
    if (seen.has(key)) { duplicates.push(`${s.name} = ${seen.get(key)}`); continue; }
    seen.set(key, s.name);
    entries.push({ key, name: s.name || 'Fonte', priority: s.priority ?? 100, kind: 'source', load: () => loadFromSource(s) });
  }
  let bridgeCount = 0;
  for (const b of bridges) {
    if (bridgeCount >= MAX_BRIDGES) break;
    if (!hasXtreamCreds(b)) continue;
    const key = sourceKey(b);
    if (seen.has(key)) { duplicates.push(`${b.name} = ${seen.get(key)}`); continue; }
    seen.set(key, b.name);
    bridgeCount += 1;
    entries.push({ key, name: b.name || 'Bridge', priority: 80, kind: 'bridge', load: () => loadFromBridge(b) });
  }
  return { entries, duplicates };
}

function streamFingerprint(r) {
  const names = (r.streams || []).map((s) => normalizeName(s.name)).sort();
  return `${names.length}:${names.slice(0, 60).join('|')}`;
}

// ---------------------------------------------------------------------------
// Categorias: "BR | ESPORTES", "⚽ Esportes" e "Canais Esportes" viram uma so.
// ---------------------------------------------------------------------------
const CATEGORY_PREFIX_RE = /^(br|bra|brasil|brazil|pt|prt|portugal)\s+(?=\S)/;

function categoryKey(name) {
  const n = normalizeName(name);
  if (!n) return '';
  let k = n.replace(/^canais?\s+/, '').replace(/\s+canais?$/, '');
  const is24h = /(^|\s)24\s*h(rs)?(\s|$)/.test(k);
  if (is24h) {
    const base = k.replace(/(^|\s)24\s*h(rs)?(?=\s|$)/g, ' ').replace(/\s+/g, ' ').trim();
    if (base) k = `${base} 24h`;
  }
  const stripped = k.replace(CATEGORY_PREFIX_RE, '');
  if (stripped && stripped !== k && !/^(tv|canais?)$/.test(stripped)) k = stripped;
  return k.trim() || n;
}

function cleanCategoryName(name) {
  let s = String(name || '').trim();
  s = s.replace(/^[^\p{L}\p{N}]+/u, '');
  s = s.replace(/^(BR|BRA|PT)\s*[|:\-»•]+\s*/i, '');
  s = s.replace(/^Canais\s*[|:\-»•]+\s*/i, '');
  s = s.replace(/\s*[^\p{L}\p{N})]+$/u, '');
  s = s.replace(/\s+/g, ' ').trim();
  let suffix = '';
  if (/^24\s*h\s*[-)|:]+\s*\S/i.test(s)) {
    s = s.replace(/^24\s*h\s*[-)|:]+\s*/i, '');
    suffix = ' 24h';
  }
  if (s && s === s.toUpperCase() && /\p{Lu}/u.test(s) && s.length > 3) {
    s = s.toLowerCase().replace(/(^|[\s/(])(\p{L})/gu, (m, sp, ch) => sp + ch.toUpperCase());
  }
  s = (s + suffix).trim();
  return s || String(name || '').trim() || 'Outros';
}

const MAX_STREAMS_PER_CHANNEL = 8;

/** Fonte cujos links testados nao tocaram nenhum (404/HTML/timeout). */
function isDeadSource(entryKey) {
  const h = entryKey && playbackHealth.get(entryKey);
  return !!(h && h.total >= 3 && h.ok === 0);
}

/** Xtream: alem do .m3u8, o mesmo canal em .ts (MPEG-TS) como reserva. */
function tsVariant(url) {
  return /\/live\/[^/]+\/[^/]+\/[^/]+\.m3u8$/.test(url) ? url.replace(/\.m3u8$/, '.ts') : null;
}

function mergeResults(results, manuals) {
  // Fonte morta (nenhum link toca) nao entra; se TODAS parecerem mortas
  // (ex.: rede do VPS caiu), mantem tudo para nao zerar a lista.
  const alive = results.filter((r) => !isDeadSource(r.entryKey));
  if (alive.length && alive.length < results.length) {
    console.log('[live-tv] fontes ignoradas (links nao tocam):', results.filter((r) => isDeadSource(r.entryKey)).map((r) => r.sourceName).join(', '));
    results = alive;
  }
  const adultCatIds = new Set();
  for (const r of results) {
    for (const c of r.categories) {
      if (isAdultText(c.name) || isAdultText(c.id)) {
        adultCatIds.add(String(c.id));
      }
    }
  }

  const categoryMap = new Map();

  for (const r of results) {
    for (const c of r.categories) {
      if (adultCatIds.has(String(c.id)) || isAdultText(c.name)) continue;
      const key = categoryKey(c.name);
      if (!key) continue;
      if (!categoryMap.has(key)) categoryMap.set(key, { id: key, name: cleanCategoryName(c.name) });
    }
  }

  const channelMap = new Map();
  for (const r of results) {
    const catNameById = new Map(r.categories.map((c) => [c.id, c.name]));
    for (const s of r.streams) {
      const key = channelMergeKey(s.name);
      if (!key || key.length < 2) continue;

      const rawCatName = catNameById.get(s.categoryId) || 'Outros';
      const isAdult =
        adultCatIds.has(String(s.categoryId)) ||
        isAdultText(rawCatName) ||
        isAdultText(s.name);

      const catKey = isAdult ? ADULT_CATEGORY_ID : categoryKey(rawCatName) || 'outros';
      if (!isAdult && !categoryMap.has(catKey)) {
        categoryMap.set(catKey, { id: catKey, name: cleanCategoryName(rawCatName) });
      }

      let ch = channelMap.get(key);
      if (!ch) {
        ch = { id: key, name: s.name, logo: s.logo, categoryId: catKey, rawCategory: rawCatName, streams: [] };
        channelMap.set(key, ch);
      } else if (isAdult) {
        ch.categoryId = ADULT_CATEGORY_ID;
      }
      if (!ch.logo && s.logo) ch.logo = s.logo;
      const sLeg = isLegendadoName(s.name);
      const chLeg = isLegendadoName(ch.name);
      if ((!sLeg && chLeg) || (sLeg === chLeg && qualityScore(s.name) > qualityScore(ch.name))) {
        ch.name = String(s.name || '').replace(/\s*\([^)]*\)\s*/g, ' ').replace(/\s+/g, ' ').trim() || s.name;
      }
      if (!ch.streams.some((x) => x.url === s.url)) {
        // Prioridade da fonte manda (1 = melhor); qualidade so desempata dentro
        // da mesma fonte (antes +80 por nome sem "HD" jogava a melhor fonte
        // para tras das pontes).
        const basePri = (r.priority ?? 100) + playbackPenalty(r.entryKey);
        const q = qualityScore(s.name);
        // inteiro: os apps (TV 3.x e celular 16.3) leem priority como Int
        const pri = Math.round(basePri + (q >= 30 ? 0 : q <= 5 ? 6 : 2));
        ch.streams.push({ url: s.url, label: s.sourceLabel, priority: pri, quality: q, leg: sLeg });
        const ts = tsVariant(s.url);
        if (ts && !ch.streams.some((x) => x.url === ts)) {
          ch.streams.push({ url: ts, label: s.sourceLabel, priority: pri + 1, quality: q, leg: sLeg });
        }
      }
    }
  }

  for (const m of manuals) {
    const url = String(m.stream_url || '').trim();
    const name = String(m.name || '').trim() || 'Canal manual';
    if (!url) continue;
    const key = channelMergeKey(name) || ('manual-' + (m.id || url).toString().slice(0, 24));
    const groupRaw = String(m.group_title || 'Manuais').trim() || 'Manuais';
    const catKey = categoryKey(groupRaw) || 'manuais';
    if (!categoryMap.has(catKey)) {
      categoryMap.set(catKey, { id: catKey, name: cleanCategoryName(groupRaw) });
    }
    let ch = channelMap.get(key);
    if (!ch) {
      ch = { id: key, name, logo: m.logo || '', categoryId: catKey, rawCategory: groupRaw, streams: [] };
      channelMap.set(key, ch);
    } else if (!ch.logo && m.logo) {
      ch.logo = m.logo;
    }
    if (!ch.streams.some((x) => x.url === url)) {
      ch.streams.push({
        url,
        label: 'Manual',
        priority: Number.isFinite(Number(m.priority)) ? Math.round(Number(m.priority)) : 1,
        quality: 40,
        leg: isLegendadoName(name),
      });
    }
  }

  let channels = [];
  for (const ch of channelMap.values()) {
    if (ch.categoryId === ADULT_CATEGORY_ID) continue;
    let streams = ch.streams.slice().sort((a, b) => (a.priority ?? 100) - (b.priority ?? 100));
    const hasGood = streams.some((s) => (s.quality ?? 20) >= 30);
    if (hasGood) streams = streams.filter((s) => (s.quality ?? 20) >= 20);
    channels.push({
      ...ch,
      name: displayBaseName(ch.name) || ch.name,
      streams: streams.slice(0, MAX_STREAMS_PER_CHANNEL).map(({ url, label, priority, quality, leg }) => ({
        url,
        label,
        priority,
        quality: qualityLabelFromScore(quality ?? 20),
        leg: !!leg,
      })),
    });
  }

  // Mesmo nome exibido = mesmo canal (junta as URLs como alternativas).
  // Chave sem espacos: "SporTV 2" = "SporTV2", "TV Cidade CE" = "TV CidadeCE".
  // "ESPN 1"/"SporTV 1"/"Paramount 01" + o canal sem numero = o mesmo canal.
  const compactKey = (ch) => (channelMergeKey(ch.name) || normalizeName(ch.name)).replace(/\s+/g, '');
  const allKeys = new Set(channels.map(compactKey));
  const byDisplay = new Map();
  for (const ch of channels) {
    let dn = compactKey(ch);
    const one = dn.match(/^(.*[a-z])0?1$/);
    if (one && allKeys.has(one[1])) dn = one[1];
    if (!dn) continue;
    const prev = byDisplay.get(dn);
    if (!prev) { byDisplay.set(dn, ch); continue; }
    const seen = new Set(prev.streams.map((s) => s.url));
    for (const s of ch.streams) {
      if (!seen.has(s.url)) { prev.streams.push(s); seen.add(s.url); }
    }
    if (!prev.logo && ch.logo) prev.logo = ch.logo;
    if ((ch.name || '').length < (prev.name || '').length) prev.name = ch.name;
    if (ch.streams.length > prev.streams.length) prev.id = ch.id;
  }
  channels = Array.from(byDisplay.values()).map((ch) => ({
    ...ch,
    streams: ch.streams
      .slice()
      .sort((a, b) => (a.priority ?? 100) - (b.priority ?? 100))
      .slice(0, MAX_STREAMS_PER_CHANNEL),
  }));
  {
    // ids precisam ser unicos para o app (favoritos / listas)
    const ids = new Set();
    channels = channels.filter((ch) => (ids.has(ch.id) ? false : (ids.add(ch.id), true)));
  }

  // Categorias fixas e limpas (Canais, Regionais, Capitais, HBO, Telecine...);
  // canais 24h e "series avulsas" saem. Ver lib/live-categories.js.
  const organized = liveCategories.organize(channels.filter((ch) => ch.streams.length));
  channels = organized.channels.map(({ rawCategory, ...ch }) => ch);
  return { channels, categories: organized.categories };
}

// ---------------------------------------------------------------------------
// Teste de reproducao: a lista da fonte pode vir OK (player_api) mas os links
// nao tocarem (ex.: Lives0topTV/4LiveTV devolvendo 404/403). A cada 30 min
// testamos alguns links de cada fonte; fonte que nao toca vai para o FIM da
// ordem dos links (continua como reserva - nada e apagado do painel).
// Obs.: o teste sai do IP do VPS; painel que bloqueia datacenter parece morto
// aqui e so e rebaixado, nao removido.
// ---------------------------------------------------------------------------
const PROBE_TTL_MS = 30 * 60 * 1000;
const PROBE_SAMPLES = 4;
const PROBE_TIMEOUT_MS = 8000;
const playbackHealth = new Map(); // entryKey -> { ok, total, at, name }

function probeEnabled() {
  const v = String(process.env.LIVE_TV_PROBE || '').trim();
  if (v === '0') return false;
  if (v === '1') return true;
  return process.env.NODE_ENV !== 'test';
}

function playbackPenalty(entryKey) {
  const h = entryKey && playbackHealth.get(entryKey);
  if (!h || !h.total) return 0;
  if (h.ok === 0) return 1000;
  if (h.ok / h.total < 0.5) return 300;
  return 0;
}

async function probeStreamUrl(url) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), PROBE_TIMEOUT_MS);
  try {
    const res = await fetch(url, {
      signal: controller.signal,
      redirect: 'follow',
      headers: { 'User-Agent': 'VLC/3.0.20 LibVLC/3.0.20', Range: 'bytes=0-4095' },
    });
    if (!(res.status === 200 || res.status === 206)) return false;
    const reader = res.body && res.body.getReader ? res.body.getReader() : null;
    let buf = Buffer.alloc(0);
    if (reader) {
      while (buf.length < 512) {
        const { value, done } = await reader.read();
        if (done || !value) break;
        buf = Buffer.concat([buf, Buffer.from(value)]);
      }
      try { await reader.cancel(); } catch (_) {}
    }
    if (!buf.length) return false;
    const head = buf.slice(0, 64).toString('latin1');
    if (/^\s*#EXTM3U/.test(head)) return true;
    if (buf[0] === 0x47) return true; // MPEG-TS
    return false; // html / vazio = painel respondendo erro
  } catch (_) {
    return false;
  } finally {
    clearTimeout(timer);
  }
}

async function probeSources(results, now = Date.now()) {
  if (!probeEnabled()) return;
  const jobs = results
    .filter((r) => r.entryKey && r.streams && r.streams.length)
    .filter((r) => {
      const h = playbackHealth.get(r.entryKey);
      return !h || now - h.at > PROBE_TTL_MS;
    })
    .map(async (r) => {
      const pool = r.streams;
      const picks = [];
      for (let i = 0; i < PROBE_SAMPLES && pool.length; i++) {
        picks.push(pool[Math.floor(((i + 0.5) / PROBE_SAMPLES) * pool.length)].url);
      }
      const oks = await Promise.all(picks.map(probeStreamUrl));
      const ok = oks.filter(Boolean).length;
      playbackHealth.set(r.entryKey, { ok, total: picks.length, at: Date.now(), name: r.sourceName });
    });
  await Promise.all(jobs);
  const summary = Array.from(playbackHealth.values()).map((h) => `${h.name} ${h.ok}/${h.total}`);
  if (summary.length) console.log('[live-tv] teste de reproducao:', summary.join(', '));
}

async function buildLiveTv(serviceKey) {
  const startedAt = Date.now();
  const [{ rows: sources, origin }, bridges, manuals] = await Promise.all([
    loadSourceRows(serviceKey),
    loadBridgeRows(serviceKey),
    loadManualChannels(serviceKey),
  ]);
  const { entries, duplicates } = buildSourceEntries(sources, bridges);
  pruneSourceHealth(new Set(entries.map((e) => e.key)));
  if (duplicates.length) console.log('[live-tv] fontes repetidas (carregadas 1x):', duplicates.join('; '));

  if (!entries.length && !manuals.length) {
    return {
      categories: [],
      channels: [],
      sourcesUsed: 0,
      diagnostic: {
        origin,
        sourcesInDb: sources.length,
        withCredentials: 0,
        manuals: 0,
        hint: origin === 'live_tv_sources'
          ? 'Nenhuma fonte Xtream nem canal manual. Cadastre na aba TV ao vivo.'
          : 'Nenhuma fonte IPTV com credenciais nem canal manual.',
      },
    };
  }

  const now = Date.now();
  const settled = await settleWithBudget(
    entries.map((e) => loadWithHealth(e, now).then((r) => ({ ...r, sourceName: e.name, priority: e.priority, entryKey: e.key }))),
    20000,
  );
  let results = settled.filter((r) => r && r.streams && r.streams.length > 0);

  // Painel com outro endereco mas exatamente a mesma lista: usa so um.
  const fingerprints = new Map();
  const sameContent = [];
  results = results.filter((r) => {
    const fp = streamFingerprint(r);
    const prev = fingerprints.get(fp);
    if (prev) { sameContent.push(`${r.sourceName} = ${prev}`); return false; }
    fingerprints.set(fp, r.sourceName);
    return true;
  });
  if (sameContent.length) console.log('[live-tv] mesma lista em fontes diferentes:', sameContent.join('; '));

  try {
    await probeSources(results);
  } catch (e) {
    console.warn('[live-tv] teste de reproducao falhou:', e && e.message);
  }

  const report = entries.map((e) => {
    const st = sourceHealth.get(e.key) || {};
    const r = settled.find((x) => x && x.entryKey === e.key);
    return {
      name: e.name,
      streams: (r && r.streams && r.streams.length) || 0,
      reused: !!(r && r.reused),
      skip: (r && r.skipReason) || (r ? null : 'sem resposta no tempo'),
      fails: st.fails || 0,
      playable: playbackHealth.has(e.key) ? `${playbackHealth.get(e.key).ok}/${playbackHealth.get(e.key).total}` : null,
    };
  });
  console.log('[live-tv] scan fontes:', JSON.stringify(report));
  if (!results.length) console.warn('[live-tv] NENHUMA fonte retornou streams — confira live_tv_sources');

  const { channels, categories } = mergeResults(results, manuals);
  const payload = {
    categories: channels.length ? [{ id: 'all', name: 'Todos' }, ...categories] : [],
    channels,
    sourcesUsed: results.length,
    origin,
  };
  if (!channels.length) {
    payload.diagnostic = {
      origin,
      triedSources: entries.map((e) => e.name),
      perSource: report,
      hint: 'Fontes contatadas mas painel não devolveu canais live',
    };
  }
  console.log(`[live-tv] lista montada: ${channels.length} canais, ${categories.length} categorias, ${results.length} fontes, ${manuals.length} manuais, ${Date.now() - startedAt} ms`);
  return payload;
}

// Uma atualizacao por vez: pedidos simultaneos esperam a mesma.
let refreshInflight = null;
const MIN_FORCE_INTERVAL_MS = 60_000;

function hasChannels(payload) {
  return !!(payload && Array.isArray(payload.channels) && payload.channels.length > 0);
}

function refreshLiveTv(serviceKey) {
  if (refreshInflight) return refreshInflight;
  refreshInflight = (async () => {
    const payload = await buildLiveTv(serviceKey);
    if (hasChannels(payload)) {
      const prev = liveTvCache.payload;
      const prevCount = hasChannels(prev) ? prev.channels.length : 0;
      const shrunk = !allowShrinkOnce && prevCount > 200 && payload.channels.length < prevCount * 0.4 &&
        Date.now() - liveTvCache.at < 2 * 60 * 60 * 1000;
      if (shrunk) {
        console.warn(`[live-tv] lista nova muito menor (${payload.channels.length} vs ${prevCount}) — mantendo a anterior`);
        return prev;
      }
      allowShrinkOnce = false;
      payload.version = listVersion(payload);
      liveTvCache = { at: Date.now(), payload };
      console.log(`[live-tv] cache atualizado: ${payload.channels.length} canais, TTL ${Math.round(CACHE_TTL_MS / 60000)} min`);
    }
    return payload;
  })().finally(() => {
    refreshInflight = null;
  });
  return refreshInflight;
}

/**
 * Admin mudou fontes/pontes/canais: a proxima lista sai do zero (sem a trava
 * de "lista muito menor" — apagar uma fonte grande e intencional).
 */
let allowShrinkOnce = false;
let invalidateTimer = null;
function invalidateLiveTv(reason) {
  allowShrinkOnce = true;
  liveTvCache = { at: 0, payload: liveTvCache.payload };
  for (const k of Array.from(playbackHealth.keys())) playbackHealth.delete(k);
  console.log('[live-tv] cache invalidado:', reason || 'admin');
  const key = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!key || process.env.NODE_ENV === 'test') return;
  clearTimeout(invalidateTimer);
  invalidateTimer = setTimeout(() => {
    refreshLiveTv(key).catch((e) => console.warn('[live-tv] refresh apos admin', e && e.message));
  }, 3000);
  if (invalidateTimer.unref) invalidateTimer.unref();
}

function listVersion(payload) {
  const chans = (payload && payload.channels) || [];
  let h = 0;
  for (const c of chans) {
    const str = c.id + ':' + c.categoryId + ':' + (c.streams || []).length;
    for (let i = 0; i < str.length; i++) h = (h * 31 + str.charCodeAt(i)) | 0;
  }
  return `${chans.length}-${(h >>> 0).toString(36)}`;
}

function sendCached(res, xCache, extra = {}) {
  const now = Date.now();
  res.setHeader('X-Cache', xCache);
  res.setHeader('Cache-Control', 'private, max-age=60');
  res.status(200).json({
    ...liveTvCache.payload,
    cached: true,
    cacheAgeSec: Math.round((now - liveTvCache.at) / 1000),
    cacheTtlSec: Math.round(CACHE_TTL_MS / 1000),
    ...extra,
  });
}

async function handler(req, res) {
  if (req.method !== 'GET') {
    res.status(405).json({ error: 'Método não permitido' });
    return;
  }

  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!serviceKey) {
    res.status(500).json({ error: 'Servidor sem SUPABASE_SERVICE_ROLE_KEY', categories: [], channels: [], sourcesUsed: 0 });
    return;
  }

  if (await enforceVipOrReject(req, res, serviceKey, { feature: 'live-tv' })) {
    return;
  }

  const refreshParam = String(req.query?.refresh || '').toLowerCase();
  const now = Date.now();
  const cached = hasChannels(liveTvCache.payload);
  const age = now - liveTvCache.at;
  const forceRefresh = (refreshParam === '1' || refreshParam === 'true') &&
    !(cached && age < MIN_FORCE_INTERVAL_MS);

  if (!forceRefresh && cached && age < CACHE_TTL_MS) {
    sendCached(res, 'HIT');
    return;
  }

  // Lista vencida: responde na hora com a anterior e atualiza por tras.
  if (!forceRefresh && cached) {
    refreshLiveTv(serviceKey).catch((e) => console.error('[live-tv] atualizacao em segundo plano', e && e.message));
    sendCached(res, 'STALE', { stale: true });
    return;
  }

  try {
    const payload = await refreshLiveTv(serviceKey);
    if (!hasChannels(payload) && hasChannels(liveTvCache.payload)) {
      sendCached(res, 'STALE', { stale: true });
      return;
    }
    res.setHeader('X-Cache', forceRefresh ? 'BYPASS' : 'MISS');
    res.status(200).json(payload);
  } catch (err) {
    console.error('[live-tv]', err);
    if (hasChannels(liveTvCache.payload)) {
      sendCached(res, 'STALE', { stale: true });
      return;
    }
    res.status(500).json({
      error: err.message || 'Erro ao carregar canais',
      categories: [],
      channels: [],
      sourcesUsed: 0,
    });
  }
}

module.exports = handler;
module.exports.invalidate = invalidateLiveTv;
module.exports._test = {
  isDeadSource,
  tsVariant,
  listVersion,
  sourceKey,
  backoffMs,
  loadWithHealth,
  sourceHealth,
  buildSourceEntries,
  categoryKey,
  cleanCategoryName,
  mergeResults,
  streamFingerprint,
  refreshLiveTv,
  channelMergeKey,
  playbackHealth,
  playbackPenalty,
  probeStreamUrl,
};

// Aquece a lista logo apos o boot (direto, sem passar pelo bloqueio VIP do
// pedido falso — com REQUIRE_VIP_LIVE_TV=1 o aquecimento antigo era recusado).
// "[live-tv] warmup" fica aqui para o scripts/patch_live_tv_speed.py nao repetir.
if (process.env.NODE_ENV !== 'test') {
  try {
    setTimeout(() => {
      const key = process.env.SUPABASE_SERVICE_ROLE_KEY;
      if (!key) return;
      refreshLiveTv(key).catch((e) => console.warn('[live-tv] warmup', e && e.message));
    }, 1200).unref();
  } catch (_) {}
}
