module.exports = async function handleXtreamReels({ action, body, res, serviceKey, SUPABASE_URL, h }) {
  if (action === 'probe_xtream') {
    let xh = String(body.host || body.xtream_host || '').trim().replace(/\/+$/, '');
    let xu = String(body.user || body.xtream_user || '').trim();
    let xp = String(body.pass || body.xtream_pass || '').trim();
    if (!xh || !xu || !xp) {
      const br = await fetch(
        `${SUPABASE_URL}/rest/v1/iptv_bridges?is_active=eq.true&select=name,xtream_host,xtream_user,xtream_pass&order=created_at.desc&limit=1`,
        { headers: { apikey: serviceKey, Authorization: `Bearer ${serviceKey}` } },
      ).then((r) => r.json()).catch(() => []);
      const row = Array.isArray(br) ? br[0] : null;
      if (row) { xh = row.xtream_host; xu = row.xtream_user; xp = row.xtream_pass; }
    }
    if (!xh || !xu || !xp) {
      res.status(400).json({ error: 'Informe host, usuario e senha ou cadastre uma ponte Xtream' });
      return true;
    }
    const url = new URL(String(xh).replace(/\/+$/, '') + '/player_api.php');
    url.searchParams.set('username', xu);
    url.searchParams.set('password', xp);
    url.searchParams.set('action', 'get_vod_categories');
    const xr = await fetch(url.toString(), {
      headers: { 'User-Agent': 'IPTVSmarters/1.0', Accept: 'application/json' },
    });
    const raw = await xr.text();
    if (!xr.ok) { res.status(400).json({ error: 'Xtream HTTP ' + xr.status }); return true; }
    let cats = [];
    try { cats = JSON.parse(raw); } catch (_) { res.status(400).json({ error: 'Xtream sem JSON' }); return true; }
    const list = (Array.isArray(cats) ? cats : []).map((c) => ({
      id: String(c.category_id),
      name: String(c.category_name || 'Outros'),
    }));
    const hint = /reel|novel|short|historia|historias|drama|curta|tiktok|vertical/i;
    res.status(200).json({
      ok: true,
      host: xh,
      categories: list,
      suggested: list.filter((c) => hint.test(c.name)),
    });
    return true;
  }

  if (action === 'import_xtream') {
    let xh = String(body.host || body.xtream_host || '').trim().replace(/\/+$/, '');
    let xu = String(body.user || body.xtream_user || '').trim();
    let xp = String(body.pass || body.xtream_pass || '').trim();
    if (!xh || !xu || !xp) {
      const br = await fetch(
        `${SUPABASE_URL}/rest/v1/iptv_bridges?is_active=eq.true&select=xtream_host,xtream_user,xtream_pass&order=created_at.desc&limit=1`,
        { headers: { apikey: serviceKey, Authorization: `Bearer ${serviceKey}` } },
      ).then((r) => r.json()).catch(() => []);
      const row = Array.isArray(br) ? br[0] : null;
      if (row) { xh = row.xtream_host; xu = row.xtream_user; xp = row.xtream_pass; }
    }
    const catIds = new Set((Array.isArray(body.category_ids) ? body.category_ids : []).map((x) => String(x)));
    if (!xh || !xu || !xp || !catIds.size) {
      res.status(400).json({ error: 'Informe host e as pastas (category_ids)' });
      return true;
    }
    const limit = Math.min(Math.max(Number(body.limit || 40), 1), 80);
    const replace = body.replace === true;
    const url = new URL(String(xh).replace(/\/+$/, '') + '/player_api.php');
    url.searchParams.set('username', xu);
    url.searchParams.set('password', xp);
    url.searchParams.set('action', 'get_vod_streams');
    const xr = await fetch(url.toString(), {
      headers: { 'User-Agent': 'IPTVSmarters/1.0', Accept: 'application/json' },
    });
    const raw = await xr.text();
    if (!xr.ok) { res.status(400).json({ error: 'Xtream HTTP ' + xr.status }); return true; }
    let streams = [];
    try { streams = JSON.parse(raw); } catch (_) { res.status(400).json({ error: 'Xtream sem JSON' }); return true; }
    const picked = (Array.isArray(streams) ? streams : []).filter((s) => catIds.has(String(s.category_id)));
    function storyKey(name) {
      return String(name || '')
        .replace(/\s*\[(LEG|DUB)\]\s*$/i, '')
        .replace(/\s*[-|]\s*(EP|E|CAP|CAPITULO|PARTE)\s*\d+.*$/i, '')
        .replace(/\s+(EP|E|CAP)\s*\d+.*$/i, '')
        .replace(/\s*#\d+\s*$/g, '')
        .trim();
    }
    function epNum(name) {
      const m = String(name || '').match(/(?:EP|E|CAP|CAPITULO|PARTE|#)\s*(\d+)/i);
      return m ? Number(m[1]) : 0;
    }
    function vodUrl(streamId, ext) {
      const e = String(ext || 'mp4').replace(/^\./, '') || 'mp4';
      return String(xh).replace(/\/+$/, '') + '/movie/' + xu + '/' + xp + '/' + streamId + '.' + e;
    }
    const groups = new Map();
    for (const s of picked) {
      const title = storyKey(s.name || '');
      if (!title || !s.stream_id) continue;
      if (!groups.has(title.toLowerCase())) groups.set(title.toLowerCase(), { title, poster: s.stream_icon || '', items: [] });
      groups.get(title.toLowerCase()).items.push(s);
    }
    const existingRes = await fetch(`${SUPABASE_URL}/rest/v1/reel_stories?select=id,title`, { headers: h });
    const existingRows = await existingRes.json();
    const have = new Map();
    for (const row of (Array.isArray(existingRows) ? existingRows : [])) {
      have.set(String(row.title || '').trim().toLowerCase(), row.id);
    }
    let created = 0, updated = 0, skipped = 0, failed = 0;
    const titles = Array.from(groups.values()).slice(0, limit);
    for (const g of titles) {
      const key = g.title.toLowerCase();
      let storyId = have.get(key);
      if (storyId && !replace) { skipped += 1; continue; }
      if (!storyId) {
        const sr = await fetch(`${SUPABASE_URL}/rest/v1/reel_stories`, {
          method: 'POST', headers: h,
          body: JSON.stringify({
            title: g.title,
            poster_url: g.poster || null,
            genre: 'Reels',
            language: 'pt-BR',
            is_active: true,
            vip_only: true,
            use_addons: false,
            sort_order: 0,
            updated_at: new Date().toISOString(),
          }),
        });
        const sd = await sr.json();
        if (!sr.ok) { failed += 1; continue; }
        const story = Array.isArray(sd) ? sd[0] : sd;
        storyId = story.id;
        have.set(key, storyId);
        created += 1;
      } else {
        updated += 1;
        if (g.poster) {
          await fetch(`${SUPABASE_URL}/rest/v1/reel_stories?id=eq.${encodeURIComponent(storyId)}`, {
            method: 'PATCH', headers: h,
            body: JSON.stringify({ poster_url: g.poster, updated_at: new Date().toISOString() }),
          });
        }
      }
      const items = g.items.slice().sort((a, b) => epNum(a.name) - epNum(b.name) || Number(a.stream_id) - Number(b.stream_id));
      let n = 1;
      for (const it of items) {
        const ep = epNum(it.name) || n;
        n += 1;
        const videoUrl = vodUrl(it.stream_id, it.container_extension);
        const er = await fetch(`${SUPABASE_URL}/rest/v1/reel_episodes`, {
          method: 'POST', headers: h,
          body: JSON.stringify({
            story_id: storyId,
            episode: ep,
            title: it.name || g.title,
            video_url: videoUrl,
            is_active: true,
          }),
        });
        if (!er.ok) {
          await fetch(
            `${SUPABASE_URL}/rest/v1/reel_episodes?story_id=eq.${encodeURIComponent(storyId)}&episode=eq.${ep}`,
            { method: 'PATCH', headers: h, body: JSON.stringify({ video_url: videoUrl, title: it.name || g.title, is_active: true }) },
          );
        }
      }
    }
    res.status(200).json({ ok: true, created, updated, skipped, failed, groups: titles.length, scanned: picked.length });
    return true;
  }

  if (action === 'delete_all') {
    if (String(body.confirm || '') !== 'APAGAR') {
      res.status(400).json({ error: 'Confirme com confirm=APAGAR' });
      return true;
    }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/reel_stories?id=neq.00000000-0000-0000-0000-000000000000`, {
      method: 'DELETE', headers: h,
    });
    if (!r.ok) { const d = await r.json(); res.status(502).json({ error: d }); return true; }
    res.status(200).json({ ok: true });
    return true;
  }

  return false;
};
