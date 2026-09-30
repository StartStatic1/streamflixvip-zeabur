#!/usr/bin/env python3
"""Ingresso por filme via InfinitePay + gate no media-sources."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
p = root / "api/infinitepay.js"
t = p.read_text()

old_create = """  const { userId, amount, planLabel, durationHours } = req.body || {};
  if (!userId || amount == null) {
    return res.status(400).json({ error: 'userId e amount são obrigatórios' });
  }

  const hours = Number(durationHours) > 0 ? Number(durationHours) : 720;
  const label = planLabel || 'VIP';
  const cents = Math.round(Number(amount) * 100);
  if (!(cents > 0)) return res.status(400).json({ error: 'amount inválido' });

  // order_nsu carrega dados para o webhook (sem depender de metadata externa)
  const orderNsu = ['vip', userId, String(hours), encodeURIComponent(label), Date.now()].join('__');
"""

new_create = """  const { userId, amount, planLabel, durationHours, type, tmdbId, mediaType } = req.body || {};
  if (!userId || amount == null) {
    return res.status(400).json({ error: 'userId e amount são obrigatórios' });
  }

  const kind = String(type || 'vip').toLowerCase() === 'ticket' ? 'ticket' : 'vip';
  if (kind === 'ticket' && !tmdbId) {
    return res.status(400).json({ error: 'tmdbId obrigatório para ingresso' });
  }

  const hours = Number(durationHours) > 0 ? Number(durationHours) : (kind === 'ticket' ? 24 : 720);
  const label = planLabel || (kind === 'ticket' ? 'Ingresso' : 'VIP');
  const cents = Math.round(Number(amount) * 100);
  if (!(cents > 0)) return res.status(400).json({ error: 'amount inválido' });

  const orderNsu = kind === 'ticket'
    ? ['ticket', userId, String(tmdbId), String(hours), String(mediaType || 'movie'), Date.now()].join('__')
    : ['vip', userId, String(hours), encodeURIComponent(label), Date.now()].join('__');
"""

if "kind === 'ticket'" in t:
    print("ok create ja")
elif old_create in t:
    t = t.replace(old_create, new_create, 1)
    print("ok create")
else:
    print("aviso create")

old_wh = """    // order_nsu = vip__userId__hours__labelEnc__ts
    const parts = orderNsu.split('__');
    if (parts[0] !== 'vip' || parts.length < 4) {
      // responde 200 para não ficar em loop; ignora pedido estranho
      return res.status(200).json({ success: true, message: 'ignored' });
    }
"""

new_wh = """    const parts = orderNsu.split('__');
    if (parts[0] === 'ticket') {
      return grantTicket(serviceKey, parts, body, res);
    }
    if (parts[0] !== 'vip' || parts.length < 4) {
      return res.status(200).json({ success: true, message: 'ignored' });
    }
"""

if "grantTicket(" in t and "parts[0] === 'ticket'" in t:
    print("ok webhook ja")
elif old_wh in t:
    t = t.replace(old_wh, new_wh, 1)
    print("ok webhook")
else:
    print("aviso webhook")

grant_fn = "\nasync function grantTicket(serviceKey, parts, body, res) {\n  const userId = parts[1];\n  const tmdbId = parts[2];\n  const hours = Number(parts[3]) || 24;\n  const mediaType = parts[4] === 'tv' ? 'tv' : 'movie';\n  if (!userId || !tmdbId) return res.status(200).json({ success: true, message: 'ticket ignored' });\n  const headers = { apikey: serviceKey, Authorization: 'Bearer ' + serviceKey, 'Content-Type': 'application/json' };\n  const now = new Date();\n  const expires = new Date(now.getTime() + hours * 60 * 60 * 1000);\n  const tx = body.transaction_nsu || body.invoice_slug || 'ip';\n  const row = { user_id: userId, tmdb_id: String(tmdbId), media_type: mediaType, expires_at: expires.toISOString(), paid_at: now.toISOString(), provider_tx: String(tx).slice(0, 80), amount_cents: Number(body.paid_amount || body.amount || 0) || null };\n  const r = await fetch(SUPABASE_URL + '/rest/v1/movie_tickets', { method: 'POST', headers: { ...headers, Prefer: 'return=minimal' }, body: JSON.stringify(row) });\n  if (!r.ok) { const txt = await r.text(); console.error('[infinitepay] ticket upsert', r.status, txt.slice(0, 200)); return res.status(500).json({ success: false, message: 'ticket table' }); }\n  try { require('../lib/vip-status-cache').invalidate(userId); } catch (_) {}\n  console.log('[infinitepay] TICKET ok user=' + userId + ' tmdb=' + tmdbId);\n  return res.status(200).json({ success: true, message: 'ticket' });\n}\n\n"

if "async function grantTicket" not in t:
    t = t.replace("async function handleWebhook(req, res) {", grant_fn + "async function handleWebhook(req, res) {", 1)
    print("ok grantTicket")
else:
    print("ok grantTicket ja")
p.write_text(t)

ms = root / "api/media-sources.js"
mt = ms.read_text()
helper = "\nasync function hasValidTicket(serviceKey, userId, tmdbId) {\n  if (!serviceKey || !userId || !tmdbId) return false;\n  try {\n    const url = SUPABASE_URL + '/rest/v1/movie_tickets?user_id=eq.' + encodeURIComponent(userId) + '&tmdb_id=eq.' + encodeURIComponent(String(tmdbId)) + '&expires_at=gt.' + encodeURIComponent(new Date().toISOString()) + '&select=id,expires_at&limit=1';\n    const r = await fetch(url, { headers: { apikey: serviceKey, Authorization: 'Bearer ' + serviceKey } });\n    if (!r.ok) return false;\n    const rows = await r.json();\n    return Array.isArray(rows) && rows.length > 0;\n  } catch (e) { console.warn('[media-sources] ticket', e.message); return false; }\n}\n\n"
if "function hasValidTicket" not in mt:
    if "async function loadVipTitleConfig" in mt:
        mt = mt.replace("async function loadVipTitleConfig", helper + "async function loadVipTitleConfig", 1)
        print("ok ticket helper")
    else:
        print("aviso ticket helper")
else:
    print("ok ticket helper ja")

old_gate = "  if (needsVip && !access.isVip) {\n    res.status(200).json({\n      error: 'VIP necessário para este título/episódio.',\n      code: 'VIP_REQUIRED',\n"
new_gate = "  if (needsVip && !access.isVip) {\n    const ticketOk = await hasValidTicket(serviceKey, access.userId, tmdbId);\n    if (ticketOk) { access.isVip = true; access.source = access.source || 'ticket'; }\n  }\n\n  if (needsVip && !access.isVip) {\n    res.status(200).json({\n      error: 'VIP necessário para este título/episódio.',\n      code: 'VIP_REQUIRED',\n"
if "hasValidTicket(serviceKey, access.userId, tmdbId)" in mt:
    print("ok gate ja")
elif old_gate in mt:
    mt = mt.replace(old_gate, new_gate, 1)
    print("ok gate")
else:
    print("aviso gate")
ms.write_text(mt)
print("fim apply_ticket_infinitepay")
