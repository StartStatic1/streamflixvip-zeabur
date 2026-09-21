const SUPABASE_URL =
  process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';
const { testKey, loadConfig } = require('../lib/torbox');

function svc(key) {
  return {
    apikey: key,
    Authorization: 'Bearer ' + key,
    'Content-Type': 'application/json',
    Prefer: 'return=representation',
  };
}

async function requireAdmin(req, serviceKey) {
  const auth = req.headers.authorization || '';
  if (!auth.startsWith('Bearer ')) return { error: 'Unauthorized', status: 401 };
  const jwt = auth.slice(7);
  const authR = await fetch(SUPABASE_URL + '/auth/v1/user', {
    headers: { apikey: serviceKey, Authorization: 'Bearer ' + jwt },
  });
  if (!authR.ok) return { error: 'Sessao invalida — saia e entre de novo', status: 401 };
  const user = await authR.json();
  const adm = await fetch(
    SUPABASE_URL + '/rest/v1/vip_panel_admins?id=eq.' + encodeURIComponent(user.id) + '&select=id',
    { headers: svc(serviceKey) },
  );
  const rows = await adm.json();
  if (!Array.isArray(rows) || !rows[0]) return { error: 'Sem permissao admin', status: 403 };
  return { user };
}

function maskKey(k) {
  const s = String(k || '');
  if (s.length < 8) return s ? '\u2022\u2022\u2022\u2022' : '';
  return s.slice(0, 4) + '\u2022\u2022\u2022\u2022' + s.slice(-4);
}

module.exports = async function handler(req, res) {
  if (req.method !== 'POST') {
    res.status(405).json({ error: 'POST only' });
    return;
  }
  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!serviceKey) {
    res.status(500).json({ error: 'SUPABASE_SERVICE_ROLE_KEY missing' });
    return;
  }
  const gate = await requireAdmin(req, serviceKey);
  if (gate.error) {
    res.status(gate.status).json({ error: gate.error });
    return;
  }

  const body = typeof req.body === 'string' ? JSON.parse(req.body || '{}') : req.body || {};
  const action = body.action;
  const h = svc(serviceKey);

  if (action === 'status') {
    const r = await fetch(
      SUPABASE_URL + '/rest/v1/torbox_settings?id=eq.1&select=is_enabled,last_ok_at,last_error,updated_at,api_key',
      { headers: h },
    );
    const rows = await r.json();
    if (!r.ok) {
      res.status(502).json({ error: 'Rode sql/torbox_settings.sql no Supabase', detail: rows });
      return;
    }
    const row = Array.isArray(rows) && rows[0] ? rows[0] : null;
    const envSet = !!String(process.env.TORBOX_API_KEY || '').trim();
    res.status(200).json({
      is_enabled: row ? !!row.is_enabled : false,
      has_key: !!(row && row.api_key) || envSet,
      key_mask: maskKey((row && row.api_key) || process.env.TORBOX_API_KEY || ''),
      last_ok_at: row && row.last_ok_at,
      last_error: row && row.last_error,
      updated_at: row && row.updated_at,
      env_fallback: envSet && !(row && row.api_key),
    });
    return;
  }

  if (action === 'save') {
    const apiKey = String(body.api_key || '').trim();
    const enabled = body.is_enabled !== false;
    const patch = { is_enabled: enabled, updated_at: new Date().toISOString() };
    if (apiKey) patch.api_key = apiKey;
    const r = await fetch(SUPABASE_URL + '/rest/v1/torbox_settings?id=eq.1', {
      method: 'PATCH',
      headers: h,
      body: JSON.stringify(patch),
    });
    const rows = await r.json();
    if (!r.ok) {
      res.status(502).json({ error: rows });
      return;
    }
    res.status(200).json({ ok: true, row: Array.isArray(rows) ? rows[0] : rows });
    return;
  }

  if (action === 'test') {
    const cfg = await loadConfig(serviceKey);
    const key = String(body.api_key || cfg.apiKey || '').trim();
    if (!key) {
      res.status(400).json({ ok: false, error: 'Cole a API key' });
      return;
    }
    try {
      const info = await testKey(key);
      await fetch(SUPABASE_URL + '/rest/v1/torbox_settings?id=eq.1', {
        method: 'PATCH',
        headers: h,
        body: JSON.stringify({
          last_ok_at: new Date().toISOString(),
          last_error: null,
          updated_at: new Date().toISOString(),
        }),
      });
      res.status(200).json({ ok: true, email: info.email, plan: info.plan });
    } catch (e) {
      await fetch(SUPABASE_URL + '/rest/v1/torbox_settings?id=eq.1', {
        method: 'PATCH',
        headers: h,
        body: JSON.stringify({
          last_error: String(e.message || e).slice(0, 200),
          updated_at: new Date().toISOString(),
        }),
      });
      res.status(200).json({ ok: false, error: String(e.message || e) });
    }
    return;
  }

  res.status(400).json({ error: 'Acao invalida' });
};
