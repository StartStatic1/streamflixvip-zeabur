function xtreamUrl(host, user, pass, action, extra) {
  const url = new URL(String(host).replace(/\/+$/, '') + '/player_api.php');
  url.searchParams.set('username', user);
  url.searchParams.set('password', pass);
  url.searchParams.set('action', action);
  if (extra) {
    Object.keys(extra).forEach((k) => {
      if (extra[k] != null && extra[k] !== '') url.searchParams.set(k, String(extra[k]));
    });
  }
  return url.toString();
}

async function xtreamJson(host, user, pass, action, extra) {
  const xr = await fetch(xtreamUrl(host, user, pass, action, extra), {
    headers: { 'User-Agent': 'IPTVSmarters/1.0', Accept: 'application/json' },
  });
  const raw = await xr.text();
  if (!xr.ok) throw new Error('Xtream HTTP ' + xr.status + ' em ' + action);
  try {
    return JSON.parse(raw);
  } catch (_) {
    throw new Error('Xtream sem JSON em ' + action);
  }
}

function mapCats(list, type) {
  return (Array.isArray(list) ? list : []).map((c) => ({
    id: String(type) + ':' + String(c.category_id),
    raw_id: String(c.category_id),
    type,
    name: String(c.category_name || 'Outros'),
    label: (type === 'series' ? 'Series | ' : 'Filmes | ') + String(c.category_name || 'Outros'),
  }));
}

function parseCatId(value) {
  const s = String(value || '');
  const m = s.match(/^(vod|series):(.+)$/i);
  if (m) return { type: m[1].toLowerCase(), id: m[2] };
  return { type: 'vod', id: s };
}

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

function hintName(name) {
  return /reel|novel|short|historia|historias|drama|curta|tiktok|vertical|miniss|mini.?serie/i.test(String(name || ''));
}

async function upsertStory({ SUPABASE_URL, h, have, title, poster, replace }) {
  const key = String(title || '').trim().toLowerCase();
  if (!key) return { storyId: null, created: 0, updated: 0, skipped: 1 };
  let storyId = have.get(key);
  if (storyId && !replace) return { storyId: null, created: 0, updated: 0, skipped: 1 };
  if (!storyId) {
    const sr = await fetch(`${SUPABASE_URL}/rest/v1/reel_stories`, {
      method: 'POST',
      headers: h,
      body: JSON.stringify({
        title,
        poster_url: poster || null,
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
    if (!sr.ok) return { storyId: null, created: 0, updated: 0, skipped: 0, failed: 1 };
    const story = Array.isArray(sd) ? sd[0] : sd;
    have.set(key, story.id);
    return { storyId: story.id, created: 1, updated: 0, skipped: 0 };
  }
  if (poster) {
    await fetch(`${SUPABASE_URL}/rest/v1/reel_stories?id=eq.${encodeURIComponent(storyId)}`, {
      method: 'PATCH',
      headers: h,
      body: JSON.stringify({ poster_url: poster, updated_at: new Date().toISOString() }),
    });
  }
  return { storyId, created: 0, updated: 1, skipped: 0 };
}

async function saveEpisode({ SUPABASE_URL, h, storyId, episode, title, videoUrl }) {
  const er = await fetch(`${SUPABASE_URL}/rest/v1/reel_episodes`, {
    method: 'POST',
    headers: h,
    body: JSON.stringify({
      story_id: storyId,
      episode,
      title: title || null,
      video_url: videoUrl,
      is_active: true,
    }),
  });
  if (er.ok) return;
  await fetch(
    `${SUPABASE_URL}/rest/v1/reel_episodes?story_id=eq.${encodeURIComponent(storyId)}&episode=eq.${episode}`,
    {
      method: 'PATCH',
      headers: h,
      body: JSON.stringify({ video_url: videoUrl, title: title || null, is_active: true }),
    },
  );
}

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
    try {
      const [vod, series] = await Promise.all([
        xtreamJson(xh, xu, xp, 'get_vod_categories').catch(() => []),
        xtreamJson(xh, xu, xp, 'get_series_categories').catch(() => []),
      ]);
      const list = mapCats(vod, 'vod').concat(mapCats(series, 'series'));
      res.status(200).json({
        ok: true,
        host: xh,
        vod: mapCats(vod, 'vod').length,
        series: mapCats(series, 'series').length,
        categories: list,
        suggested: list.filter((c) => hintName(c.name)),
      });
    } catch (e) {
      res.status(400).json({ error: e.message || 'Falha ao listar pastas' });
    }
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
    const parsed = (Array.isArray(body.category_ids) ? body.category_ids : []).map(parseCatId);
    const vodIds = new Set(parsed.filter((p) => p.type === 'vod').map((p) => p.id));
    const seriesIds = new Set(parsed.filter((p) => p.type === 'series').map((p) => p.id));
    if (!xh || !xu || !xp || (!vodIds.size && !seriesIds.size)) {
      res.status(400).json({ error: 'Informe host e as pastas (category_ids)' });
      return true;
    }
    const limit = Math.min(Math.max(Number(body.limit || 40), 1), 80);
    const replace = body.replace === true;
    const existingRes = await fetch(`${SUPABASE_URL}/rest/v1/reel_stories?select=id,title`, { headers: h });
    const existingRows = await existingRes.json();
    const have = new Map();
    for (const row of (Array.isArray(existingRows) ? existingRows : [])) {
      have.set(String(row.title || '').trim().toLowerCase(), row.id);
    }
    let created = 0, updated = 0, skipped = 0, failed = 0, scanned = 0;
    const groups = [];

    if (vodIds.size) {
      try {
        const streams = await xtreamJson(xh, xu, xp, 'get_vod_streams');
        const picked = (Array.isArray(streams) ? streams : []).filter((s) => vodIds.has(String(s.category_id)));
        scanned += picked.length;
        const map = new Map();
        for (const s of picked) {
          const title = storyKey(s.name || '');
          if (!title || !s.stream_id) continue;
          if (!map.has(title.toLowerCase())) map.set(title.toLowerCase(), { title, poster: s.stream_icon || '', items: [] });
          map.get(title.toLowerCase()).items.push(s);
        }
        for (const g of Array.from(map.values()).slice(0, limit)) {
          const up = await upsertStory({ SUPABASE_URL, h, have, title: g.title, poster: g.poster, replace });
          created += up.created || 0;
          updated += up.updated || 0;
          skipped += up.skipped || 0;
          failed += up.failed || 0;
          if (!up.storyId) continue;
          const items = g.items.slice().sort((a, b) => epNum(a.name) - epNum(b.name) || Number(a.stream_id) - Number(b.stream_id));
          let n = 1;
          for (const it of items) {
            const ep = epNum(it.name) || n;
            n += 1;
            const ext = String(it.container_extension || 'mp4').replace(/^\./, '') || 'mp4';
            const videoUrl = String(xh).replace(/\/+$/, '') + '/movie/' + xu + '/' + xp + '/' + it.stream_id + '.' + ext;
            await saveEpisode({ SUPABASE_URL, h, storyId: up.storyId, episode: ep, title: it.name || g.title, videoUrl });
          }
          groups.push(g.title);
        }
      } catch (e) {
        res.status(400).json({ error: e.message || 'Falha VOD' });
        return true;
      }
    }

    if (seriesIds.size) {
      try {
        const seriesList = await xtreamJson(xh, xu, xp, 'get_series');
        const picked = (Array.isArray(seriesList) ? seriesList : []).filter((s) => seriesIds.has(String(s.category_id)));
        scanned += picked.length;
        const slice = picked.slice(0, Math.max(1, limit - groups.length));
        for (const s of slice) {
          const title = storyKey(s.name || s.title || '');
          if (!title || !s.series_id) { failed += 1; continue; }
          const up = await upsertStory({
            SUPABASE_URL,
            h,
            have,
            title,
            poster: s.cover || s.stream_icon || '',
            replace,
          });
          created += up.created || 0;
          updated += up.updated || 0;
          skipped += up.skipped || 0;
          failed += up.failed || 0;
          if (!up.storyId) continue;
          let info = {};
          try {
            info = await xtreamJson(xh, xu, xp, 'get_series_info', { series_id: s.series_id });
          } catch (_) {
            failed += 1;
            continue;
          }
          const seasons = info && info.episodes && typeof info.episodes === 'object' ? info.episodes : {};
          const flat = [];
          Object.keys(seasons).forEach((sk) => {
            const arr = Array.isArray(seasons[sk]) ? seasons[sk] : [];
            arr.forEach((ep) => flat.push(ep));
          });
          flat.sort((a, b) => Number(a.episode_num || 0) - Number(b.episode_num || 0) || Number(a.id || 0) - Number(b.id || 0));
          let n = 1;
          for (const it of flat) {
            const ep = Number(it.episode_num || n);
            n += 1;
            const ext = String(it.container_extension || 'mp4').replace(/^\./, '') || 'mp4';
            const videoUrl = String(xh).replace(/\/+$/, '') + '/series/' + xu + '/' + xp + '/' + it.id + '.' + ext;
            await saveEpisode({
              SUPABASE_URL,
              h,
              storyId: up.storyId,
              episode: ep,
              title: it.title || (title + ' EP ' + ep),
              videoUrl,
            });
          }
          groups.push(title);
        }
      } catch (e) {
        res.status(400).json({ error: e.message || 'Falha series' });
        return true;
      }
    }

    res.status(200).json({
      ok: true,
      created,
      updated,
      skipped,
      failed,
      groups: groups.length,
      scanned,
      vod_cats: vodIds.size,
      series_cats: seriesIds.size,
    });
    return true;
  }

  if (action === 'delete_all') {
    if (String(body.confirm || '') !== 'APAGAR') {
      res.status(400).json({ error: 'Confirme com confirm=APAGAR' });
      return true;
    }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/reel_stories?id=neq.00000000-0000-0000-0000-000000000000`, {
      method: 'DELETE',
      headers: h,
    });
    if (!r.ok) { const d = await r.json(); res.status(502).json({ error: d }); return true; }
    res.status(200).json({ ok: true });
    return true;
  }

  return false;
};
