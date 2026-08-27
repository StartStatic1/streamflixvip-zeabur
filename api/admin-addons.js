// api/admin-addons.js
// Gestao de add-ons Stremio (so admin do painel).
const {
  normalizeManifestUrl,
  baseFromManifestUrl,
  probeManifest,
} = require('../lib/stremio-addons');

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

  if (action === 'list-addons') {
    const r = await fetch(
      `${SUPABASE_URL}/rest/v1/stremio_addons?select=id,name,manifest_url,base_url,is_active,priority,notes,last_ok_at,last_error,created_at&order=priority.desc,created_at.desc`,
      { headers: svcHeaders },
    );
    const rows = await r.json();
    if (!r.ok) {
      res.status(502).json({
        error: 'Tabela stremio_addons ausente? Rode sql/stremio_addons.sql no Supabase.',
        detail: rows,
      });
      return;
    }
    res.status(200).json({ addons: rows || [] });
    return;
  }

  if (action === 'probe-addon') {
    try {
      const probed = await probeManifest(body.manifest_url || body.url);
      res.status(200).json({
        ok: true,
        name: probed.name,
        manifest_url: probed.manifest_url,
        base_url: probed.base_url,
        types: probed.raw.types || [],
        resources: probed.raw.resources || [],
      });
    } catch (e) {
      res.status(400).json({ error: e.message || 'Falha ao ler manifest' });
    }
    return;
  }

  if (action === 'create-addon') {
    const rawUrl = body.manifest_url || body.url;
    let probed;
    try {
      probed = await probeManifest(rawUrl);
    } catch (e) {
      res.status(400).json({ error: e.message || 'Manifest invalido' });
      return;
    }
    const name = String(body.name || probed.name || 'Addon').trim();
    const priority = Number.isFinite(Number(body.priority)) ? Number(body.priority) : 0;
    const payload = {
      name,
      manifest_url: probed.manifest_url,
      base_url: probed.base_url,
      is_active: body.is_active !== false,
      priority,
      notes: body.notes ? String(body.notes).trim() : null,
      last_ok_at: new Date().toISOString(),
      last_error: null,
    };
    const r = await fetch(`${SUPABASE_URL}/rest/v1/stremio_addons`, {
      method: 'POST',
      headers: { ...svcHeaders, Prefer: 'return=representation' },
      body: JSON.stringify(payload),
    });
    const result = await r.json();
    if (!r.ok) {
      res.status(502).json({ error: 'Erro ao criar add-on', detail: result });
      return;
    }
    res.status(200).json({ addon: Array.isArray(result) ? result[0] : result });
    return;
  }

  if (action === 'toggle-addon') {
    const id = body.id || body.addonId;
    if (!id) {
      res.status(400).json({ error: 'Informe id' });
      return;
    }
    const isActive = body.is_active !== undefined ? !!body.is_active : !!body.isActive;
    const r = await fetch(`${SUPABASE_URL}/rest/v1/stremio_addons?id=eq.${encodeURIComponent(id)}`, {
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

  if (action === 'update-addon') {
    const id = body.id || body.addonId;
    if (!id) {
      res.status(400).json({ error: 'Informe id' });
      return;
    }
    const patch = { updated_at: new Date().toISOString() };
    if (body.name != null && String(body.name).trim()) patch.name = String(body.name).trim();
    if (body.notes !== undefined) patch.notes = body.notes ? String(body.notes).trim() : null;
    if (body.priority != null) patch.priority = Number(body.priority) || 0;
    if (body.manifest_url) {
      try {
        const probed = await probeManifest(body.manifest_url);
        patch.manifest_url = probed.manifest_url;
        patch.base_url = probed.base_url;
        patch.last_ok_at = new Date().toISOString();
        patch.last_error = null;
      } catch (e) {
        res.status(400).json({ error: e.message || 'Manifest invalido' });
        return;
      }
    }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/stremio_addons?id=eq.${encodeURIComponent(id)}`, {
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

  if (action === 'delete-addon') {
    const id = body.id || body.addonId;
    if (!id) {
      res.status(400).json({ error: 'Informe id' });
      return;
    }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/stremio_addons?id=eq.${encodeURIComponent(id)}`, {
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

  if (action === 'test-addon') {
    const id = body.id || body.addonId;
    const tmdbId = body.tmdb_id || body.tmdbId || 550;
    const type = String(body.type || 'movie').toLowerCase() === 'tv' ? 'tv' : 'movie';
    const { collectAddonSources } = require('../lib/stremio-addons');
    // testa so este addon: se id, marca temp — senao todos ativos
    try {
      if (id) {
        const one = await fetch(
          `${SUPABASE_URL}/rest/v1/stremio_addons?id=eq.${encodeURIComponent(id)}&select=id,name,manifest_url,base_url,priority`,
          { headers: svcHeaders },
        );
        const rows = await one.json();
        if (!one.ok || !rows.length) {
          res.status(404).json({ error: 'Add-on nao encontrado' });
          return;
        }
        // força collect via list ativa + filter no resultado por label
        const all = await collectAddonSources(serviceKey, Number(tmdbId), type, null, null);
        const filtered = all.filter((s) =>
          String(s.source_label || '').includes(rows[0].name),
        );
        res.status(200).json({
          ok: true,
          tmdb_id: Number(tmdbId),
          type,
          streams: filtered.length ? filtered : all.slice(0, 15),
          count: filtered.length || all.length,
        });
        return;
      }
      const all = await collectAddonSources(serviceKey, Number(tmdbId), type, null, null);
      res.status(200).json({ ok: true, tmdb_id: Number(tmdbId), type, streams: all, count: all.length });
    } catch (e) {
      res.status(500).json({ error: e.message || 'Falha no teste' });
    }
    return;
  }

  res.status(400).json({
    error: 'action invalida',
    allowed: [
      'list-addons',
      'probe-addon',
      'create-addon',
      'toggle-addon',
      'update-addon',
      'delete-addon',
      'test-addon',
    ],
  });
};
