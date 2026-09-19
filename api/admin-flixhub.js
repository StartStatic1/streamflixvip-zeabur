// api/admin-flixhub.js — painel FlixHub (agregador Nuvio, separado do app)
const crypto = require('crypto');
const SUPABASE_URL =
  process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';
const PUBLIC_BASE = (process.env.PUBLIC_BASE_URL || 'https://www.streamflixvip.online').replace(/\/+$/, '');

function newToken() {
  return crypto.randomBytes(24).toString('hex');
}
function newId() {
  return crypto.randomUUID ? crypto.randomUUID() : crypto.randomBytes(16).toString('hex');
}
function svc(key) {
  return {
    apikey: key,
    Authorization: 'Bearer ' + key,
    'Content-Type': 'application/json',
  };
}
function normHost(h) {
  let u = String(h || '').trim();
  if (!u) return '';
  if (!/^https?:\/\//i.test(u)) u = 'http://' + u;
  return u.replace(/\/+$/, '');
}
function manifestOf(id, token) {
  return PUBLIC_BASE + '/api/flixhub/' + id + '/' + token + '/manifest.json';
}

async function requireAdmin(req, serviceKey) {
  const token = String(req.headers.authorization || '').replace(/^Bearer\s+/i, '').trim();
  if (!token) return { error: 'Token nao fornecido', status: 401 };
  const userRes = await fetch(SUPABASE_URL + '/auth/v1/user', {
    headers: { apikey: serviceKey, Authorization: 'Bearer ' + token },
  });
  if (!userRes.ok) return { error: 'Token invalido', status: 401 };
  const user = await userRes.json();
  if (!user || !user.id) return { error: 'Token invalido', status: 401 };
  const adminRes = await fetch(
    SUPABASE_URL + '/rest/v1/vip_panel_admins?id=eq.' + encodeURIComponent(user.id) + '&select=id',
    { headers: svc(serviceKey) },
  );
  const rows = await adminRes.json();
  if (!adminRes.ok || !rows.length) return { error: 'Acesso negado', status: 403 };
  return { userId: user.id };
}

function normalizeServers(arr) {
  const colors = ['🔴', '🔵', '🟢', '🟣', '🟠', '🟡', '⚪', '🟤'];
  return (Array.isArray(arr) ? arr : []).map((s, i) => ({
    id: String(s.id || newId()),
    name: String(s.name || ('Servidor ' + (i + 1))).trim() || ('Servidor ' + (i + 1)),
    color: String(s.color || colors[i % colors.length]),
    host: normHost(s.host || s.xtream_host),
    user: String(s.user || s.xtream_user || '').trim(),
    pass: String(s.pass || s.xtream_pass || '').trim(),
    enabled: s.enabled !== false,
    priority: Number(s.priority) || i + 1,
    use_movies: s.use_movies !== false,
    use_series: s.use_series !== false,
  })).filter((s) => s.host && s.user);
}

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  if (req.method === 'OPTIONS') { res.status(200).end(); return; }
  if (req.method !== 'POST') { res.status(405).json({ error: 'Method not allowed' }); return; }

  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!serviceKey) { res.status(500).json({ error: 'Sem SERVICE_ROLE' }); return; }

  const gate = await requireAdmin(req, serviceKey);
  if (gate.error) { res.status(gate.status).json({ error: gate.error }); return; }

  let body = req.body;
  if (typeof body === 'string') {
    try { body = JSON.parse(body); } catch (_) { body = {}; }
  }
  const action = body && body.action;
  const h = svc(serviceKey);

  if (action === 'list') {
    const r = await fetch(
      SUPABASE_URL + '/rest/v1/flixhub_packs?select=id,name,access_token,is_active,servers,created_at,updated_at&order=created_at.desc',
      { headers: h },
    );
    const rows = await r.json();
    if (!r.ok) {
      res.status(502).json({
        error: 'Rode sql/flixhub_packs.sql no Supabase',
        detail: rows,
      });
      return;
    }
    const packs = (Array.isArray(rows) ? rows : []).map((p) => ({
      ...p,
      manifest_url: p.access_token ? manifestOf(p.id, p.access_token) : null,
      server_count: Array.isArray(p.servers) ? p.servers.length : 0,
      enabled_count: Array.isArray(p.servers)
        ? p.servers.filter((s) => s && s.enabled !== false).length
        : 0,
    }));
    res.status(200).json({ packs, publicBase: PUBLIC_BASE });
    return;
  }

  if (action === 'save') {
    const name = String(body.name || '').trim() || 'FlixHub';
    let servers = normalizeServers(body.servers);
    if (body.id) {
      const cur = await fetch(
        SUPABASE_URL + '/rest/v1/flixhub_packs?id=eq.' + encodeURIComponent(body.id) + '&select=servers',
        { headers: h },
      ).then((r) => r.json());
      const old = (cur && cur[0] && cur[0].servers) || [];
      const byId = {};
      (Array.isArray(old) ? old : []).forEach((s) => { byId[String(s.id)] = s; });
      servers = servers.map((s) => {
        if (!s.pass && byId[s.id] && byId[s.id].pass) s.pass = byId[s.id].pass;
        return s;
      }).filter((s) => s.pass);
    } else {
      servers = servers.filter((s) => s.pass);
    }
    if (!servers.length) {
      res.status(400).json({ error: 'Adicione pelo menos 1 servidor com host, usuario e senha' });
      return;
    }
    servers.sort((a, b) => (a.priority || 0) - (b.priority || 0));
    const payload = {
      name,
      servers,
      is_active: body.is_active !== false,
      updated_at: new Date().toISOString(),
    };
    let row;
    if (body.id) {
      const r = await fetch(
        SUPABASE_URL + '/rest/v1/flixhub_packs?id=eq.' + encodeURIComponent(body.id),
        { method: 'PATCH', headers: { ...h, Prefer: 'return=representation' }, body: JSON.stringify(payload) },
      );
      const j = await r.json();
      if (!r.ok) { res.status(502).json({ error: 'Erro ao salvar', detail: j }); return; }
      row = Array.isArray(j) ? j[0] : j;
    } else {
      payload.access_token = newToken();
      const r = await fetch(SUPABASE_URL + '/rest/v1/flixhub_packs', {
        method: 'POST',
        headers: { ...h, Prefer: 'return=representation' },
        body: JSON.stringify(payload),
      });
      const j = await r.json();
      if (!r.ok) {
        res.status(502).json({ error: 'Erro ao criar. Rode sql/flixhub_packs.sql', detail: j });
        return;
      }
      row = Array.isArray(j) ? j[0] : j;
    }
    res.status(200).json({
      ok: true,
      pack: row,
      manifest_url: manifestOf(row.id, row.access_token),
    });
    return;
  }

  if (action === 'rotate-token') {
    if (!body.id) { res.status(400).json({ error: 'Informe id' }); return; }
    const token = newToken();
    const r = await fetch(
      SUPABASE_URL + '/rest/v1/flixhub_packs?id=eq.' + encodeURIComponent(body.id),
      {
        method: 'PATCH',
        headers: { ...h, Prefer: 'return=representation' },
        body: JSON.stringify({ access_token: token, updated_at: new Date().toISOString() }),
      },
    );
    const j = await r.json();
    if (!r.ok) { res.status(502).json({ error: j }); return; }
    const row = Array.isArray(j) ? j[0] : j;
    res.status(200).json({ ok: true, access_token: token, manifest_url: manifestOf(row.id, token) });
    return;
  }

  if (action === 'toggle') {
    if (!body.id) { res.status(400).json({ error: 'Informe id' }); return; }
    await fetch(SUPABASE_URL + '/rest/v1/flixhub_packs?id=eq.' + encodeURIComponent(body.id), {
      method: 'PATCH',
      headers: h,
      body: JSON.stringify({ is_active: !!body.is_active, updated_at: new Date().toISOString() }),
    });
    res.status(200).json({ success: true });
    return;
  }

  if (action === 'delete') {
    if (!body.id) { res.status(400).json({ error: 'Informe id' }); return; }
    await fetch(SUPABASE_URL + '/rest/v1/flixhub_packs?id=eq.' + encodeURIComponent(body.id), {
      method: 'DELETE',
      headers: h,
    });
    res.status(200).json({ success: true });
    return;
  }

  res.status(400).json({ error: 'action invalida', allowed: ['list', 'save', 'rotate-token', 'toggle', 'delete'] });
};
