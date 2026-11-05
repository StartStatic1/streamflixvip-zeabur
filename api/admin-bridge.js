// api/admin-bridge.js — aba Bridge (não altera admin-vip / add-ons).
const crypto = require('crypto');
const SUPABASE_URL =
  process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';
const PUBLIC_BASE = (process.env.PUBLIC_BASE_URL || 'https://www.streamflixvip.online').replace(/\/+$/, '');

const {
  xtreamFetch,
  normHost: normHostLib,
  relayConfigured,
  proxyConfigured,
} = require('../lib/xtream-fetch');

/** tokens curtos para ingest pelo celular quando VPS+CF levam 403 */
const phoneProbeTokens = new Map();

function newToken() {
  return crypto.randomBytes(24).toString('hex');
}
function manifestOf(id, token) {
  return PUBLIC_BASE + '/api/bridge/' + id + '/' + token + '/manifest.json';
}
function baseOf(id, token) {
  return PUBLIC_BASE + '/api/bridge/' + id + '/' + token;
}

function svc(serviceKey) {
  return {
    apikey: serviceKey,
    Authorization: `Bearer ${serviceKey}`,
    'Content-Type': 'application/json',
  };
}

function normHost(h) {
  return normHostLib(h);
}

async function xtream(host, user, pass, action) {
  return xtreamFetch(host, user, pass, action || '', {}, { timeoutMs: 20000 });
}

function cats(raw) {
  return (Array.isArray(raw) ? raw : []).map((c) => ({
    id: String(c.category_id),
    name: String(c.category_name || 'Outros'),
  }));
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

async function ensureToken(row, h) {
  let token = String(row.access_token || '').trim();
  if (token) return token;
  token = newToken();
  await fetch(SUPABASE_URL + '/rest/v1/iptv_bridges?id=eq.' + encodeURIComponent(row.id), {
    method: 'PATCH',
    headers: h,
    body: JSON.stringify({ access_token: token }),
  });
  row.access_token = token;
  return token;
}

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  if (req.method === 'OPTIONS') { res.status(200).end(); return; }
  if (req.method !== 'POST') { res.status(405).json({ error: 'Method not allowed' }); return; }

  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!serviceKey) { res.status(500).json({ error: 'Sem SERVICE_ROLE' }); return; }

  let body = req.body;
  if (typeof body === 'string') {
    try { body = JSON.parse(body); } catch (_) { body = {}; }
  }
  const action = body && body.action;
  const h = svc(serviceKey);

  // Ingest do bookmarklet no celular: autentica só pelo token de uso único
  if (action === 'phone_probe_submit') {
    const token = String(body.token || '').trim();
    const row = phoneProbeTokens.get(token);
    if (!row || row.exp < Date.now()) {
      res.status(400).json({ error: 'Atalho expirado — gere de novo no Bridge' });
      return;
    }
    phoneProbeTokens.delete(token);
    const live = cats(body.live);
    const movies = cats(body.movies);
    const series = cats(body.series);
    try {
      const fs = require('fs');
      const path = require('path');
      const dir = process.env.XTREAM_CLIENT_CACHE_DIR || '/root/streamflix-data/xtream-client-cache';
      fs.mkdirSync(dir, { recursive: true });
      const key = Buffer.from(row.host + '|' + row.user).toString('hex').slice(0, 40);
      const streams = Array.isArray(body.streams) ? body.streams : [];
      fs.writeFileSync(
        path.join(dir, key + '.json'),
        JSON.stringify({ at: Date.now(), host: row.host, user: row.user, live_cats: live, streams }),
      );
    } catch (_) {}
    res.status(200).json({
      ok: true,
      server: (body.info && body.info.server_info) || {},
      live,
      movies,
      series,
      streams_cached: Array.isArray(body.streams) ? body.streams.length : 0,
      host: row.host,
      user: row.user,
      pass: row.pass,
    });
    return;
  }

  const gate = await requireAdmin(req, serviceKey);
  if (gate.error) { res.status(gate.status).json({ error: gate.error }); return; }

  if (action === 'list') {
    const r = await fetch(
      SUPABASE_URL + '/rest/v1/iptv_bridges?select=id,name,xtream_host,xtream_user,xtream_pass,use_live,use_movies,use_series,live_cats,vod_cats,series_cats,is_active,addon_id,access_token,created_at&order=created_at.desc',
      { headers: h },
    );
    const rows = await r.json();
    if (!r.ok) {
      res.status(502).json({ error: 'Rode sql/iptv_bridges.sql e sql/iptv_bridges_token.sql', detail: rows });
      return;
    }
    res.status(200).json({ bridges: rows, publicBase: PUBLIC_BASE });
    return;
  }

  if (action === 'probe') {
    const host = normHost(body.host || body.xtream_host);
    const user = String(body.user || body.xtream_user || '').trim();
    const pass = String(body.pass || body.xtream_pass || '').trim();
    if (!host || !user || !pass) {
      res.status(400).json({ error: 'Informe host, usuario e senha' });
      return;
    }
    try {
      const info = await xtream(host, user, pass, '');
      const auth = info && info.user_info ? info.user_info : info;
      if (auth && String(auth.auth) === '0') {
        res.status(400).json({ error: 'Login Xtream recusado' });
        return;
      }
      const [live, vod, series] = await Promise.all([
        xtream(host, user, pass, 'get_live_categories').catch(() => []),
        xtream(host, user, pass, 'get_vod_categories').catch(() => []),
        xtream(host, user, pass, 'get_series_categories').catch(() => []),
      ]);
      res.status(200).json({
        ok: true,
        server: (info && info.server_info) || {},
        live: cats(live),
        movies: cats(vod),
        series: cats(series),
      });
    } catch (e) {
      const payload = {
        error: e.message || 'Falha ao ler o servidor',
        ip_blocked: !!(e && (e.ip_blocked || e.code === 'IP_BLOCKED')),
        relay: relayConfigured(),
        proxy: proxyConfigured(),
      };
      res.status(400).json(payload);
    }
    return;
  }

  if (action === 'phone_probe_start') {
    const host = normHost(body.host || body.xtream_host);
    const user = String(body.user || body.xtream_user || '').trim();
    const pass = String(body.pass || body.xtream_pass || '').trim();
    if (!host || !user || !pass) {
      res.status(400).json({ error: 'Informe host, usuario e senha' });
      return;
    }
    const token = crypto.randomBytes(16).toString('hex');
    const exp = Date.now() + 15 * 60 * 1000;
    phoneProbeTokens.set(token, { host, user, pass, exp, adminId: gate.userId });
    for (const [k, v] of phoneProbeTokens) {
      if (v.exp < Date.now()) phoneProbeTokens.delete(k);
    }
    const openUrl =
      host +
      '/player_api.php?username=' +
      encodeURIComponent(user) +
      '&password=' +
      encodeURIComponent(pass);
    const submitUrl = PUBLIC_BASE + '/api/admin-bridge';
    const bm =
      'javascript:void((async()=>{try{' +
      'const T=' + JSON.stringify(token) + ';' +
      'const U=' + JSON.stringify(submitUrl) + ';' +
      'const user=' + JSON.stringify(user) + ';' +
      'const pass=' + JSON.stringify(pass) + ';' +
      'const base=location.origin;' +
      'const q=(a)=>base+"/player_api.php?username="+encodeURIComponent(user)+"&password="+encodeURIComponent(pass)+(a?"&action="+a:"");' +
      'const j=async(a)=>{const r=await fetch(q(a));const t=await r.text();try{return JSON.parse(t)}catch(e){throw new Error("sem JSON "+r.status)}};' +
      'const info=await j("");' +
      'const live=await j("get_live_categories").catch(()=>[]);' +
      'const movies=await j("get_vod_categories").catch(()=>[]);' +
      'const series=await j("get_series_categories").catch(()=>[]);' +
      'const streams=await j("get_live_streams").catch(()=>[]);' +
      'const r=await fetch(U,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({action:"phone_probe_submit",token:T,info,live,movies,series,streams,host:base})});' +
      'const d=await r.json();alert(d.error||("OK pastas TV="+(d.live||[]).length+" · streams="+(d.streams_cached||0)));' +
      '}catch(e){alert("Falha: "+(e&&e.message||e));}})());';
    res.status(200).json({
      ok: true,
      token,
      open_url: openUrl,
      bookmarklet: bm,
      expires_in_sec: 900,
      hint:
        '1) Abra o link no celular. 2) Nos favoritos, cole o atalho. 3) Com a página do painel Xtream aberta, execute o atalho.',
    });
    return;
  }

  if (action === 'rename') {
    if (!body.id) { res.status(400).json({ error: 'Informe id' }); return; }
    const name = String(body.name || '').trim();
    if (!name) { res.status(400).json({ error: 'Informe o nome' }); return; }
    const cur = await fetch(
      SUPABASE_URL + '/rest/v1/iptv_bridges?id=eq.' + encodeURIComponent(body.id) + '&select=id,addon_id',
      { headers: h },
    ).then((r) => r.json());
    const row = cur && cur[0];
    if (!row) { res.status(404).json({ error: 'Ponte nao encontrada' }); return; }
    await fetch(SUPABASE_URL + '/rest/v1/iptv_bridges?id=eq.' + encodeURIComponent(body.id), {
      method: 'PATCH',
      headers: h,
      body: JSON.stringify({ name, updated_at: new Date().toISOString() }),
    });
    if (row.addon_id) {
      await fetch(SUPABASE_URL + '/rest/v1/stremio_addons?id=eq.' + encodeURIComponent(row.addon_id), {
        method: 'PATCH',
        headers: h,
        body: JSON.stringify({ name, updated_at: new Date().toISOString() }),
      });
    }
    res.status(200).json({ ok: true, name });
    return;
  }

  if (action === 'save') {
    const name = String(body.name || '').trim() || 'StreamFlix.Bridge';
    const host = normHost(body.host || body.xtream_host);
    const user = String(body.user || body.xtream_user || '').trim();
    const pass = String(body.pass || body.xtream_pass || '').trim();
    if (!host || !user || (!pass && !body.id)) {
      res.status(400).json({ error: 'Informe host, usuario e senha' });
      return;
    }
    const payload = {
      name,
      xtream_host: host,
      xtream_user: user,
      xtream_pass: pass,
      use_live: !!body.use_live,
      use_movies: body.use_movies !== false,
      use_series: body.use_series !== false,
      live_cats: Array.isArray(body.live_cats) ? body.live_cats : [],
      vod_cats: Array.isArray(body.vod_cats) ? body.vod_cats : [],
      series_cats: Array.isArray(body.series_cats) ? body.series_cats : [],
      is_active: body.is_active !== false,
      updated_at: new Date().toISOString(),
    };
    if (body.id && !pass) delete payload.xtream_pass;
    if (!body.id) payload.access_token = newToken();
    let row;
    if (body.id) {
      const r = await fetch(
        SUPABASE_URL + '/rest/v1/iptv_bridges?id=eq.' + encodeURIComponent(body.id),
        { method: 'PATCH', headers: { ...h, Prefer: 'return=representation' }, body: JSON.stringify(payload) },
      );
      const j = await r.json();
      if (!r.ok) { res.status(502).json({ error: 'Erro ao salvar', detail: j }); return; }
      row = Array.isArray(j) ? j[0] : j;
    } else {
      const r = await fetch(SUPABASE_URL + '/rest/v1/iptv_bridges', {
        method: 'POST',
        headers: { ...h, Prefer: 'return=representation' },
        body: JSON.stringify(payload),
      });
      const j = await r.json();
      if (!r.ok) { res.status(502).json({ error: 'Erro ao criar. Rode o SQL das pontes + token', detail: j }); return; }
      row = Array.isArray(j) ? j[0] : j;
    }
    const token = await ensureToken(row, h);
    const manifestUrl = manifestOf(row.id, token);
    const addonPayload = {
      name,
      manifest_url: manifestUrl,
      base_url: baseOf(row.id, token),
      is_active: payload.is_active,
      priority: 3,
      notes: 'bridge',
      last_ok_at: new Date().toISOString(),
      last_error: null,
      updated_at: new Date().toISOString(),
    };
    if (row.addon_id) {
      await fetch(SUPABASE_URL + '/rest/v1/stremio_addons?id=eq.' + encodeURIComponent(row.addon_id), {
        method: 'PATCH',
        headers: h,
        body: JSON.stringify(addonPayload),
      });
    } else {
      const ar = await fetch(SUPABASE_URL + '/rest/v1/stremio_addons', {
        method: 'POST',
        headers: { ...h, Prefer: 'return=representation' },
        body: JSON.stringify(addonPayload),
      });
      const aj = await ar.json();
      const addon = Array.isArray(aj) ? aj[0] : aj;
      if (ar.ok && addon && addon.id) {
        await fetch(SUPABASE_URL + '/rest/v1/iptv_bridges?id=eq.' + encodeURIComponent(row.id), {
          method: 'PATCH',
          headers: h,
          body: JSON.stringify({ addon_id: addon.id }),
        });
        row.addon_id = addon.id;
      }
    }
    res.status(200).json({ ok: true, bridge: row, manifest_url: manifestUrl });
    return;
  }

  if (action === 'rotate-token') {
    if (!body.id) { res.status(400).json({ error: 'Informe id' }); return; }
    const token = newToken();
    const cur = await fetch(
      SUPABASE_URL + '/rest/v1/iptv_bridges?id=eq.' + encodeURIComponent(body.id) + '&select=id,name,addon_id',
      { headers: h },
    ).then((r) => r.json());
    const row = cur && cur[0];
    if (!row) { res.status(404).json({ error: 'Ponte nao encontrada' }); return; }
    await fetch(SUPABASE_URL + '/rest/v1/iptv_bridges?id=eq.' + encodeURIComponent(body.id), {
      method: 'PATCH',
      headers: h,
      body: JSON.stringify({ access_token: token, updated_at: new Date().toISOString() }),
    });
    if (row.addon_id) {
      await fetch(SUPABASE_URL + '/rest/v1/stremio_addons?id=eq.' + encodeURIComponent(row.addon_id), {
        method: 'PATCH',
        headers: h,
        body: JSON.stringify({
          manifest_url: manifestOf(row.id, token),
          base_url: baseOf(row.id, token),
          updated_at: new Date().toISOString(),
        }),
      });
    }
    res.status(200).json({ ok: true, access_token: token, manifest_url: manifestOf(row.id, token) });
    return;
  }

  if (action === 'toggle') {
    if (!body.id) { res.status(400).json({ error: 'Informe id' }); return; }
    const on = !!body.is_active;
    await fetch(SUPABASE_URL + '/rest/v1/iptv_bridges?id=eq.' + encodeURIComponent(body.id), {
      method: 'PATCH',
      headers: h,
      body: JSON.stringify({ is_active: on, updated_at: new Date().toISOString() }),
    });
    const cur = await fetch(
      SUPABASE_URL + '/rest/v1/iptv_bridges?id=eq.' + encodeURIComponent(body.id) + '&select=addon_id',
      { headers: h },
    ).then((r) => r.json());
    const addonId = cur && cur[0] && cur[0].addon_id;
    if (addonId) {
      await fetch(SUPABASE_URL + '/rest/v1/stremio_addons?id=eq.' + encodeURIComponent(addonId), {
        method: 'PATCH',
        headers: h,
        body: JSON.stringify({ is_active: on }),
      });
    }
    res.status(200).json({ success: true });
    return;
  }

  if (action === 'delete') {
    if (!body.id) { res.status(400).json({ error: 'Informe id' }); return; }
    const cur = await fetch(
      SUPABASE_URL + '/rest/v1/iptv_bridges?id=eq.' + encodeURIComponent(body.id) + '&select=addon_id',
      { headers: h },
    ).then((r) => r.json());
    const addonId = cur && cur[0] && cur[0].addon_id;
    await fetch(SUPABASE_URL + '/rest/v1/iptv_bridges?id=eq.' + encodeURIComponent(body.id), {
      method: 'DELETE',
      headers: h,
    });
    if (addonId) {
      await fetch(SUPABASE_URL + '/rest/v1/stremio_addons?id=eq.' + encodeURIComponent(addonId), {
        method: 'DELETE',
        headers: h,
      });
    }
    res.status(200).json({ success: true });
    return;
  }

  res.status(400).json({
    error: 'action invalida',
    allowed: ['list', 'probe', 'phone_probe_start', 'phone_probe_submit', 'save', 'rename', 'toggle', 'delete', 'rotate-token'],
  });
};
