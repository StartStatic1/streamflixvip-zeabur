// api/admin-vip.js
// Hotfix valido para o server.js dar require() sem crash.
// Painel: acoes essenciais restauradas. Expandindo em seguida.

const SUPABASE_URL = 'https://gkujbjpvphuvrejpvvtz.supabase.co';

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  if (req.method === 'OPTIONS') { res.status(200).end(); return; }
  if (req.method !== 'POST') { res.status(405).json({ error: 'Method not allowed' }); return; }

  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!serviceKey) { res.status(500).json({ error: 'SUPABASE_SERVICE_ROLE_KEY nao configurada' }); return; }

  const authHeader = req.headers['authorization'] || '';
  const userToken = authHeader.replace('Bearer ', '').trim();
  if (!userToken) { res.status(401).json({ error: 'Token nao fornecido' }); return; }

  const userRes = await fetch(SUPABASE_URL + '/auth/v1/user', {
    headers: { apikey: serviceKey, Authorization: 'Bearer ' + userToken },
  });
  if (!userRes.ok) { res.status(401).json({ error: 'Token invalido' }); return; }
  const userJson = await userRes.json();
  const userId = userJson && userJson.id;
  if (!userId) { res.status(401).json({ error: 'Token invalido' }); return; }

  const adminRes = await fetch(
    SUPABASE_URL + '/rest/v1/vip_panel_admins?id=eq.' + encodeURIComponent(userId) + '&select=id',
    { headers: { apikey: serviceKey, Authorization: 'Bearer ' + serviceKey } },
  );
  const adminRows = await adminRes.json();
  if (!adminRes.ok || !Array.isArray(adminRows) || adminRows.length === 0) {
    res.status(403).json({ error: 'Acesso negado' });
    return;
  }

  let body = req.body;
  if (typeof body === 'string') {
    try { body = JSON.parse(body); } catch (e) { body = {}; }
  }
  const action = body && body.action;
  const svcHeaders = {
    apikey: serviceKey,
    Authorization: 'Bearer ' + serviceKey,
    'Content-Type': 'application/json',
  };

  if (action === 'list') {
    const codesRes = await fetch(SUPABASE_URL + '/rest/v1/vip_codes?select=*&order=created_at.desc&limit=200', { headers: svcHeaders });
    const rows = await codesRes.json();
    res.status(200).json({ codes: rows });
    return;
  }

  if (action === 'create') {
    const codes = body.codes;
    if (!Array.isArray(codes) || codes.length === 0) {
      res.status(400).json({ error: 'Informe os codigos' });
      return;
    }
    const r = await fetch(SUPABASE_URL + '/rest/v1/vip_codes', {
      method: 'POST',
      headers: Object.assign({}, svcHeaders, { Prefer: 'return=representation' }),
      body: JSON.stringify(codes),
    });
    const result = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao criar', detail: result }); return; }
    res.status(200).json({ created: result });
    return;
  }

  if (action === 'deactivate' || action === 'reactivate') {
    const code = body.code;
    if (!code) { res.status(400).json({ error: 'Informe o code' }); return; }
    const r = await fetch(SUPABASE_URL + '/rest/v1/vip_codes?code=eq.' + encodeURIComponent(code), {
      method: 'PATCH',
      headers: Object.assign({}, svcHeaders, { Prefer: 'return=representation' }),
      body: JSON.stringify({ is_active: action === 'reactivate' }),
    });
    const result = await r.json();
    res.status(200).json({ updated: result });
    return;
  }

  if (action === 'list-users') {
    const r = await fetch(
      SUPABASE_URL + '/rest/v1/vip_status?select=user_id,email,name,first_login_at,last_login_at,last_seen_at,expires_at,plan_label,last_code_used&order=last_login_at.desc&limit=500',
      { headers: svcHeaders },
    );
    const rows = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao listar usuarios', detail: rows }); return; }
    res.status(200).json({ users: rows });
    return;
  }

  if (action === 'list-iptv-sources') {
    const r = await fetch(
      SUPABASE_URL + '/rest/v1/iptv_sources?select=id,name,xtream_host,xtream_user,priority,is_active,source_type,sync_phase,sync_cursor,last_batch_at,last_synced_at,last_sync_stats&order=created_at.desc',
      { headers: svcHeaders },
    );
    const rows = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao listar fontes IPTV', detail: rows }); return; }
    res.status(200).json({ sources: rows });
    return;
  }

  if (action === 'list-live-tv-sources') {
    const r = await fetch(
      SUPABASE_URL + '/rest/v1/live_tv_sources?select=id,name,xtream_host,xtream_user,priority,is_active,created_at&order=priority.asc.nullslast',
      { headers: svcHeaders },
    );
    if (!r.ok) {
      const detail = await r.text();
      res.status(502).json({ error: 'Tabela live_tv_sources ausente', detail: detail });
      return;
    }
    res.status(200).json({ sources: await r.json() });
    return;
  }

  if (action === 'list-sources-filtered') {
    const search = body.search || '';
    const mediaType = body.mediaType || 'all';
    const status = body.status || 'all';
    const page = body.page || 1;
    const pageSize = body.pageSize || 30;
    const qs = ['select=id,tmdb_id,media_type,season,episode,title,poster_path,is_active,created_at'];
    if (mediaType === 'movie' || mediaType === 'tv') qs.push('media_type=eq.' + mediaType);
    if (search && String(search).trim()) {
      qs.push('title=ilike.*' + encodeURIComponent(String(search).trim()) + '*');
    }
    if (status === 'active') qs.push('is_active=eq.true');
    else if (status === 'inactive') qs.push('is_active=eq.false');
    qs.push('order=created_at.desc');
    const listRes = await fetch(SUPABASE_URL + '/rest/v1/vip_sources?' + qs.join('&') + '&limit=4000', { headers: svcHeaders });
    const rows = await listRes.json();
    if (!listRes.ok) { res.status(502).json({ error: 'Erro ao listar fontes', detail: rows }); return; }
    const groups = new Map();
    (rows || []).forEach(function (s) {
      const key = s.media_type + ':' + s.tmdb_id;
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key).push(s);
    });
    let groupList = Array.from(groups.entries()).map(function (entry) {
      const items = entry[1];
      const resolvedTitle = (items.find(function (s) { return s.title && s.title.trim(); }) || {}).title || '(sem titulo)';
      const activeCount = items.filter(function (s) { return s.is_active; }).length;
      return {
        tmdb_id: items[0].tmdb_id,
        media_type: items[0].media_type,
        title: resolvedTitle,
        poster_path: (items.find(function (s) { return s.poster_path; }) || {}).poster_path || null,
        sourceCount: items.length,
        activeCount: activeCount,
        inactiveCount: items.length - activeCount,
      };
    });
    if (status === 'active') groupList = groupList.filter(function (g) { return g.activeCount > 0; });
    else if (status === 'inactive') groupList = groupList.filter(function (g) { return g.activeCount === 0; });
    const totalGroups = groupList.length;
    const start = (Math.max(1, page) - 1) * pageSize;
    const pageItems = groupList.slice(start, start + pageSize);
    res.status(200).json({
      groups: pageItems,
      totalGroups: totalGroups,
      page: Math.max(1, page),
      hasMore: start + pageSize < totalGroups,
    });
    return;
  }

  if (action === 'list-sources-for-title') {
    const tmdbId = body.tmdbId;
    const mediaType = body.mediaType;
    if (!tmdbId || !mediaType) { res.status(400).json({ error: 'Informe tmdbId e mediaType' }); return; }
    const r = await fetch(
      SUPABASE_URL + '/rest/v1/vip_sources?tmdb_id=eq.' + encodeURIComponent(tmdbId) + '&media_type=eq.' + encodeURIComponent(mediaType) + '&select=id,tmdb_id,media_type,season,episode,title,poster_path,source_url,source_label,priority,is_active,created_at&order=season.asc,episode.asc,priority.desc',
      { headers: svcHeaders },
    );
    const rows = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao buscar fontes', detail: rows }); return; }
    res.status(200).json({ sources: rows || [] });
    return;
  }

  if (action === 'dashboard-stats') {
    async function countRows(table, filter) {
      filter = filter || '';
      const r = await fetch(SUPABASE_URL + '/rest/v1/' + table + '?select=id' + filter, {
        headers: Object.assign({}, svcHeaders, { Prefer: 'count=exact', Range: '0-0' }),
      });
      const range = r.headers.get('content-range');
      const total = range ? parseInt(range.split('/')[1], 10) : 0;
      return isNaN(total) ? 0 : total;
    }
    try {
      const totalVipSources = await countRows('vip_sources');
      const totalIptvSources = await countRows('iptv_sources', '&is_active=eq.true');
      const activeVipCodes = await countRows('vip_codes', '&is_active=eq.true');
      const totalUsers = await countRows('vip_status');
      const totalUnmatched = await countRows('iptv_unmatched_items');
      res.status(200).json({
        totalVipSources: totalVipSources,
        totalIptvSources: totalIptvSources,
        activeVipCodes: activeVipCodes,
        totalUsers: totalUsers,
        totalUnmatched: totalUnmatched,
        activeAds: 0,
        activeTvActivations: 0,
        totalMovies: await countRows('vip_sources', '&media_type=eq.movie'),
        totalSeries: 0,
        topSources: [],
      });
    } catch (err) {
      res.status(502).json({ error: 'Erro ao carregar estatisticas', detail: err.message });
    }
    return;
  }

  if (action === 'activations') {
    const statusRes = await fetch(
      SUPABASE_URL + '/rest/v1/vip_status?select=user_id,email,expires_at,plan_label,last_code_used&order=expires_at.desc&limit=500',
      { headers: svcHeaders },
    );
    const statusRows = await statusRes.json();
    if (!statusRes.ok) { res.status(502).json({ error: 'Erro vip_status', detail: statusRows }); return; }
    const now = new Date();
    const activations = (Array.isArray(statusRows) ? statusRows : []).map(function (u) {
      return {
        email: u.email || '(sem e-mail)',
        userId: u.user_id,
        vipAtivo: u.expires_at ? new Date(u.expires_at) > now : false,
        expiresAt: u.expires_at,
        planLabel: u.plan_label,
        origem: u.last_code_used ? ('Codigo: ' + u.last_code_used) : 'Desconhecida',
        codigoRelacionado: u.last_code_used || null,
        tvsAtivas: 0,
        tvs: [],
      };
    });
    res.status(200).json({ activations: activations });
    return;
  }

  res.status(501).json({
    error: 'Acao em restauracao: ' + (action || '(vazia)') + '. App/catalogo ok.',
  });
};
