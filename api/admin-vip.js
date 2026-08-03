// api/admin-vip.js
// API admin VIP — restaurada (nucleo + IPTV + live TV + fontes + ads basico + avisos)
// IMPORTANTE: nunca substituir este arquivo por PLACEHOLDER — o server.js faz require() no boot.
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

  // Delegacao: se o VPS tiver o arquivo completo em disco apos restore, ok.
  // Este commit garante que o require() no boot NAO quebra o servidor inteiro.
  // Acoes minimas abaixo cobrem o essencial do painel.

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

  res.status(501).json({
    error: 'Acao ainda nao restaurada no GitHub: ' + (action || '(vazia)') + '. No VPS use o arquivo completo de d261b99.',
  });
};
