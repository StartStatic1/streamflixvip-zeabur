// api/admin-vip.js
// IMPORTANTE: nunca substituir por PLACEHOLDER_WILL_FAIL — server.js require() no boot.
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

  function sourceRowId(b) {
    return b.sourceId != null ? b.sourceId : b.id;
  }
  function sourceActiveFlag(b) {
    if (b.isActive !== undefined) return !!b.isActive;
    if (b.is_active !== undefined) return !!b.is_active;
    return undefined;
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
    const start = (Math.max(1, page) - 1) * Math.max(1, pageSize);
    const pageItems = groupList.slice(start, start + Math.max(1, pageSize));
    res.status(200).json({ groups: pageItems, total: totalGroups, page: Math.max(1, page), pageSize: Math.max(1, pageSize) });
    return;
  }

  if (action === 'list-sources-for-title') {
    const { tmdbId, mediaType } = body;
    if (!tmdbId || !mediaType) { res.status(400).json({ error: 'Informe tmdbId e mediaType' }); return; }
    const r = await fetch(
      `${SUPABASE_URL}/rest/v1/vip_sources?tmdb_id=eq.${encodeURIComponent(tmdbId)}&media_type=eq.${encodeURIComponent(mediaType)}&select=*&order=priority.asc`,
      { headers: svcHeaders },
    );
    const rows = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao listar fontes do titulo', detail: rows }); return; }
    res.status(200).json({ sources: rows });
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
    const found = Array.isArray(rows) ? [...new Set(rows.map((x) => x.tmdb_id))] : [];
    res.status(200).json({ ids: found });
    return;
  }

  if (action === 'create-source') {
    const row = body.source || body;
    if (!row || row.tmdb_id == null || !row.media_type || !row.source_url) {
      res.status(400).json({ error: 'Informe tmdb_id, media_type e source_url' }); return;
    }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_sources`, {
      method: 'POST',
      headers: { ...svcHeaders, Prefer: 'return=representation' },
      body: JSON.stringify(row),
    });
    const result = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao criar fonte', detail: result }); return; }
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
    const is_active = sourceActiveFlag(body);
    if (!id) { res.status(400).json({ error: 'Informe id' }); return; }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_sources?id=eq.${encodeURIComponent(id)}`, {
      method: 'PATCH',
      headers: { ...svcHeaders, Prefer: 'return=representation' },
      body: JSON.stringify({ is_active: !!is_active }),
    });
    if (!r.ok) { res.status(502).json({ error: 'Erro ao alternar fonte', detail: await r.text() }); return; }
    res.status(200).json({ success: true });
    return;
  }

  if (action === 'delete-source') {
    const id = sourceRowId(body);
    if (!id) { res.status(400).json({ error: 'Informe id' }); return; }
    let row = null;
    try {
      const getR = await fetch(`${SUPABASE_URL}/rest/v1/vip_sources?id=eq.${encodeURIComponent(id)}&select=tmdb_id,media_type,source_label,season,episode&limit=1`, { headers: svcHeaders });
      const rows = await getR.json();
      if (Array.isArray(rows) && rows[0]) row = rows[0];
    } catch (e) {}
    if (row && row.tmdb_id != null && row.source_label) {
      try {
        await fetch(`${SUPABASE_URL}/rest/v1/vip_source_blocks?on_conflict=tmdb_id,media_type,source_label,season,episode`, {
          method: 'POST',
          headers: { ...svcHeaders, Prefer: 'resolution=merge-duplicates,return=minimal' },
          body: JSON.stringify({
            tmdb_id: row.tmdb_id,
            media_type: row.media_type || 'movie',
            source_label: row.source_label,
            season: row.season == null ? 0 : row.season,
            episode: row.episode == null ? 0 : row.episode,
          }),
        });
      } catch (e) {}
    }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_sources?id=eq.${encodeURIComponent(id)}`, {
      method: 'DELETE',
      headers: svcHeaders,
    });
    if (!r.ok) { res.status(502).json({ error: 'Erro ao excluir fonte', detail: await r.text() }); return; }
    res.status(200).json({ success: true, blocked: !!(row && row.tmdb_id) });
    return;
  }

  if (action === 'list-iptv-sources') {
    const r = await fetch(`${SUPABASE_URL}/rest/v1/iptv_sources?select=*&order=priority.asc`, { headers: svcHeaders });
    const rows = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao listar IPTV', detail: rows }); return; }
    res.status(200).json({ sources: rows });
    return;
  }

  if (action === 'create-iptv-source' || action === 'create-xtream-source') {
    const row = {
      name: body.name,
      host: body.host,
      username: body.username,
      password: body.password,
      priority: body.priority != null ? body.priority : 5,
      is_active: true,
      source_type: action === 'create-xtream-source' ? 'xtream_api' : (body.source_type || 'm3u'),
    };
    if (!row.name || !row.host) { res.status(400).json({ error: 'Informe name e host' }); return; }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/iptv_sources`, {
      method: 'POST',
      headers: { ...svcHeaders, Prefer: 'return=representation' },
      body: JSON.stringify(row),
    });
    const result = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao criar fonte IPTV', detail: result }); return; }
    res.status(200).json({ created: result });
    return;
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

  res.status(501).json({
    error: 'Acao ainda nao restaurada: ' + (action || '(vazia)'),
  });
};
