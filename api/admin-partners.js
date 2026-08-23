// api/admin-partners.js
// Gestão de API keys de parceiros (só admin do painel).
const { generateApiKey } = require('../lib/partner-auth');

const SUPABASE_URL =
  process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  if (req.method === 'OPTIONS') {
    res.status(200).end();
    return;
  }
  if (req.method !== 'POST') {
    res.status(405).json({ error: 'Method not allowed' });
    return;
  }

  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!serviceKey) {
    res.status(500).json({ error: 'SUPABASE_SERVICE_ROLE_KEY nao configurada' });
    return;
  }

  const authHeader = req.headers['authorization'] || '';
  const userToken = authHeader.replace('Bearer ', '').trim();
  if (!userToken) {
    res.status(401).json({ error: 'Token nao fornecido' });
    return;
  }

  const userRes = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
    headers: { apikey: serviceKey, Authorization: `Bearer ${userToken}` },
  });
  if (!userRes.ok) {
    res.status(401).json({ error: 'Token invalido' });
    return;
  }
  const userJson = await userRes.json();
  const userId = userJson && userJson.id;
  if (!userId) {
    res.status(401).json({ error: 'Token invalido' });
    return;
  }

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
  if (typeof body === 'string') {
    try {
      body = JSON.parse(body);
    } catch (e) {
      body = {};
    }
  }
  const action = body && body.action;
  const svcHeaders = {
    apikey: serviceKey,
    Authorization: `Bearer ${serviceKey}`,
    'Content-Type': 'application/json',
  };

  if (action === 'list-partners') {
    const r = await fetch(
      `${SUPABASE_URL}/rest/v1/api_partners?select=id,name,contact_email,notes,key_prefix,is_active,scopes,rate_limit_per_min,request_count,last_used_at,created_at&order=created_at.desc`,
      { headers: svcHeaders },
    );
    const rows = await r.json();
    if (!r.ok) {
      res.status(502).json({
        error: 'Tabela api_partners ausente? Rode sql/api_partners.sql no Supabase.',
        detail: rows,
      });
      return;
    }
    res.status(200).json({ partners: rows || [] });
    return;
  }

  if (action === 'create-partner') {
    const name = String(body.name || '').trim();
    if (!name) {
      res.status(400).json({ error: 'Informe o nome do parceiro' });
      return;
    }
    const scopes = Array.isArray(body.scopes) && body.scopes.length
      ? body.scopes.map(String)
      : ['sources'];
    const rate = Number.isFinite(Number(body.rate_limit_per_min))
      ? Math.max(10, Math.min(1000, Number(body.rate_limit_per_min)))
      : 60;

    const key = generateApiKey();
    const payload = {
      name,
      contact_email: body.contact_email ? String(body.contact_email).trim() : null,
      notes: body.notes ? String(body.notes).trim() : null,
      key_prefix: key.prefix,
      key_hash: key.hash,
      is_active: true,
      scopes,
      rate_limit_per_min: rate,
    };

    const r = await fetch(`${SUPABASE_URL}/rest/v1/api_partners`, {
      method: 'POST',
      headers: { ...svcHeaders, Prefer: 'return=representation' },
      body: JSON.stringify(payload),
    });
    const result = await r.json();
    if (!r.ok) {
      res.status(502).json({ error: 'Erro ao criar parceiro', detail: result });
      return;
    }

    // A chave completa só é devolvida UMA vez
    res.status(200).json({
      partner: Array.isArray(result) ? result[0] : result,
      api_key: key.raw,
      warning: 'Copie a API key agora. Ela nao sera mostrada de novo.',
    });
    return;
  }

  if (action === 'toggle-partner') {
    const id = body.id || body.partnerId;
    if (!id) {
      res.status(400).json({ error: 'Informe id' });
      return;
    }
    const isActive = body.is_active !== undefined ? !!body.is_active : !!body.isActive;
    const r = await fetch(`${SUPABASE_URL}/rest/v1/api_partners?id=eq.${encodeURIComponent(id)}`, {
      method: 'PATCH',
      headers: svcHeaders,
      body: JSON.stringify({ is_active: isActive, updated_at: new Date().toISOString() }),
    });
    if (!r.ok) {
      res.status(502).json({ error: 'Erro ao alternar', detail: await r.text() });
      return;
    }
    res.status(200).json({ success: true });
    return;
  }

  if (action === 'update-partner') {
    const id = body.id || body.partnerId;
    if (!id) {
      res.status(400).json({ error: 'Informe id' });
      return;
    }
    const patch = { updated_at: new Date().toISOString() };
    if (body.name != null && String(body.name).trim()) patch.name = String(body.name).trim();
    if (body.contact_email !== undefined) {
      patch.contact_email = body.contact_email ? String(body.contact_email).trim() : null;
    }
    if (body.notes !== undefined) patch.notes = body.notes ? String(body.notes).trim() : null;
    if (Array.isArray(body.scopes)) patch.scopes = body.scopes.map(String);
    if (body.rate_limit_per_min != null) {
      patch.rate_limit_per_min = Math.max(10, Math.min(1000, Number(body.rate_limit_per_min) || 60));
    }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/api_partners?id=eq.${encodeURIComponent(id)}`, {
      method: 'PATCH',
      headers: svcHeaders,
      body: JSON.stringify(patch),
    });
    if (!r.ok) {
      res.status(502).json({ error: 'Erro ao atualizar', detail: await r.text() });
      return;
    }
    res.status(200).json({ success: true });
    return;
  }

  if (action === 'rotate-partner-key') {
    const id = body.id || body.partnerId;
    if (!id) {
      res.status(400).json({ error: 'Informe id' });
      return;
    }
    const key = generateApiKey();
    const r = await fetch(`${SUPABASE_URL}/rest/v1/api_partners?id=eq.${encodeURIComponent(id)}`, {
      method: 'PATCH',
      headers: { ...svcHeaders, Prefer: 'return=representation' },
      body: JSON.stringify({
        key_prefix: key.prefix,
        key_hash: key.hash,
        updated_at: new Date().toISOString(),
      }),
    });
    const result = await r.json();
    if (!r.ok) {
      res.status(502).json({ error: 'Erro ao rotacionar chave', detail: result });
      return;
    }
    res.status(200).json({
      partner: Array.isArray(result) ? result[0] : result,
      api_key: key.raw,
      warning: 'Nova chave gerada. A anterior deixou de funcionar. Copie agora.',
    });
    return;
  }

  if (action === 'delete-partner') {
    const id = body.id || body.partnerId;
    if (!id) {
      res.status(400).json({ error: 'Informe id' });
      return;
    }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/api_partners?id=eq.${encodeURIComponent(id)}`, {
      method: 'DELETE',
      headers: svcHeaders,
    });
    if (!r.ok) {
      res.status(502).json({ error: 'Erro ao excluir', detail: await r.text() });
      return;
    }
    res.status(200).json({ success: true });
    return;
  }

  res.status(400).json({
    error: 'action invalida',
    allowed: [
      'list-partners',
      'create-partner',
      'toggle-partner',
      'update-partner',
      'rotate-partner-key',
      'delete-partner',
    ],
  });
};
