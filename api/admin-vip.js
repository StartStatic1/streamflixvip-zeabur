// api/admin-vip.js
// API admin VIP — restaurada (nucleo + IPTV + live TV + fontes + ads basico)
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

  const userRes = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
    headers: { apikey: serviceKey, Authorization: `Bearer ${userToken}` },
  });
  if (!userRes.ok) { res.status(401).json({ error: 'Token invalido' }); return; }
  const userJson = await userRes.json();
  const userId = userJson && userJson.id;
  if (!userId) { res.status(401).json({ error: 'Token invalido' }); return; }

  const adminRes = await fetch(
    `${SUPABASE_URL}/rest/v1/vip_panel_admins?id=eq.${encodeURIComponent(userId)}&select=id`,
    { headers: { apikey: serviceKey, Authorization: `Bearer ${serviceKey}` } },
  );
  const adminRows = await adminRes.json();
  if (!adminRes.ok || !Array.isArray(adminRows) || adminRows.length === 0) {
    res.status(403).json({ error: 'Acesso negado' });
    return;
  }

  let body = req.body;
  if (typeof body === 'string') { try { body = JSON.parse(body); } catch (e) { body = {}; } }
  const action = body && body.action;
  const svcHeaders = {
    apikey: serviceKey,
    Authorization: `Bearer ${serviceKey}`,
    'Content-Type': 'application/json',
  };

  // Painel manda sourceId / isActive; backend antigo lia id / is_active.
  // Aceita os dois nomes pra exclusão/toggle/update nunca falharem em silêncio.
  function sourceRowId(b) {
    return b.sourceId != null ? b.sourceId : b.id;
  }
  function sourceActiveFlag(b) {
    if (b.isActive !== undefined) return !!b.isActive;
    if (b.is_active !== undefined) return !!b.is_active;
    return undefined;
  }

  if (action === 'list') {
    const [codesRes, tvRes] = await Promise.all([
      fetch(`${SUPABASE_URL}/rest/v1/vip_codes?select=*&order=created_at.desc&limit=200`, { headers: svcHeaders }),
      fetch(`${SUPABASE_URL}/rest/v1/tv_activations?select=code,device_label&is_active=eq.true`, { headers: svcHeaders }),
    ]);
    const rows = await codesRes.json();
    const tvRows = await tvRes.json();
    const tvCountByCode = new Map();
    if (Array.isArray(tvRows)) {
      for (const t of tvRows) tvCountByCode.set(t.code, (tvCountByCode.get(t.code) || 0) + 1);
    }
    const rowsWithTv = Array.isArray(rows)
      ? rows.map((c) => ({ ...c, tv_activations_count: tvCountByCode.get(c.code) || 0 }))
      : rows;
    res.status(200).json({ codes: rowsWithTv });
    return;
  }

  if (action === 'create') {
    const { codes } = body;
    if (!Array.isArray(codes) || codes.length === 0) {
      res.status(400).json({ error: 'Informe os codigos' });
      return;
    }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_codes`, {
      method: 'POST',
      headers: { ...svcHeaders, Prefer: 'return=representation' },
      body: JSON.stringify(codes),
    });
    const result = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao criar', detail: result }); return; }
    res.status(200).json({ created: result });
    return;
  }

  if (action === 'deactivate' || action === 'reactivate') {
    const { code } = body;
    if (!code) { res.status(400).json({ error: 'Informe o code' }); return; }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_codes?code=eq.${encodeURIComponent(code)}`, {
      method: 'PATCH',
      headers: { ...svcHeaders, Prefer: 'return=representation' },
      body: JSON.stringify({ is_active: action === 'reactivate' }),
    });
    res.status(200).json({ updated: await r.json() });
    return;
  }

  if (action === 'list-users') {
    const r = await fetch(
      `${SUPABASE_URL}/rest/v1/vip_status?select=user_id,email,name,first_login_at,last_login_at,last_seen_at,expires_at,plan_label,last_code_used&order=last_login_at.desc&limit=500`,
      { headers: svcHeaders },
    );
    const rows = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao listar usuarios', detail: rows }); return; }
    res.status(200).json({ users: rows });
    return;
  }

  if (action === 'activations') {
    const [statusRes, codesRes, tvRes] = await Promise.all([
      fetch(`${SUPABASE_URL}/rest/v1/vip_status?select=user_id,email,expires_at,plan_label,last_code_used&order=expires_at.desc&limit=500`, { headers: svcHeaders }),
      fetch(`${SUPABASE_URL}/rest/v1/vip_codes?select=code,used_by,plan_label,is_active`, { headers: svcHeaders }),
      fetch(`${SUPABASE_URL}/rest/v1/tv_activations?select=code,device_id,device_label,expires_at,is_active`, { headers: svcHeaders }),
    ]);
    const statusRows = await statusRes.json();
    const codesRows = await codesRes.json();
    const tvRows = await tvRes.json();
    if (!statusRes.ok) { res.status(502).json({ error: 'Erro vip_status', detail: statusRows }); return; }
    const codesByUser = new Map();
    if (Array.isArray(codesRows)) {
      for (const c of codesRows) if (c.used_by) codesByUser.set(c.used_by, c.code);
    }
    const tvByCode = new Map();
    if (Array.isArray(tvRows)) {
      for (const t of tvRows) {
        if (!t.is_active) continue;
        if (!tvByCode.has(t.code)) tvByCode.set(t.code, []);
        tvByCode.get(t.code).push({ deviceId: t.device_id, deviceLabel: t.device_label, expiresAt: t.expires_at });
      }
    }
    const now = new Date();
    const activations = (Array.isArray(statusRows) ? statusRows : []).map((u) => {
      let origem = 'Desconhecida';
      let codigoRelacionado = null;
      if (u.last_code_used && String(u.last_code_used).startsWith('PIX-MP-')) origem = 'Pagamento PIX automatico';
      else if (u.last_code_used) { origem = 'Codigo: ' + u.last_code_used; codigoRelacionado = u.last_code_used; }
      else if (codesByUser.has(u.user_id)) {
        codigoRelacionado = codesByUser.get(u.user_id);
        origem = 'Codigo: ' + codigoRelacionado;
      }
      const tvs = codigoRelacionado ? (tvByCode.get(codigoRelacionado) || []) : [];
      return {
        email: u.email || '(sem e-mail)',
        userId: u.user_id,
        vipAtivo: u.expires_at ? new Date(u.expires_at) > now : false,
        expiresAt: u.expires_at,
        planLabel: u.plan_label,
        origem,
        codigoRelacionado,
        tvsAtivas: tvs.length,
        tvs,
      };
    });
    res.status(200).json({ activations });
    return;
  }

  if (action === 'list-redemptions') {
    const { userId: uid } = body;
    if (!uid) { res.status(400).json({ error: 'Informe userId' }); return; }
    const r = await fetch(
      `${SUPABASE_URL}/rest/v1/vip_redemptions?user_id=eq.${encodeURIComponent(uid)}&select=*&order=redeemed_at.desc`,
      { headers: svcHeaders },
    );
    const rows = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro historico', detail: rows }); return; }
    res.status(200).json({ users: rows });
    return;
  }

  if (action === 'list-sources-for') {
    const { mediaType, ids } = body;
    if (!mediaType || !Array.isArray(ids) || ids.length === 0) {
      res.status(200).json({ ids: [] });
      return;
    }
    const r = await fetch(
      `${SUPABASE_URL}/rest/v1/vip_sources?media_type=eq.${encodeURIComponent(mediaType)}&tmdb_id=in.(${ids.join(',')})&select=tmdb_id`,
      { headers: svcHeaders },
    );
    const rows = await r.json();
    if (!r.ok) { res.status(200).json({ ids: [] }); return; }
    res.status(200).json({ ids: (rows || []).map((x) => x.tmdb_id) });
    return;
  }

  if (action === 'list-sources-filtered') {
    const search = body.search || '';
    const mediaType = body.mediaType || 'all';
    const status = body.status || 'all';
    const sort = body.sort || 'recent';
    const page = body.page || 1;
    const pageSize = body.pageSize || 30;
    const qs = ['select=id,tmdb_id,media_type,season,episode,title,poster_path,is_active,created_at'];
    if (mediaType === 'movie' || mediaType === 'tv') qs.push('media_type=eq.' + mediaType);
    if (search && String(search).trim()) qs.push('title=ilike.*' + encodeURIComponent(String(search).trim()) + '*');
    if (status === 'active') qs.push('is_active=eq.true');
    else if (status === 'inactive') qs.push('is_active=eq.false');
    qs.push('order=created_at.desc');
    const listRes = await fetch(`${SUPABASE_URL}/rest/v1/vip_sources?${qs.join('&')}&limit=4000`, { headers: svcHeaders });
    const rows = await listRes.json();
    if (!listRes.ok) { res.status(502).json({ error: 'Erro ao listar fontes', detail: rows }); return; }
    const groups = new Map();
    (rows || []).forEach((s) => {
      const key = s.media_type + ':' + s.tmdb_id;
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key).push(s);
    });
    let groupList = [...groups.entries()].map(([, items]) => {
      const resolvedTitle = (items.find((s) => s.title && s.title.trim()) || {}).title || '(sem titulo)';
      const activeCount = items.filter((s) => s.is_active).length;
      const latest = Math.max(...items.map((s) => new Date(s.created_at).getTime()));
      return {
        tmdb_id: items[0].tmdb_id,
        media_type: items[0].media_type,
        title: resolvedTitle,
        poster_path: (items.find((s) => s.poster_path) || {}).poster_path || null,
        sourceCount: items.length,
        activeCount,
        inactiveCount: items.length - activeCount,
        latest,
      };
    });
    if (status === 'active') groupList = groupList.filter((g) => g.activeCount > 0);
    else if (status === 'inactive') groupList = groupList.filter((g) => g.activeCount === 0);
    const totalGroups = groupList.length;
    if (sort === 'az') groupList.sort((a, b) => a.title.localeCompare(b.title, 'pt-BR'));
    else if (sort === 'za') groupList.sort((a, b) => b.title.localeCompare(a.title, 'pt-BR'));
    else if (sort === 'sources') groupList.sort((a, b) => b.sourceCount - a.sourceCount);
    else groupList.sort((a, b) => b.latest - a.latest);
    const start = (Math.max(1, page) - 1) * pageSize;
    const pageItems = groupList.slice(start, start + pageSize);

    if (pageItems.length > 0) {
      const ids = [...new Set(pageItems.map((g) => g.tmdb_id))];
      const countRes = await fetch(
        `${SUPABASE_URL}/rest/v1/vip_sources?tmdb_id=in.(${ids.join(',')})&select=tmdb_id,media_type,is_active,title,poster_path`,
        { headers: svcHeaders },
      );
      if (countRes.ok) {
        const allForPage = await countRes.json();
        const byKey = new Map();
        (allForPage || []).forEach((s) => {
          const key = s.media_type + ':' + s.tmdb_id;
          if (!byKey.has(key)) byKey.set(key, []);
          byKey.get(key).push(s);
        });
        pageItems.forEach((g) => {
          const items = byKey.get(g.media_type + ':' + g.tmdb_id) || [];
          if (items.length === 0) return;
          g.sourceCount = items.length;
          g.activeCount = items.filter((s) => s.is_active).length;
          g.inactiveCount = items.length - g.activeCount;
          const withTitle = items.find((s) => s.title && String(s.title).trim());
          if (withTitle) g.title = withTitle.title;
          const withPoster = items.find((s) => s.poster_path);
          if (withPoster) g.poster_path = withPoster.poster_path;
        });
      }
    }

    res.status(200).json({
      groups: pageItems.map((g) => ({
        tmdb_id: g.tmdb_id, media_type: g.media_type, title: g.title,
        poster_path: g.poster_path, sourceCount: g.sourceCount,
        activeCount: g.activeCount, inactiveCount: g.inactiveCount,
      })),
      totalGroups,
      page: Math.max(1, page),
      hasMore: start + pageSize < totalGroups,
    });
    return;
  }

  if (action === 'list-sources-for-title') {
    const { tmdbId, mediaType } = body;
    if (!tmdbId || !mediaType) { res.status(400).json({ error: 'Informe tmdbId e mediaType' }); return; }
    const r = await fetch(
      `${SUPABASE_URL}/rest/v1/vip_sources?tmdb_id=eq.${encodeURIComponent(tmdbId)}&media_type=eq.${encodeURIComponent(mediaType)}&select=id,tmdb_id,media_type,season,episode,title,poster_path,source_url,source_label,priority,is_active,created_at&order=season.asc,episode.asc,priority.desc`,
      { headers: svcHeaders },
    );
    const rows = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro fontes do titulo', detail: rows }); return; }
    res.status(200).json({ sources: rows || [] });
    return;
  }

  if (action === 'create-source') {
    const { tmdb_id, media_type, title, poster_path, season, episode, source_url, source_label, priority } = body;
    if (!tmdb_id || !media_type || !source_url) {
      res.status(400).json({ error: 'Dados incompletos para criar a fonte' });
      return;
    }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_sources`, {
      method: 'POST',
      headers: { ...svcHeaders, Prefer: 'return=representation' },
      body: JSON.stringify({
        tmdb_id, media_type, title, poster_path, season, episode,
        source_url, source_label, priority, created_by: userId,
      }),
    });
    const result = await r.json();
    if (!r.ok) {
      const msg = JSON.stringify(result).includes('duplicate') ? 'duplicate key' : (result && result.message) || 'Erro ao criar fonte';
      res.status(409).json({ error: msg, detail: result });
      return;
    }
    res.status(200).json({ created: result });
    return;
  }

  if (action === 'update-source') {
    const id = sourceRowId(body);
    if (!id) { res.status(400).json({ error: 'Informe id' }); return; }
    const patch = {};
    if (body.source_url !== undefined) patch.source_url = body.source_url;
    if (body.source_label !== undefined) patch.source_label = body.source_label;
    if (body.priority !== undefined) patch.priority = body.priority;
    if (body.season !== undefined) patch.season = body.season;
    if (body.episode !== undefined) patch.episode = body.episode;
    if (body.title !== undefined) patch.title = body.title;
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_sources?id=eq.${encodeURIComponent(id)}`, {
      method: 'PATCH',
      headers: { ...svcHeaders, Prefer: 'return=representation' },
      body: JSON.stringify(patch),
    });
    const result = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao atualizar fonte', detail: result }); return; }
    res.status(200).json({ updated: result });
    return;
  }

  if (action === 'toggle-source') {
    const id = sourceRowId(body);
    const isActive = sourceActiveFlag(body);
    if (!id) { res.status(400).json({ error: 'Informe id' }); return; }
    if (isActive === undefined) { res.status(400).json({ error: 'Informe isActive' }); return; }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_sources?id=eq.${encodeURIComponent(id)}`, {
      method: 'PATCH',
      headers: { ...svcHeaders, Prefer: 'return=representation' },
      body: JSON.stringify({ is_active: isActive }),
    });
    if (!r.ok) { res.status(502).json({ error: 'Erro ao alternar fonte', detail: await r.text() }); return; }
    res.status(200).json({ success: true });
    return;
  }

  if (action === 'delete-source') {
    const id = sourceRowId(body);
    if (!id) { res.status(400).json({ error: 'Informe id' }); return; }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_sources?id=eq.${encodeURIComponent(id)}`, {
      method: 'DELETE',
      headers: svcHeaders,
    });
    if (!r.ok) { res.status(502).json({ error: 'Erro ao excluir fonte', detail: await r.text() }); return; }
    res.status(200).json({ success: true });
    return;
  }

  if (action === 'dashboard-stats') {
    async function countRows(table, filter) {
      filter = filter || '';
      const r = await fetch(`${SUPABASE_URL}/rest/v1/${table}?select=id${filter}`, {
        headers: { ...svcHeaders, Prefer: 'count=exact', Range: '0-0' },
      });
      const range = r.headers.get('content-range');
      const total = range ? parseInt(range.split('/')[1], 10) : 0;
      return isNaN(total) ? 0 : total;
    }
    try {
      res.status(200).json({
        totalVipSources: await countRows('vip_sources'),
        totalIptvSources: await countRows('iptv_sources', '&is_active=eq.true'),
        activeVipCodes: await countRows('vip_codes', '&is_active=eq.true'),
        totalUsers: await countRows('vip_status'),
        totalUnmatched: await countRows('iptv_unmatched_items'),
        activeAds: await countRows('vip_ads', '&is_active=eq.true'),
        activeTvActivations: await countRows('tv_activations', '&is_active=eq.true'),
        totalMovies: await countRows('vip_sources', '&media_type=eq.movie'),
        totalSeries: await countRows('vip_sources', '&media_type=eq.tv'),
        topSources: [],
      });
    } catch (err) {
      res.status(502).json({ error: 'Erro stats', detail: err.message });
    }
    return;
  }

  if (action === 'list-iptv-sources') {
    const r = await fetch(
      `${SUPABASE_URL}/rest/v1/iptv_sources?select=id,name,xtream_host,xtream_user,priority,is_active,source_type,sync_phase,sync_cursor,last_batch_at,last_synced_at,last_sync_stats,xtream_sync_cursor,xtream_series_sync_cursor,xtream_last_batch_at,xtream_last_synced_at,xtream_last_sync_stats,xtream_series_last_sync_stats&order=created_at.desc`,
      { headers: svcHeaders },
    );
    const rows = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro IPTV', detail: rows }); return; }
    res.status(200).json({ sources: rows });
    return;
  }

  if (action === 'create-iptv-source' || action === 'create-xtream-source') {
    const name = body.name;
    const xtreamHost = body.xtreamHost || body.xtream_host;
    const xtreamUser = body.xtreamUser || body.xtream_user;
    const xtreamPass = body.xtreamPass || body.xtream_pass;
    const priority = body.priority;
    if (!name || !xtreamHost || !xtreamUser || !xtreamPass) {
      res.status(400).json({ error: 'Informe name, host, user e pass' });
      return;
    }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/iptv_sources`, {
      method: 'POST',
      headers: { ...svcHeaders, Prefer: 'return=representation' },
      body: JSON.stringify({
        name: String(name).trim(),
        xtream_host: String(xtreamHost).trim().replace(/\/+$/, ''),
        xtream_user: String(xtreamUser).trim(),
        xtream_pass: String(xtreamPass).trim(),
        priority: Number.isFinite(Number(priority)) ? Number(priority) : 10,
        is_active: true,
        source_type: action === 'create-xtream-source' ? 'xtream_api' : (body.source_type || 'm3u'),
      }),
    });
    const result = await r.json();
    if (!r.ok) {
      const detail = typeof result === 'string' ? result : (result && (result.message || result.error_description || result.hint || JSON.stringify(result)));
      const msg = String(detail || '');
      if (msg.includes('duplicate') || msg.includes('unique')) {
        res.status(409).json({ error: 'Ja existe uma fonte IPTV com esse nome. Use outro nome ou edite a existente.' });
        return;
      }
      res.status(502).json({ error: 'Erro ao criar fonte IPTV: ' + msg.slice(0, 180) });
      return;
    }
    res.status(200).json({ success: true, source: Array.isArray(result) ? result[0] : result });
    return;
  }

  if (action === 'toggle-iptv-source') {
    const { sourceId, isActive } = body;
    if (!sourceId) { res.status(400).json({ error: 'Informe sourceId' }); return; }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/iptv_sources?id=eq.${encodeURIComponent(sourceId)}`, {
      method: 'PATCH',
      headers: svcHeaders,
      body: JSON.stringify({ is_active: !!isActive }),
    });
    if (!r.ok) { res.status(502).json({ error: 'Erro toggle IPTV', detail: await r.text() }); return; }
    res.status(200).json({ success: true });
    return;
  }

  // Apaga vip_sources por label em LOTES (sem return=representation).
  // DELETE unico de 100k+ linhas + Prefer representation estoura timeout Vercel/PostgREST.
  async function deleteVipSourcesByLabels(labels) {
    const unique = [...new Set((labels || []).map((l) => String(l || '').trim()).filter(Boolean))];
    let total = 0;
    const BATCH = 1500;
    const MAX_ROUNDS = 80; // 80 * 1500 = 120k linhas por invocacao
    for (const label of unique) {
      for (let round = 0; round < MAX_ROUNDS; round++) {
        const listRes = await fetch(
          `${SUPABASE_URL}/rest/v1/vip_sources?source_label=eq.${encodeURIComponent(label)}&select=id&limit=${BATCH}`,
          { headers: svcHeaders },
        );
        if (!listRes.ok) {
          const detail = await listRes.text();
          throw new Error('list vip_sources: ' + detail.slice(0, 200));
        }
        const rows = await listRes.json();
        if (!Array.isArray(rows) || rows.length === 0) break;
        const ids = rows.map((r) => r.id).filter(Boolean);
        if (ids.length === 0) break;
        const delRes = await fetch(
          `${SUPABASE_URL}/rest/v1/vip_sources?id=in.(${ids.join(',')})`,
          { method: 'DELETE', headers: { ...svcHeaders, Prefer: 'return=minimal' } },
        );
        if (!delRes.ok) {
          const detail = await delRes.text();
          throw new Error('delete vip_sources: ' + detail.slice(0, 200));
        }
        total += ids.length;
        if (rows.length < BATCH) break;
      }
    }
    return total;
  }

  // Excluir fonte IPTV de verdade: remove a linha em iptv_sources E todas as
  // entradas em vip_sources cujo source_label = nome da fonte (é assim que o
  // sync grava: source_label = source.name). Sem isso o host some da aba IPTV
  // mas continua no app e na aba Filmes (bug do mnba.shop).
  if (action === 'delete-iptv-source') {
    const { sourceId } = body;
    if (!sourceId) { res.status(400).json({ error: 'Informe sourceId' }); return; }

    const getRes = await fetch(
      `${SUPABASE_URL}/rest/v1/iptv_sources?id=eq.${encodeURIComponent(sourceId)}&select=id,name`,
      { headers: svcHeaders },
    );
    const getRows = await getRes.json();
    if (!getRes.ok || !Array.isArray(getRows) || getRows.length === 0) {
      res.status(404).json({ error: 'Fonte IPTV nao encontrada (ja foi excluida?)' });
      return;
    }
    const sourceName = String(getRows[0].name || '').trim();

    // Labels possiveis: nome exato + StreamFlix.nome (sync antigo/novo)
    const labels = [sourceName];
    if (sourceName && !sourceName.startsWith('StreamFlix.')) {
      labels.push('StreamFlix.' + sourceName);
    }
    if (sourceName.startsWith('StreamFlix.')) {
      labels.push(sourceName.slice('StreamFlix.'.length));
    }

    let vipDeleted = 0;
    try {
      vipDeleted = await deleteVipSourcesByLabels(labels);
    } catch (err) {
      res.status(502).json({
        error: 'Erro ao apagar fontes dos filmes (vip_sources)',
        detail: String(err && err.message ? err.message : err).slice(0, 300),
      });
      return;
    }

    // 2) Remove itens nao-match ligados a essa fonte
    await fetch(
      `${SUPABASE_URL}/rest/v1/iptv_unmatched_items?source_id=eq.${encodeURIComponent(sourceId)}`,
      { method: 'DELETE', headers: { ...svcHeaders, Prefer: 'return=minimal' } },
    ).catch(() => {});

    // 3) Remove a propria fonte IPTV
    const r = await fetch(`${SUPABASE_URL}/rest/v1/iptv_sources?id=eq.${encodeURIComponent(sourceId)}`, {
      method: 'DELETE',
      headers: { ...svcHeaders, Prefer: 'return=minimal' },
    });
    if (!r.ok) { res.status(502).json({ error: 'Erro excluir IPTV', detail: await r.text() }); return; }

    res.status(200).json({ success: true, vipSourcesDeleted: vipDeleted, sourceName, labelsTried: labels });
    return;
  }

  // Limpa restos orfaos: vip_sources de um host ja morto (ex: excluiu IPTV
  // antes deste fix). Uso: action purge-source-label + sourceLabel.
  if (action === 'purge-source-label') {
    const label = (body.sourceLabel || body.source_label || '').trim();
    if (!label) { res.status(400).json({ error: 'Informe sourceLabel' }); return; }
    const labels = [label];
    if (!label.startsWith('StreamFlix.')) labels.push('StreamFlix.' + label);
    else labels.push(label.slice('StreamFlix.'.length));
    let deleted = 0;
    try {
      deleted = await deleteVipSourcesByLabels(labels);
    } catch (err) {
      res.status(502).json({
        error: 'Erro ao limpar vip_sources',
        detail: String(err && err.message ? err.message : err).slice(0, 300),
      });
      return;
    }
    res.status(200).json({ success: true, deleted, sourceLabel: label });
    return;
  }

  if (action === 'update-iptv-source-priority') {
    const { sourceId, priority } = body;
    if (!sourceId) { res.status(400).json({ error: 'Informe sourceId' }); return; }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/iptv_sources?id=eq.${encodeURIComponent(sourceId)}`, {
      method: 'PATCH',
      headers: svcHeaders,
      body: JSON.stringify({ priority: Number(priority) || 10 }),
    });
    if (!r.ok) { res.status(502).json({ error: 'Erro priority', detail: await r.text() }); return; }
    res.status(200).json({ success: true });
    return;
  }

  if (action === 'list-live-tv-sources') {
    const r = await fetch(
      `${SUPABASE_URL}/rest/v1/live_tv_sources?select=id,name,xtream_host,xtream_user,priority,is_active,created_at&order=priority.asc.nullslast`,
      { headers: svcHeaders },
    );
    if (!r.ok) {
      res.status(502).json({ error: 'Tabela live_tv_sources ausente', detail: await r.text() });
      return;
    }
    res.status(200).json({ sources: await r.json() });
    return;
  }

  if (action === 'create-live-tv-source') {
    const name = body.name;
    const xtreamHost = body.xtreamHost || body.xtream_host;
    const xtreamUser = body.xtreamUser || body.xtream_user;
    const xtreamPass = body.xtreamPass || body.xtream_pass;
    const priority = body.priority;
    if (!name || !xtreamHost || !xtreamUser || !xtreamPass) {
      res.status(400).json({ error: 'Informe name, host, user e pass' });
      return;
    }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/live_tv_sources`, {
      method: 'POST',
      headers: { ...svcHeaders, Prefer: 'return=representation' },
      body: JSON.stringify({
        name: String(name).trim(),
        xtream_host: String(xtreamHost).trim().replace(/\/+$/, ''),
        xtream_user: String(xtreamUser).trim(),
        xtream_pass: String(xtreamPass).trim(),
        priority: Number.isFinite(Number(priority)) ? Number(priority) : 10,
        is_active: true,
      }),
    });
    const liveBody = await r.text();
    if (!r.ok) {
      const msg = liveBody || '';
      if (msg.includes('duplicate') || msg.includes('unique')) {
        res.status(409).json({ error: 'Ja existe fonte de TV com esse nome.' });
        return;
      }
      res.status(502).json({ error: 'Erro criar live TV: ' + msg.slice(0, 180) });
      return;
    }
    let liveJson;
    try { liveJson = JSON.parse(liveBody); } catch (_) { liveJson = []; }
    res.status(200).json({ success: true, source: Array.isArray(liveJson) ? liveJson[0] : liveJson });
    return;
  }

  if (action === 'toggle-live-tv-source') {
    const { sourceId, isActive } = body;
    if (!sourceId) { res.status(400).json({ error: 'Informe sourceId' }); return; }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/live_tv_sources?id=eq.${encodeURIComponent(sourceId)}`, {
      method: 'PATCH',
      headers: svcHeaders,
      body: JSON.stringify({ is_active: !!isActive }),
    });
    if (!r.ok) { res.status(502).json({ error: 'Erro toggle live TV', detail: await r.text() }); return; }
    res.status(200).json({ success: true });
    return;
  }

  if (action === 'delete-live-tv-source') {
    const { sourceId } = body;
    if (!sourceId) { res.status(400).json({ error: 'Informe sourceId' }); return; }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/live_tv_sources?id=eq.${encodeURIComponent(sourceId)}`, {
      method: 'DELETE',
      headers: svcHeaders,
    });
    if (!r.ok) { res.status(502).json({ error: 'Erro excluir live TV', detail: await r.text() }); return; }
    res.status(200).json({ success: true });
    return;
  }

  if (action === 'list-ads') {
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_ads?select=*&order=created_at.desc`, { headers: svcHeaders });
    const rows = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ads', detail: rows }); return; }
    res.status(200).json({ ads: rows });
    return;
  }

  if (action === 'create-ad') {
    const payload = {
      title: body.title,
      image_url: body.image_url || body.imageUrl,
      link_url: body.link_url || body.linkUrl,
      is_active: body.is_active !== false,
      placement: body.placement || 'home',
    };
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_ads`, {
      method: 'POST',
      headers: { ...svcHeaders, Prefer: 'return=representation' },
      body: JSON.stringify(payload),
    });
    const result = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro criar ad', detail: result }); return; }
    res.status(200).json({ created: result });
    return;
  }

  if (action === 'toggle-ad') {
    const { id, is_active } = body;
    if (!id) { res.status(400).json({ error: 'Informe id' }); return; }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_ads?id=eq.${encodeURIComponent(id)}`, {
      method: 'PATCH',
      headers: svcHeaders,
      body: JSON.stringify({ is_active: !!is_active }),
    });
    if (!r.ok) { res.status(502).json({ error: 'Erro toggle ad', detail: await r.text() }); return; }
    res.status(200).json({ success: true });
    return;
  }

  if (action === 'delete-ad') {
    const { id } = body;
    if (!id) { res.status(400).json({ error: 'Informe id' }); return; }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_ads?id=eq.${encodeURIComponent(id)}`, {
      method: 'DELETE',
      headers: svcHeaders,
    });
    if (!r.ok) { res.status(502).json({ error: 'Erro excluir ad', detail: await r.text() }); return; }
    res.status(200).json({ success: true });
    return;
  }

  if (action === 'cleanup-storage') {
    const cutoff = new Date(Date.now() - 14 * 24 * 60 * 60 * 1000).toISOString();
    const del = await fetch(
      `${SUPABASE_URL}/rest/v1/iptv_unmatched_items?created_at=lt.${encodeURIComponent(cutoff)}`,
      { method: 'DELETE', headers: { ...svcHeaders, Prefer: 'return=representation' } },
    );
    let unmatchedDeleted = 0;
    if (del.ok) {
      try {
        const rows = await del.json();
        unmatchedDeleted = Array.isArray(rows) ? rows.length : 0;
      } catch (_) {}
    }
    let enforceOk = false;
    try {
      const en = await fetch(`${SUPABASE_URL}/rest/v1/rpc/enforce_max_sources_per_episode`, {
        method: 'POST',
        headers: svcHeaders,
        body: JSON.stringify({ max_count: 2 }),
      });
      enforceOk = en.ok;
    } catch (_) {}
    res.status(200).json({ success: true, unmatchedDeleted, enforceOk });
    return;
  }

  res.status(501).json({
    error: 'Acao ainda nao restaurada: ' + (action || '(vazia)') + '. Nucleo do painel OK.',
  });
};
