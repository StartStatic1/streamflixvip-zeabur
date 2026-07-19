// api/heartbeat.js
//
// Endpoint chamado periodicamente (a cada ~60s) pelo site e pelo app
// nativo, enquanto o usuário está com a página/app aberto, só pra marcar
// "essa pessoa está online agora". Sem isso, o que já existia
// (last_login_at, em track-login.js) só diz QUANDO alguém logou, não SE
// ainda está usando — alguém pode ter logado ontem e nunca mais voltado
// que ainda contaria como "recente" sem esse sinal mais granular.
//
// Como o painel decide quem está "online": qualquer usuário cujo
// last_seen_at foi atualizado nos últimos 2 minutos (ver ONLINE_WINDOW_MS
// em Public/admin.html). Não é um WebSocket nem presença em tempo real de
// verdade — é polling simples, suficiente pra um painel administrativo
// que não precisa atualizar por segundo.
//
// Uso no front-end / app:
//   POST /api/heartbeat   body: { userId }

const SUPABASE_URL = 'https://gkujbjpvphuvrejpvvtz.supabase.co';

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
  if (req.method === 'OPTIONS') { res.status(200).end(); return; }
  if (req.method !== 'POST') { res.status(405).json({ error: 'Method not allowed' }); return; }

  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!serviceKey) { res.status(500).json({ error: 'SUPABASE_SERVICE_ROLE_KEY não configurada' }); return; }

  let body = req.body;
  if (typeof body === 'string') { try { body = JSON.parse(body); } catch (e) { body = {}; } }

  const userId = (body?.userId || '').trim();
  if (!userId) { res.status(400).json({ error: 'Informe "userId".' }); return; }

  const headers = {
    'apikey': serviceKey,
    'Authorization': `Bearer ${serviceKey}`,
    'Content-Type': 'application/json',
  };

  try {
    // UPDATE puro (não upsert) — se o usuário ainda não tem linha em
    // vip_status por algum motivo (não deveria acontecer, já que
    // trackLogin roda antes), o heartbeat simplesmente não faz nada em
    // vez de criar uma linha incompleta sem email/nome.
    const updateRes = await fetch(
      `${SUPABASE_URL}/rest/v1/vip_status?user_id=eq.${encodeURIComponent(userId)}`,
      {
        method: 'PATCH',
        headers: { ...headers, 'Prefer': 'return=minimal' },
        body: JSON.stringify({ last_seen_at: new Date().toISOString() }),
      },
    );

    if (!updateRes.ok) {
      const errText = await updateRes.text();
      console.error('heartbeat update error:', errText);
      res.status(200).json({ success: false });
      return;
    }

    res.status(200).json({ success: true });
  } catch (err) {
    console.error('heartbeat error:', err);
    // Mesma filosofia de track-login: isso é telemetria, nunca deve
    // quebrar a experiência de quem está usando o site/app.
    res.status(200).json({ success: false });
  }
};
