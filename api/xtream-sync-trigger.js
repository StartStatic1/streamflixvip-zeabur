// api/xtream-sync-trigger.js
//
// Endpoint para disparar sincronização Xtream manualmente via webhook/cron.
// Uso: GET /api/xtream-sync-trigger?secret=SEU_SECRET&sourceId=ID_DA_FONTE
//
// Exemplo:
// https://seu-dominio.com/api/xtream-sync-trigger?secret=xH7PEAfgTQMdgLH2UQpUUCqdsIeHOrM9&sourceId=a784f930-0486-4776-9831-3d3151252e3d

const SUPABASE_URL = 'https://gkujbjpvphuvrejpvvtz.supabase.co';

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  if (req.method === 'OPTIONS') { res.status(200).end(); return; }
  if (req.method !== 'GET') { res.status(405).json({ error: 'Method not allowed' }); return; }

  const secret = req.query.secret;
  const sourceId = req.query.sourceId;
  const expectedSecret = process.env.IPTV_SYNC_SECRET;

  if (!secret || secret !== expectedSecret) {
    res.status(401).json({ error: 'Secret inválido' });
    return;
  }

  if (!sourceId) {
    res.status(400).json({ error: 'sourceId obrigatório' });
    return;
  }

  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!serviceKey) {
    res.status(500).json({ error: 'SUPABASE_SERVICE_ROLE_KEY não configurada' });
    return;
  }

  try {
    // Chamar o xtream-sync.js
    const xtreamSync = require('./xtream-sync');
    const mockReq = { body: { sourceId } };
    const mockRes = {
      status: (code) => ({
        json: (data) => {
          res.status(code).json(data);
        },
        end: () => {
          res.status(code).end();
        },
      }),
      setHeader: () => {},
    };

    await xtreamSync(mockReq, mockRes);
  } catch (err) {
    console.error('[xtream-sync-trigger] Erro:', err);
    res.status(502).json({ error: 'Erro na sincronização', detail: String(err) });
  }
};
