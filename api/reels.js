const SUPABASE_URL =
  process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-User-Id');
  if (req.method === 'OPTIONS') {
    res.status(200).end();
    return;
  }
  if (req.method !== 'GET') {
    res.status(405).json({ error: 'Method not allowed' });
    return;
  }
  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!serviceKey) {
    res.status(500).json({ error: 'service key ausente' });
    return;
  }
  const h = {
    apikey: serviceKey,
    Authorization: `Bearer ${serviceKey}`,
  };
  const action = String(req.query.action || 'list');
  const storyId = req.query.id || req.query.story_id;

  try {
    if (action === 'list') {
      const r = await fetch(
        `${SUPABASE_URL}/rest/v1/reel_stories?is_active=eq.true&select=id,title,subtitle,poster_url,genre,language,vip_only,use_addons,sort_order&order=sort_order.desc,created_at.desc`,
        { headers: h },
      );
      const stories = await r.json();
      if (!r.ok) {
        res.status(502).json({ error: 'Rode sql/reels.sql', detail: stories });
        return;
      }
      res.status(200).json({ stories: stories || [] });
      return;
    }
    if (action === 'story') {
      if (!storyId) {
        res.status(400).json({ error: 'id obrigatorio' });
        return;
      }
      const r = await fetch(
        `${SUPABASE_URL}/rest/v1/reel_stories?id=eq.${encodeURIComponent(storyId)}&select=*`,
        { headers: h },
      );
      const rows = await r.json();
      if (!r.ok || !Array.isArray(rows) || !rows[0]) {
        res.status(404).json({ error: 'historia nao encontrada' });
        return;
      }
      const er = await fetch(
        `${SUPABASE_URL}/rest/v1/reel_episodes?story_id=eq.${encodeURIComponent(storyId)}&is_active=eq.true&select=id,episode,title,video_url,duration_seconds&order=episode.asc`,
        { headers: h },
      );
      const episodes = await er.json();
      res.status(200).json({ story: rows[0], episodes: Array.isArray(episodes) ? episodes : [] });
      return;
    }
    res.status(400).json({ error: 'action invalida' });
  } catch (e) {
    res.status(500).json({ error: e.message || 'erro' });
  }
};
