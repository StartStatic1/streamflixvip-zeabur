#!/usr/bin/env python3
"""GET /api/infinitepay/ticket-status — ingresso valido do filme."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
p = root / "api/infinitepay.js"
t = p.read_text()
n = 0

t2 = t.replace(
    "res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');",
    "res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');",
    1,
)
if t2 != t:
    t = t2
    n += 1
    print("ok methods")
else:
    print("ok methods ja")

if "/ticket-status" not in t:
    t = t.replace(
        "  if (path.includes('/create-link')) return createLink(req, res);\n",
        "  if (path.includes('/ticket-status')) return ticketStatus(req, res);\n  if (path.includes('/create-link')) return createLink(req, res);\n",
        1,
    )
    n += 1
    print("ok route")
else:
    print("ok route ja")

if "async function ticketStatus" not in t:
    fn = r'''
async function ticketStatus(req, res) {
  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!serviceKey) return res.status(500).json({ active: false, error: 'config' });
  const userId = String((req.query && (req.query.userId || req.query.user_id)) || '').trim();
  const tmdbId = String((req.query && (req.query.tmdbId || req.query.tmdb_id)) || '').trim();
  if (!userId || !tmdbId) return res.status(400).json({ active: false, error: 'userId e tmdbId' });
  const url = SUPABASE_URL + '/rest/v1/movie_tickets?user_id=eq.' + encodeURIComponent(userId)
    + '&tmdb_id=eq.' + encodeURIComponent(tmdbId)
    + '&expires_at=gt.' + encodeURIComponent(new Date().toISOString())
    + '&select=expires_at,paid_at&order=expires_at.desc&limit=1';
  const r = await fetch(url, { headers: { apikey: serviceKey, Authorization: 'Bearer ' + serviceKey } });
  if (!r.ok) return res.status(200).json({ active: false });
  const rows = await r.json();
  const row = Array.isArray(rows) && rows[0];
  if (!row) return res.status(200).json({ active: false, tmdbId });
  return res.status(200).json({ active: true, tmdbId, expiresAt: row.expires_at });
}

'''
    t = t.replace("async function createLink", fn + "async function createLink", 1)
    n += 1
    print("ok status fn")
else:
    print("ok status fn ja")

p.write_text(t)
print("fim apply_ticket_status", n)
