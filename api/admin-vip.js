// api/admin-vip.js
// HOTFIX: arquivo foi corrompido com placeholder; restauração mínima para o server.js conseguir dar require() e subir a API.
// Painel admin: ações essenciais + stub. Completar CRUD live_tv em seguida.

const SUPABASE_URL = 'https://gkujbjpvphuvrejpvvtz.supabase.co';

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  if (req.method === 'OPTIONS') { res.status(200).end(); return; }
  if (req.method !== 'POST') { res.status(405).json({ error: 'Method not allowed' }); return; }

  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!serviceKey) { res.status(500).json({ error: 'SUPABASE_SERVICE_ROLE_KEY não configurada' }); return; }

  const authHeader = req.headers['authorization'] || '';
  const userToken = authHeader.replace('Bearer ', '').trim();
  if (!userToken) { res.status(401).json({ error: 'Token não fornecido' }); return; }

  const userRes = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
    headers: { apikey: serviceKey, Authorization: `Bearer ${userToken}` },
  });
  if (!userRes.ok) { res.status(401).json({ error: 'Token inválido' }); return; }
  const userJson = await userRes.json();
  const userId = userJson?.id;
  if (!userId) { res.status(401).json({ error: 'Token inválido' }); return; }

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
  const action = body?.action;
  const svcHeaders = {
    apikey: serviceKey,
    Authorization: `Bearer ${serviceKey}`,
    'Content-Type': 'application/json',
  };

  // ── LIST codes ──
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

  if (action === 'list-iptv-sources') {
    const r = await fetch(
      `${SUPABASE_URL}/rest/v1/iptv_sources?select=id,name,xtream_host,xtream_user,priority,is_active,source_type,sync_phase,sync_cursor,last_batch_at,last_synced_at,last_sync_stats,xtream_sync_cursor,xtream_series_sync_cursor,xtream_last_batch_at,xtream_last_synced_at,xtream_last_sync_stats,xtream_series_last_sync_stats&order=created_at.desc`,
      { headers: svcHeaders },
    );
    const rows = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao listar fontes IPTV', detail: rows }); return; }
    res.status(200).json({ sources: rows });
    return;
  }

  if (action === 'list-live-tv-sources') {
    const r = await fetch(
      `${SUPABASE_URL}/rest/v1/live_tv_sources?select=id,name,xtream_host,xtream_user,priority,is_active,created_at&order=priority.asc.nullslast`,
      { headers: svcHeaders },
    );
    if (!r.ok) {
      const detail = await r.text();
      res.status(502).json({ error: 'Tabela live_tv_sources ausente. Rode migrations/live_tv_sources.sql', detail });
      return;
    }
    res.status(200).json({ sources: await r.json() });
    return;
  }

  if (action === 'create-live-tv-source') {
    const { name, xtreamHost, xtreamUser, xtreamPass, priority } = body;
    if (!name || !xtreamHost || !xtreamUser || !xtreamPass) {
      res.status(400).json({ error: 'Informe name, xtreamHost, xtreamUser e xtreamPass' });
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
    if (!r.ok) { const detail = await r.text(); res.status(502).json({ error: 'Erro ao criar fonte de TV', detail }); return; }
    res.status(200).json({ success: true, source: (await r.json())[0] });
    return;
  }

  if (action === 'toggle-live-tv-source') {
    const { sourceId, isActive } = body;
    if (!sourceId) { res.status(400).json({ error: 'Informe sourceId' }); return; }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/live_tv_sources?id=eq.${encodeURIComponent(sourceId)}`, {
      method: 'PATCH', headers: svcHeaders, body: JSON.stringify({ is_active: !!isActive }),
    });
    if (!r.ok) { const detail = await r.text(); res.status(502).json({ error: 'Erro ao atualizar', detail }); return; }
    res.status(200).json({ success: true });
    return;
  }

  if (action === 'delete-live-tv-source') {
    const { sourceId } = body;
    if (!sourceId) { res.status(400).json({ error: 'Informe sourceId' }); return; }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/live_tv_sources?id=eq.${encodeURIComponent(sourceId)}`, {
      method: 'DELETE', headers: svcHeaders,
    });
    if (!r.ok) { const detail = await r.text(); res.status(502).json({ error: 'Erro ao excluir', detail }); return; }
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
      try { const rows = await del.json(); unmatchedDeleted = Array.isArray(rows) ? rows.length : 0; } catch (_) {}
    }
    let enforceOk = false;
    try {
      const en = await fetch(`${SUPABASE_URL}/rest/v1/rpc/enforce_max_sources_per_episode`, {
        method: 'POST',
        headers: { ...svcHeaders, 'Content-Type': 'application/json' },
        body: JSON.stringify({ max_count: 2 }),
      });
      enforceOk = en.ok;
    } catch (_) {}
    res.status(200).json({ success: true, unmatchedDeleted, enforceOk });
    return;
  }

  // Demais ações do painel: resposta clara até restaurarmos o arquivo completo
  res.status(501).json({
    error: 'Painel em modo hotfix. Catálogo/API do app ok. Ação ainda não restaurada: ' + (action || '(vazia)'),
  });
};
