const SUPABASE_URL =
  process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';

async function requireAdmin(req, res, serviceKey) {
  const authHeader = req.headers['authorization'] || '';
  const userToken = authHeader.replace('Bearer ', '').trim();
  if (!userToken) {
    res.status(401).json({ error: 'Token nao fornecido' });
    return null;
  }
  const userRes = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
    headers: { apikey: serviceKey, Authorization: `Bearer ${userToken}` },
  });
  if (!userRes.ok) {
    res.status(401).json({ error: 'Token invalido' });
    return null;
  }
  const userJson = await userRes.json();
  const userId = userJson && userJson.id;
  if (!userId) {
    res.status(401).json({ error: 'Token invalido' });
    return null;
  }
  const adminRes = await fetch(
    `${SUPABASE_URL}/rest/v1/vip_panel_admins?id=eq.${encodeURIComponent(userId)}&select=id`,
    { headers: { apikey: serviceKey, Authorization: `Bearer ${serviceKey}` } },
  );
  const adminRows = await adminRes.json();
  if (!adminRes.ok || !Array.isArray(adminRows) || adminRows.length === 0) {
    res.status(403).json({ error: 'Acesso negado' });
    return null;
  }
  return userId;
}

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
  const adminId = await requireAdmin(req, res, serviceKey);
  if (!adminId) return;

  let body = req.body;
  if (typeof body === 'string') {
    try { body = JSON.parse(body); } catch (e) { body = {}; }
  }
  const action = body && body.action;
  const h = {
    apikey: serviceKey,
    Authorization: `Bearer ${serviceKey}`,
    'Content-Type': 'application/json',
    Prefer: 'return=representation',
  };

  try {
    if (action === 'list') {
      const r = await fetch(
        `${SUPABASE_URL}/rest/v1/reel_stories?select=id,title,subtitle,poster_url,genre,language,is_active,vip_only,use_addons,sort_order,created_at&order=sort_order.desc,created_at.desc`,
        { headers: h },
      );
      const stories = await r.json();
      if (!r.ok) {
        res.status(502).json({ error: 'Rode sql/reels.sql no Supabase.', detail: stories });
        return;
      }
      const ids = (stories || []).map((s) => s.id);
      let counts = {};
      if (ids.length) {
        const er = await fetch(
          `${SUPABASE_URL}/rest/v1/reel_episodes?select=story_id&story_id=in.(${ids.join(',')})`,
          { headers: h },
        );
        const eps = await er.json();
        if (Array.isArray(eps)) {
          for (const e of eps) counts[e.story_id] = (counts[e.story_id] || 0) + 1;
        }
      }
      res.status(200).json({
        stories: (stories || []).map((s) => ({ ...s, episode_count: counts[s.id] || 0 })),
      });
      return;
    }

    if (action === 'save_story') {
      const row = {
        title: String(body.title || '').trim(),
        subtitle: body.subtitle || null,
        poster_url: body.poster_url || null,
        genre: body.genre || null,
        language: body.language || 'pt-BR',
        is_active: body.is_active !== false,
        vip_only: body.vip_only !== false,
        use_addons: body.use_addons !== false,
        sort_order: Number(body.sort_order || 0),
        notes: body.notes || null,
        updated_at: new Date().toISOString(),
      };
      if (!row.title) {
        res.status(400).json({ error: 'Titulo obrigatorio' });
        return;
      }
      let r;
      if (body.id) {
        r = await fetch(
          `${SUPABASE_URL}/rest/v1/reel_stories?id=eq.${encodeURIComponent(body.id)}`,
          { method: 'PATCH', headers: h, body: JSON.stringify(row) },
        );
      } else {
        r = await fetch(`${SUPABASE_URL}/rest/v1/reel_stories`, {
          method: 'POST', headers: h, body: JSON.stringify(row),
        });
      }
      const data = await r.json();
      if (!r.ok) {
        res.status(502).json({ error: data });
        return;
      }
      res.status(200).json({ ok: true, story: Array.isArray(data) ? data[0] : data });
      return;
    }

    if (action === 'delete_story') {
      if (!body.id) {
        res.status(400).json({ error: 'id obrigatorio' });
        return;
      }
      const r = await fetch(
        `${SUPABASE_URL}/rest/v1/reel_stories?id=eq.${encodeURIComponent(body.id)}`,
        { method: 'DELETE', headers: h },
      );
      if (!r.ok) {
        const d = await r.json();
        res.status(502).json({ error: d });
        return;
      }
      res.status(200).json({ ok: true });
      return;
    }

    if (action === 'list_episodes') {
      if (!body.story_id) {
        res.status(400).json({ error: 'story_id obrigatorio' });
        return;
      }
      const r = await fetch(
        `${SUPABASE_URL}/rest/v1/reel_episodes?story_id=eq.${encodeURIComponent(body.story_id)}&select=id,story_id,episode,title,video_url,duration_seconds,is_active&order=episode.asc`,
        { headers: h },
      );
      const episodes = await r.json();
      if (!r.ok) {
        res.status(502).json({ error: episodes });
        return;
      }
      res.status(200).json({ episodes });
      return;
    }

    if (action === 'save_episode') {
      const row = {
        story_id: body.story_id,
        episode: Number(body.episode || 0),
        title: body.title || null,
        video_url: String(body.video_url || '').trim(),
        duration_seconds: body.duration_seconds ? Number(body.duration_seconds) : null,
        is_active: body.is_active !== false,
      };
      if (!row.story_id || row.episode < 1) {
        res.status(400).json({ error: 'story_id e episode >= 1' });
        return;
      }
      let r;
      if (body.id) {
        r = await fetch(
          `${SUPABASE_URL}/rest/v1/reel_episodes?id=eq.${encodeURIComponent(body.id)}`,
          { method: 'PATCH', headers: h, body: JSON.stringify(row) },
        );
      } else {
        r = await fetch(`${SUPABASE_URL}/rest/v1/reel_episodes`, {
          method: 'POST', headers: h, body: JSON.stringify(row),
        });
      }
      const data = await r.json();
      if (!r.ok) {
        res.status(502).json({ error: data });
        return;
      }
      res.status(200).json({ ok: true, episode: Array.isArray(data) ? data[0] : data });
      return;
    }

    if (action === 'delete_episode') {
      if (!body.id) {
        res.status(400).json({ error: 'id obrigatorio' });
        return;
      }
      const r = await fetch(
        `${SUPABASE_URL}/rest/v1/reel_episodes?id=eq.${encodeURIComponent(body.id)}`,
        { method: 'DELETE', headers: h },
      );
      if (!r.ok) {
        const d = await r.json();
        res.status(502).json({ error: d });
        return;
      }
      res.status(200).json({ ok: true });
      return;
    }

    res.status(400).json({ error: 'action invalida' });
  } catch (e) {
    res.status(500).json({ error: e.message || 'erro' });
  }
};
