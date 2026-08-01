// api/live-tv.js
//
// Lista canais ao vivo das fontes IPTV (até 3), agrupando por nome
// com múltiplas URLs de stream para fallback no player do app.
// Credenciais Xtream NUNCA saem daqui — o app só recebe nome, logo, categoria e URLs.

const SUPABASE_URL = process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';
const MAX_SOURCES = 3;

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
    throw new Error(`Supabase ${table}: ${r.status} ${body.slice(0, 200)}`);
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

  try {
    // 1) Fontes ativas com credenciais
    let sources = await sbSelect(
      serviceKey,
      'iptv_sources',
      'is_active=eq.true&select=id,name,priority,xtream_host,xtream_user,xtream_pass,source_type,is_active&order=priority.asc.nullslast&limit=20',
    );

    // 2) Fallback: se nenhuma ativa, tenta qualquer uma com host/user/pass
    //    (evita TV “morta” quando alguém desmarcou is_active por engano)
    if (!Array.isArray(sources) || sources.length === 0) {
      console.warn('[live-tv] nenhuma is_active=true — tentando todas as fontes');
      sources = await sbSelect(
        serviceKey,
        'iptv_sources',
        'select=id,name,priority,xtream_host,xtream_user,xtream_pass,source_type,is_active&order=priority.asc.nullslast&limit=20',
      );
    }

    if (!Array.isArray(sources)) sources = [];

    const withCreds = sources.filter(hasXtreamCreds).slice(0, MAX_SOURCES);

    if (!withCreds.length) {
      res.status(200).json({
        categories: [],
        channels: [],
        sourcesUsed: 0,
        diagnostic: {
          sourcesInDb: sources.length,
          withCredentials: 0,
          activeTrue: sources.filter((s) => s.is_active === true).length,
          hint: sources.length === 0
            ? 'Tabela iptv_sources vazia ou inacessível'
            : 'Fontes existem mas sem xtream_host/user/pass preenchidos',
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

    const categoryMap = new Map();
    for (const r of results) {
      for (const c of r.categories) {
        const key = normalizeName(c.name);
        if (!key) continue;
        if (!categoryMap.has(key)) {
          categoryMap.set(key, { id: key, name: c.name });
        }
      }
    }

    const channelMap = new Map();
    for (const r of results) {
      for (const s of r.streams) {
        const key = normalizeName(s.name);
        if (!key) continue;
        const catKey =
          normalizeName(
            r.categories.find((c) => c.id === s.categoryId)?.name || 'Outros',
          ) || 'outros';

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
    const filteredCategories = categories.filter((c) => usedCats.has(c.id));

    const sourcesUsed = results.filter((r) => r.streams.length > 0).length;

    const payload = {
      categories: channels.length
        ? [{ id: 'all', name: 'Todos' }, ...filteredCategories]
        : [],
      channels,
      sourcesUsed,
    };

    if (!channels.length) {
      payload.diagnostic = {
        triedSources: withCreds.map((s) => s.name),
        perSource: results.map((r) => ({
          name: r.sourceName,
          streams: r.streams.length,
          categories: r.categories.length,
          skipReason: r.skipReason || null,
        })),
        hint: 'Fontes contatadas mas painel Xtream não devolveu canais live (offline, user expirado ou bloqueio)',
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
