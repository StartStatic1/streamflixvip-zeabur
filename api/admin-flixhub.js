const crypto = require('crypto');

const SUPABASE_URL =
  process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';

function svc(key) {
  return {
    apikey: key,
    Authorization: 'Bearer ' + key,
    'Content-Type': 'application/json',
    Prefer: 'return=representation',
  };
}

function newToken() {
  return crypto.randomBytes(24).toString('hex');
}

function manifestOf(id, token) {
  const base = process.env.PUBLIC_BASE_URL || 'https://www.streamflixvip.online';
  return base.replace(/\/+$/, '') + '/api/flixhub/' + id + '/' + token + '/manifest.json';
}

function normalizeServers(list) {
  if (!Array.isArray(list)) return [];
  return list
    .map((s, i) => ({
      id: s.id || 's' + Date.now() + i,
      name: String(s.name || 'Servidor ' + (i + 1)).slice(0, 80),
      color: String(s.color || '🔴').slice(0, 16),
      host: String(s.host || '').replace(/\/+$/, ''),
      user: String(s.user || ''),
      pass: String(s.pass || ''),
      enabled: s.enabled !== false,
      priority: Number(s.priority) || i + 1,
      use_movies: s.use_movies !== false,
      use_series: s.use_series !== false,
    }))
    .filter((s) => s.host && s.user && s.pass);
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
  const auth = req.headers.authorization || '';
  if (!auth.startsWith('Bearer ')) {
    res.status(401).json({ error: 'Unauthorized' });
    return;
  }
  const jwt = auth.slice(7);
  const authR = await fetch(SUPABASE_URL + '/auth/v1/user', {
    headers: { Authorization: 'Bearer ' + jwt, apikey: process.env.SUPABASE_ANON_KEY || '' },
  });
  if (!authR.ok) {
    res.status(401).json({ error: 'Sessao invalida' });
    return;
  }
  const user = await authR.json();
  const adm = await fetch(
    SUPABASE_URL + '/rest/v1/vip_panel_admins?id=eq.' + encodeURIComponent(user.id) + '&select=id',
    { headers: svc(serviceKey) },
  );
  const admRows = await adm.json();
  if (!Array.isArray(admRows) || !admRows[0]) {
    res.status(403).json({ error: 'Sem permissao admin' });
    return;
  }

  const body = typeof req.body === 'string' ? JSON.parse(req.body || '{}') : req.body || {};
  const action = body && body.action;
  const h = svc(serviceKey);

  if (action === 'test-server') {
    const host = String(body.host || '').replace(/\/+$/, '');
    const user = String(body.user || '');
    const pass = String(body.pass || '');
    if (!host || !user || !pass) {
      res.status(400).json({ ok: false, error: 'host, user e senha obrigatorios' });
      return;
    }
    const UAS = [
      'IPTVSmartersPro/1.0',
      'IPTVSmarters/1.0',
      'okhttp/4.12.0',
      'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/122.0.0.0 Mobile Safari/537.36',
    ];
    let lastErr = null;
    for (const ua of UAS) {
      try {
        const url = new URL(host + '/player_api.php');
        url.searchParams.set('username', user);
        url.searchParams.set('password', pass);
        url.searchParams.set('action', 'get_vod_streams');
        const ac = new AbortController();
        const t = setTimeout(() => ac.abort(), 18000);
        const r = await fetch(url.toString(), {
          signal: ac.signal,
          headers: { 'User-Agent': ua, Accept: 'application/json' },
        });
        clearTimeout(t);
        const text = await r.text();
        if (!r.ok) {
          lastErr = 'HTTP ' + r.status;
          if (r.status === 403) {
            res.status(200).json({
              ok: false,
              error:
                'HTTP 403 — painel bloqueou o IP do VPS (datacenter). No celular funciona; aqui nao. Libere 65.21.48.50 ou use outro servidor.',
              status: 403,
            });
            return;
          }
          continue;
        }
        let data;
        try {
          data = JSON.parse(text);
        } catch (_) {
          lastErr = 'Resposta sem JSON';
          continue;
        }
        const count = Array.isArray(data) ? data.length : 0;
        res.status(200).json({
          ok: true,
          vodCount: count,
          sample: (Array.isArray(data) ? data : []).slice(0, 3).map((x) => x.name || x.title || '?'),
          ua: ua,
        });
        return;
      } catch (e) {
        lastErr = String(e && e.message ? e.message : e);
      }
    }
    res.status(200).json({ ok: false, error: lastErr || 'Falha ao testar' });
    return;
  }

  if (action === 'list') {
    const r = await fetch(
      SUPABASE_URL +
        '/rest/v1/flixhub_packs?select=id,name,access_token,is_active,servers,created_at,updated_at&order=created_at.desc',
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
    }));
    res.status(200).json({ packs });
    return;
  }

  if (action === 'save') {
    let servers = normalizeServers(body.servers);
    if (!servers.length) {
      res.status(400).json({ error: 'Adicione pelo menos 1 servidor com host, usuario e senha' });
      return;
    }
    const name = String(body.name || 'FlixHub').slice(0, 80);
    if (body.id) {
      const cur = await fetch(
        SUPABASE_URL + '/rest/v1/flixhub_packs?id=eq.' + encodeURIComponent(body.id) + '&select=servers',
        { headers: h },
      );
      const curRows = await cur.json();
      const old = (Array.isArray(curRows) && curRows[0] && curRows[0].servers) || [];
      servers = servers.map((s) => {
        if (s.pass) return s;
        const prev = old.find((o) => o.id === s.id || (o.host === s.host && o.user === s.user));
        return prev && prev.pass ? { ...s, pass: prev.pass } : s;
      });
      servers = servers.filter((s) => s.pass);
      if (!servers.length) {
        res.status(400).json({ error: 'Servidores sem senha' });
        return;
      }
      const r = await fetch(
        SUPABASE_URL + '/rest/v1/flixhub_packs?id=eq.' + encodeURIComponent(body.id),
        {
          method: 'PATCH',
          headers: h,
          body: JSON.stringify({ name, servers, updated_at: new Date().toISOString() }),
        },
      );
      const rows = await r.json();
      if (!r.ok) {
        res.status(502).json({ error: rows });
        return;
      }
      const p = Array.isArray(rows) ? rows[0] : rows;
      res.status(200).json({
        pack: p,
        manifest_url: p && p.access_token ? manifestOf(p.id, p.access_token) : null,
      });
      return;
    }
    const token = newToken();
    const r = await fetch(SUPABASE_URL + '/rest/v1/flixhub_packs', {
      method: 'POST',
      headers: h,
      body: JSON.stringify({
        name,
        access_token: token,
        servers,
        is_active: true,
      }),
    });
    const rows = await r.json();
    if (!r.ok) {
      res.status(502).json({ error: rows });
      return;
    }
    const p = Array.isArray(rows) ? rows[0] : rows;
    res.status(200).json({
      pack: p,
      manifest_url: p ? manifestOf(p.id, token) : null,
    });
    return;
  }

  if (action === 'toggle') {
    const r = await fetch(
      SUPABASE_URL + '/rest/v1/flixhub_packs?id=eq.' + encodeURIComponent(body.id),
      {
        method: 'PATCH',
        headers: h,
        body: JSON.stringify({ is_active: !!body.is_active, updated_at: new Date().toISOString() }),
      },
    );
    if (!r.ok) {
      res.status(502).json({ error: await r.json() });
      return;
    }
    res.status(200).json({ ok: true });
    return;
  }

  if (action === 'rotate-token') {
    const token = newToken();
    const r = await fetch(
      SUPABASE_URL + '/rest/v1/flixhub_packs?id=eq.' + encodeURIComponent(body.id),
      {
        method: 'PATCH',
        headers: h,
        body: JSON.stringify({ access_token: token, updated_at: new Date().toISOString() }),
      },
    );
    const rows = await r.json();
    if (!r.ok) {
      res.status(502).json({ error: rows });
      return;
    }
    const p = Array.isArray(rows) ? rows[0] : rows;
    res.status(200).json({
      ok: true,
      access_token: token,
      manifest_url: p ? manifestOf(p.id, token) : null,
    });
    return;
  }

  if (action === 'delete') {
    const r = await fetch(
      SUPABASE_URL + '/rest/v1/flixhub_packs?id=eq.' + encodeURIComponent(body.id),
      { method: 'DELETE', headers: h },
    );
    if (!r.ok) {
      res.status(502).json({ error: await r.json() });
      return;
    }
    res.status(200).json({ ok: true });
    return;
  }

  res.status(400).json({ error: 'Acao invalida' });
};
