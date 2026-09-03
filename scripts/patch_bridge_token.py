#!/usr/bin/env python3
from pathlib import Path
import re

def new_token_fn():
    return '''function newToken() {
  return require('crypto').randomBytes(24).toString('hex');
}

function manifestOf(id, token) {
  return PUBLIC_BASE + '/api/bridge/' + id + '/' + token + '/manifest.json';
}

function baseOf(id, token) {
  return PUBLIC_BASE + '/api/bridge/' + id + '/' + token;
}

'''

def patch_admin():
    p = Path('api/admin-bridge.js')
    t = p.read_text()
    if 'function newToken' in t:
        print('admin-bridge ja tem token')
        return
    t = t.replace(
        "const PUBLIC_BASE = (process.env.PUBLIC_BASE_URL || 'https://www.streamflixvip.online').replace(/\\/+$/, '');\n",
        "const PUBLIC_BASE = (process.env.PUBLIC_BASE_URL || 'https://www.streamflixvip.online').replace(/\\/+$/, '');\n" + new_token_fn(),
    )
    t = t.replace(
        'iptv_bridges?select=id,name,xtream_host,use_live,use_movies,use_series,live_cats,vod_cats,series_cats,is_active,addon_id,created_at',
        'iptv_bridges?select=id,name,xtream_host,use_live,use_movies,use_series,live_cats,vod_cats,series_cats,is_active,addon_id,access_token,created_at',
    )
    old_save_addon = """    const manifestUrl = PUBLIC_BASE + '/api/bridge/' + row.id + '/manifest.json';
    const addonPayload = {
      name,
      manifest_url: manifestUrl,
      base_url: PUBLIC_BASE + '/api/bridge/' + row.id,
"""
    new_save_addon = """    let token = String(row.access_token || '').trim();
    if (!token) {
      token = newToken();
      await fetch(SUPABASE_URL + '/rest/v1/iptv_bridges?id=eq.' + encodeURIComponent(row.id), {
        method: 'PATCH',
        headers: h,
        body: JSON.stringify({ access_token: token }),
      });
      row.access_token = token;
    }
    const manifestUrl = manifestOf(row.id, token);
    const addonPayload = {
      name,
      manifest_url: manifestUrl,
      base_url: baseOf(row.id, token),
"""
    if old_save_addon not in t:
        raise SystemExit('admin-bridge: bloco manifestUrl nao achado')
    t = t.replace(old_save_addon, new_save_addon, 1)

    # create payload includes token on insert
    t = t.replace(
        "      is_active: body.is_active !== false,\n      updated_at: new Date().toISOString(),\n    };",
        "      is_active: body.is_active !== false,\n      updated_at: new Date().toISOString(),\n    };\n    if (!body.id) payload.access_token = newToken();",
    )

    rotate = '''
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
'''
    t = t.replace(
        "  res.status(400).json({ error: 'action invalida'",
        rotate + "  res.status(400).json({ error: 'action invalida'",
    )
    t = t.replace(
        "allowed: ['list', 'probe', 'save', 'toggle', 'delete']",
        "allowed: ['list', 'probe', 'save', 'toggle', 'delete', 'rotate-token']",
    )
    p.write_text(t)
    print('admin-bridge token ok')

def patch_bridge():
    p = Path('api/bridge.js')
    t = p.read_text()
    if 'access_token' in t and 'token invalido' in t:
        print('bridge.js ja exige token')
        return
    old_load = '''async function loadBridge(id, key) {
  const r = await fetch(
    SUPABASE_URL + '/rest/v1/iptv_bridges?id=eq.' + encodeURIComponent(id) + '&is_active=eq.true&select=*',
    { headers: svc(key) },
  );
  const rows = await r.json();
  return Array.isArray(rows) && rows[0] ? rows[0] : null;
}'''
    new_load = '''async function loadBridge(id, token, key) {
  const r = await fetch(
    SUPABASE_URL + '/rest/v1/iptv_bridges?id=eq.' + encodeURIComponent(id) + '&is_active=eq.true&select=*',
    { headers: svc(key) },
  );
  const rows = await r.json();
  const row = Array.isArray(rows) && rows[0] ? rows[0] : null;
  if (!row) return null;
  const expected = String(row.access_token || '').trim();
  if (!expected || String(token || '').trim() !== expected) {
    const err = new Error('token');
    err.code = 401;
    throw err;
  }
  return row;
}'''
    if old_load not in t:
        raise SystemExit('bridge.js loadBridge nao achado')
    t = t.replace(old_load, new_load, 1)
    old_parse = '''  const path = String(req.path || req.url || '').split('?')[0];
  const m = path.match(/\\/api\\/bridge\\/([^/]+)\\/(.+)$/);
  if (!m) {
    res.status(404).json({ error: 'Rota bridge invalida' });
    return;
  }
  const id = m[1];
  const rest = m[2];

  const b = await loadBridge(id, serviceKey);
  if (!b) {
    res.status(404).json({ error: 'Bridge inativa ou inexistente' });
    return;
  }'''
    new_parse = '''  const path = String(req.path || req.url || '').split('?')[0];
  const m = path.match(/\\/api\\/bridge\\/([^/]+)\\/([^/]+)\\/(.+)$/);
  if (!m) {
    res.status(401).json({ error: 'Token obrigatorio. Use /api/bridge/ID/TOKEN/manifest.json' });
    return;
  }
  const id = m[1];
  const token = m[2];
  const rest = m[3];

  let b;
  try {
    b = await loadBridge(id, token, serviceKey);
  } catch (e) {
    if (e && e.code === 401) {
      res.status(401).json({ error: 'Token invalido ou revogado' });
      return;
    }
    throw e;
  }
  if (!b) {
    res.status(404).json({ error: 'Bridge inativa ou inexistente' });
    return;
  }'''
    if old_parse not in t:
        raise SystemExit('bridge.js parse path nao achado')
    t = t.replace(old_parse, new_parse, 1)
    p.write_text(t)
    print('bridge.js token ok')

def patch_html():
    p = Path('Public/admin-bridge.html')
    t = p.read_text()
    if 'rotate-token' in t:
        print('html ja tem girar token')
        return
    t = t.replace(
        "const url = (d.publicBase || '') + '/api/bridge/' + b.id + '/manifest.json';",
        "const token = b.access_token || '';\n    const url = (d.publicBase || '') + '/api/bridge/' + b.id + '/' + token + '/manifest.json';",
    )
    t = t.replace(
        "+ '<button class=\"btn-sec\" style=\"padding:6px 10px;font-size:.75rem\" onclick=\"navigator.clipboard.writeText(\\\'' + url + '\\\');toast(\\\'URL copiada\\\')\">Copiar JSON</button>'",
        "+ '<button class=\"btn-sec\" style=\"padding:6px 10px;font-size:.75rem\" onclick=\"navigator.clipboard.writeText(\\\'' + url + '\\\');toast(\\\'URL copiada\\\')\">Copiar JSON</button>'
      + '<button class=\"btn-sec\" style=\"padding:6px 10px;font-size:.75rem\" onclick=\"if(confirm(\\\'Gera token novo e corta o link antigo?\\\'))api(\\\'rotate-token\\\',{id:\\\'' + b.id + '\\\'}).then(loadList)\">Novo token</button>'",
    )
    p.write_text(t)
    print('html token ok')

patch_admin()
patch_bridge()
patch_html()
print('patch_bridge_token fim')
