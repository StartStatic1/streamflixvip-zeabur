// api/comments.js
// CRUD de comentários — roda no servidor (Vercel) usando a service role key,
// mesmo padrão de api/redeem-vip.js. A tabela já tem RLS (ver
// supabase/comments.sql), então mesmo se essa key vazasse o dano seria
// limitado, mas mantemos a escrita no servidor para validar o texto
// (tamanho, autoria) antes de gravar.
//
// Uso no front-end:
//   GET  /api/comments?titleId=123&titleType=movie&limit=20&offset=0
//   POST /api/comments   body: { titleId, titleType, userId, userName, userAvatar, body }
//   POST /api/comments   body: { action:'vote', commentId, userId, vote: 1 | -1 | 0 }
//   DELETE /api/comments body: { commentId, userId }
//
// Configuração necessária na Vercel:
//   Settings > Environment Variables > SUPABASE_SERVICE_ROLE_KEY = <service role key>
//   (já deve estar configurada se você já usa api/redeem-vip.js)

const SUPABASE_URL = 'https://gkujbjpvphuvrejpvvtz.supabase.co';

function headersFor(serviceKey) {
  return {
    'apikey': serviceKey,
    'Authorization': `Bearer ${serviceKey}`,
    'Content-Type': 'application/json',
  };
}

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, DELETE, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
  if (req.method === 'OPTIONS') { res.status(200).end(); return; }

  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!serviceKey) {
    res.status(500).json({ error: 'SUPABASE_SERVICE_ROLE_KEY não configurada nas env vars da Vercel' });
    return;
  }
  const headers = headersFor(serviceKey);

  try {
    if (req.method === 'GET') {
      const { titleId, titleType, limit, offset } = req.query;
      if (!titleId || !titleType) {
        res.status(400).json({ error: 'Informe "titleId" e "titleType".' });
        return;
      }
      const lim = Math.min(Number(limit) || 20, 50);
      const off = Number(offset) || 0;

      const url = `${SUPABASE_URL}/rest/v1/comments`
        + `?title_id=eq.${encodeURIComponent(titleId)}`
        + `&title_type=eq.${encodeURIComponent(titleType)}`
        + `&select=id,title_id,title_type,user_id,user_name,user_avatar,body,likes,dislikes,created_at`
        + `&order=created_at.desc`
        + `&limit=${lim}&offset=${off}`;

      const r = await fetch(url, { headers: { ...headers, 'Prefer': 'count=exact' } });
      const rows = await r.json();
      if (!r.ok) { res.status(502).json({ error: 'Falha ao buscar comentários.' }); return; }

      const total = r.headers.get('content-range')?.split('/')?.[1] || rows.length;
      res.setHeader('Cache-Control', 's-maxage=15, stale-while-revalidate=60');
      res.status(200).json({ comments: rows, total: Number(total) });
      return;
    }

    if (req.method === 'POST') {
      let body = req.body;
      if (typeof body === 'string') { try { body = JSON.parse(body); } catch (e) { body = {}; } }

      // ── Votar em um comentário (like/dislike/remover voto) ──
      if (body?.action === 'vote') {
        const { commentId, userId, vote } = body;
        if (!commentId || !userId || ![1, -1, 0].includes(vote)) {
          res.status(400).json({ error: 'Informe "commentId", "userId" e "vote" (1, -1 ou 0).' });
          return;
        }
        if (vote === 0) {
          // Remove o voto existente
          await fetch(
            `${SUPABASE_URL}/rest/v1/comment_votes?comment_id=eq.${commentId}&user_id=eq.${userId}`,
            { method: 'DELETE', headers }
          );
        } else {
          // Upsert (troca like<->dislike ou cria o voto)
          await fetch(`${SUPABASE_URL}/rest/v1/comment_votes`, {
            method: 'POST',
            headers: { ...headers, 'Prefer': 'resolution=merge-duplicates,return=minimal' },
            body: JSON.stringify({ comment_id: commentId, user_id: userId, vote }),
          });
        }
        // Devolve o comentário atualizado (o trigger SQL já recalculou likes/dislikes)
        const r = await fetch(
          `${SUPABASE_URL}/rest/v1/comments?id=eq.${commentId}&select=id,likes,dislikes`,
          { headers }
        );
        const rows = await r.json();
        res.status(200).json({ success: true, comment: rows?.[0] || null });
        return;
      }

      // ── Criar comentário novo ──
      const { titleId, titleType, userId, userName, userAvatar, body: text } = body || {};
      if (!titleId || !titleType || !userId || !userName || !text) {
        res.status(400).json({ error: 'Informe "titleId", "titleType", "userId", "userName" e "body".' });
        return;
      }
      const trimmed = String(text).trim();
      if (trimmed.length < 1 || trimmed.length > 1000) {
        res.status(400).json({ error: 'Comentário deve ter entre 1 e 1000 caracteres.' });
        return;
      }

      const r = await fetch(`${SUPABASE_URL}/rest/v1/comments`, {
        method: 'POST',
        headers: { ...headers, 'Prefer': 'return=representation' },
        body: JSON.stringify({
          title_id: Number(titleId),
          title_type: titleType,
          user_id: userId,
          user_name: userName,
          user_avatar: userAvatar || null,
          body: trimmed,
        }),
      });
      const rows = await r.json();
      if (!r.ok) {
        console.error('comments insert error:', rows);
        res.status(502).json({ error: 'Falha ao publicar comentário.' });
        return;
      }
      res.status(201).json({ success: true, comment: rows[0] });
      return;
    }

    if (req.method === 'DELETE') {
      let body = req.body;
      if (typeof body === 'string') { try { body = JSON.parse(body); } catch (e) { body = {}; } }
      const { commentId, userId } = body || {};
      if (!commentId || !userId) {
        res.status(400).json({ error: 'Informe "commentId" e "userId".' });
        return;
      }
      // Restringe a deleção ao próprio autor mesmo usando a service key
      // (o RLS da tabela já faria isso, mas fica explícito aqui também).
      const r = await fetch(
        `${SUPABASE_URL}/rest/v1/comments?id=eq.${commentId}&user_id=eq.${userId}`,
        { method: 'DELETE', headers: { ...headers, 'Prefer': 'return=representation' } }
      );
      const rows = await r.json();
      if (!r.ok || !Array.isArray(rows) || rows.length === 0) {
        res.status(404).json({ error: 'Comentário não encontrado ou não pertence a este usuário.' });
        return;
      }
      res.status(200).json({ success: true });
      return;
    }

    res.status(405).json({ error: 'Method not allowed' });
  } catch (err) {
    console.error('comments API error:', err);
    res.status(502).json({ error: 'Falha ao processar comentários.', detail: String(err) });
  }
};
