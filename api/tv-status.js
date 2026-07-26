// api/tv-status.js
// Revalida a ativação de um aparelho de TV — chamado pela TV a cada
// abertura do app (além de confiar no cache local), pra pegar revogações
// feitas manualmente no Supabase (is_active = false ou linha apagada em
// tv_activations) mesmo sem a pessoa digitar um código de novo.
//
// Uso no front-end (TV):
//   POST /api/tv-status   body: { deviceId: "<android_id>" }

const SUPABASE_URL = 'https://gkujbjpvphuvrejpvvtz.supabase.co';

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
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
    res.status(500).json({ error: 'SUPABASE_SERVICE_ROLE_KEY não configurada nas env vars da Vercel' });
    return;
  }

  let body = req.body;
  if (typeof body === 'string') {
    try { body = JSON.parse(body); } catch (e) { body = {}; }
  }
  const deviceId = (body?.deviceId || '').trim();
  if (!deviceId) {
    res.status(400).json({ error: 'Informe "deviceId".' });
    return;
  }

  const headers = {
    'apikey': serviceKey,
    'Authorization': `Bearer ${serviceKey}`,
    'Content-Type': 'application/json',
  };

  try {
    const url = `${SUPABASE_URL}/rest/v1/tv_activations?device_id=eq.${encodeURIComponent(deviceId)}&select=is_active,expires_at,plan_label`;
    const r = await fetch(url, { headers });
    const rows = await r.json();
    const row = Array.isArray(rows) && rows.length ? rows[0] : null;

    if (!row || !row.is_active) {
      res.status(200).json({ active: false });
      return;
    }

    const expiresAt = new Date(row.expires_at);
    const active = expiresAt > new Date();

    res.status(200).json({
      active,
      expiresAt: row.expires_at,
      planLabel: row.plan_label || null,
    });
  } catch (err) {
    console.error('tv-status error:', err);
    // Falha de rede/servidor não deve derrubar quem já está ativo — a TV
    // decide localmente (mantém o cache) quando essa checagem falha.
    res.status(500).json({ error: 'Erro ao checar status.' });
  }
};
