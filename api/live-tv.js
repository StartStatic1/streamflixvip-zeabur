// api/live-tv.js
//
// Lista canais ao vivo. Preferência:
//   1) tabela live_tv_sources (fontes só de TV — não misturam com sync VOD)
//   2) fallback: iptv_sources (comportamento antigo)
// Até 3 fontes, URLs agrupadas por nome para fallback no player.
// Adulto / XXX: consolidado na categoria "000" e colocado no FINAL da lista.
//
// SEGURANÇA VIP (lib/vip-gate.js):
//   Por padrão NÃO bloqueia (app mobile/TV ainda não mandam JWT).
//   Com REQUIRE_VIP_LIVE_TV=1 no ambiente: só responde canais se houver
//   VIP válido (JWT Supabase, userId com vip_status ativo, ou deviceId TV).
//   Ative o hard gate DEPOIS de publicar app que envia Authorization.

const { enforceVipOrReject } = require('../lib/vip-gate');

const SUPABASE_URL = process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';
const MAX_SOURCES = 3;
const ADULT_CATEGORY_ID = '000';
const ADULT_CATEGORY_NAME = '000';

// Nomes/palavras típicas de categorias e canais adultos (normalizados sem acento).
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
  const timeoutId = setTimeout(() => controller.abort(), 25000);
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
  // "000" / "00" sozinho já é o bucket adulto em muitos painéis
  if (n === '000' || n === '00' || n === '0') return true;
  return ADULT_RE.test(n);
}

function buildLiveUrl(host, user, pass, streamId, extension) {
  const base = String(host).replace(/\/+$/, '');
  const ext = (extension || 'm3u8').toLowerCase().replace(/^\./, '');
  const useExt = ext === 'ts' ? 'm3u8' : (ext || 'm3u8');
  return `${base}/live/${user}/${pass}/${streamId}.${useExt}`;
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

  const [categories, streams] = await Promise.all([
    xtreamFetch(host, user, pass, 'get_live_categories').catch((e) => {
      console.error('[live-tv] categorias', source.name, e.message);
      return [];
    }),
    xtreamFetch(host, user, pass, 'get_live_streams').catch((e) => {
      console.error('[live-tv] streams', source.name, e.message);
      return [];
    }),
  ]);

  const catList = Array.isArray(categories) ? categories : [];
  const streamList = Array.isArray(streams) ? streams : [];
  console.log(`[live-tv] ${source.name}: ${catList.length} cats, ${streamList.length} streams`);

  return {
    sourceName: source.name || 'Fonte',
    priority: source.priority ?? 100,
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

  // Gate VIP (soft por padrão — não quebra app sem token).
  // Com REQUIRE_VIP_LIVE_TV=1: bloqueia se não houver VIP no banco.
  if (await enforceVipOrReject(req, res, serviceKey, { feature: 'live-tv' })) {
    return;
  }

  try {
    const { rows: sources, origin } = await loadSourceRows(serviceKey);
    const withCreds = sources.filter(hasXtreamCreds).slice(0, MAX_SOURCES);

    if (!withCreds.length) {
      res.status(200).json({
        categories: [],
        channels: [],
        sourcesUsed: 0,
        diagnostic: {
          origin,
          sourcesInDb: sources.length,
          withCredentials: 0,
          hint: origin === 'live_tv_sources'
            ? 'Nenhuma fonte em live_tv_sources com host/user/pass. Cadastre na aba TV ao vivo do admin.'
            : 'Nenhuma fonte IPTV com credenciais. Cadastre live_tv_sources (recomendado) ou ative iptv_sources.',
        },
      });
      return;
    }

    const results = await Promise.all(
      withCreds.map((s) =>
        loadFromSource(s).catch((err) => {
          console.error('[live-tv] fonte falhou', s.name, err.message);
          return {
            sourceName: s.name,
            priority: s.priority,
            categories: [],
            streams: [],
            skipReason: err.message,
          };
        }),
      ),
    );

    // Mapa categoryId original -> se é adulto
    const adultCatIds = new Set();
    for (const r of results) {
      for (const c of r.categories) {
        if (isAdultText(c.name) || isAdultText(c.id)) {
          adultCatIds.add(String(c.id));
        }
      }
    }

    const categoryMap = new Map();
    let hasAdult = false;

    for (const r of results) {
      for (const c of r.categories) {
        if (adultCatIds.has(String(c.id)) || isAdultText(c.name)) {
          hasAdult = true;
          continue; // não listar Adult/XXX soltos — tudo vira "000"
        }
        const key = normalizeName(c.name);
        if (!key) continue;
        if (!categoryMap.has(key)) categoryMap.set(key, { id: key, name: c.name });
      }
    }

    const channelMap = new Map();
    for (const r of results) {
      for (const s of r.streams) {
        const key = normalizeName(s.name);
        if (!key) continue;

        const rawCatName =
          r.categories.find((c) => c.id === s.categoryId)?.name || 'Outros';
        const isAdult =
          adultCatIds.has(String(s.categoryId)) ||
          isAdultText(rawCatName) ||
          isAdultText(s.name);

        if (isAdult) hasAdult = true;

        const catKey = isAdult
          ? ADULT_CATEGORY_ID
          : normalizeName(rawCatName) || 'outros';

        let ch = channelMap.get(key);
        if (!ch) {
          ch = {
            id: key,
            name: s.name,
            logo: s.logo,
            categoryId: catKey,
            streams: [],
          };
          channelMap.set(key, ch);
        } else if (isAdult) {
          // Se qualquer fonte marcar como adulto, fica em 000
          ch.categoryId = ADULT_CATEGORY_ID;
        }
        if (!ch.logo && s.logo) ch.logo = s.logo;
        if (!ch.streams.some((x) => x.url === s.url)) {
          ch.streams.push({
            url: s.url,
            label: s.sourceLabel,
            priority: r.priority ?? 100,
          });
        }
      }
    }

    const channels = Array.from(channelMap.values()).map((ch) => ({
      ...ch,
      streams: ch.streams.sort((a, b) => (a.priority ?? 100) - (b.priority ?? 100)),
    }));
    channels.sort((a, b) => a.name.localeCompare(b.name, 'pt-BR'));

    const categories = Array.from(categoryMap.values()).sort((a, b) =>
      a.name.localeCompare(b.name, 'pt-BR'),
    );
    const usedCats = new Set(channels.map((c) => c.categoryId));
    let filteredCategories = categories.filter((c) => usedCats.has(c.id));

    // Adulto sempre no FINAL, com id/nome "000" (não no início)
    if (hasAdult && usedCats.has(ADULT_CATEGORY_ID)) {
      filteredCategories = filteredCategories.filter((c) => c.id !== ADULT_CATEGORY_ID);
      filteredCategories.push({ id: ADULT_CATEGORY_ID, name: ADULT_CATEGORY_NAME });
    }

    const sourcesUsed = results.filter((r) => r.streams.length > 0).length;

    const payload = {
      categories: channels.length ? [{ id: 'all', name: 'Todos' }, ...filteredCategories] : [],
      channels,
      sourcesUsed,
      origin,
    };

    if (!channels.length) {
      payload.diagnostic = {
        origin,
        triedSources: withCreds.map((s) => s.name),
        perSource: results.map((r) => ({
          name: r.sourceName,
          streams: r.streams.length,
          categories: r.categories.length,
          skipReason: r.skipReason || null,
        })),
        hint: 'Fontes contatadas mas painel não devolveu canais live',
      };
    }

    res.status(200).json(payload);
  } catch (err) {
    console.error('[live-tv]', err);
    res.status(500).json({
      error: err.message || 'Erro ao carregar canais',
      categories: [],
      channels: [],
      sourcesUsed: 0,
    });
  }
}

module.exports = handler;
