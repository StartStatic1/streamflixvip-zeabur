from pathlib import Path
JS_BLOCK = r'''
    if (action === \'import_csv\') {
      const raw = String(body.csv || \'\');
      const limit = Math.min(Math.max(Number(body.limit || 40), 1), 80);
      const lines = raw.replace(/^\uFEFF/, \'\').split(/\r?\n/).filter((l) => l.trim());
      if (lines.length < 2) { res.status(400).json({ error: \'CSV vazio\' }); return; }
      const header = lines[0].toLowerCase();
      const cols = header.split(\',\').map((c) => c.trim().replace(/^"|"$/g, \'\'));
      const idx = (names) => { for (const n of names) { const i = cols.indexOf(n); if (i >= 0) return i; } return -1; };
      const iNome = idx([\'nome\', \'title\', \'titulo\']);
      const iIdioma = idx([\'idioma\', \'language\', \'lang\']);
      const iImg = idx([\'link_imagem\', \'capa\', \'poster\', \'poster_url\', \'imagem\']);
      const iMp4 = idx([\'link_mp4\', \'url\', \'video\', \'video_url\', \'mp4\']);
      if (iNome < 0 || iMp4 < 0) { res.status(400).json({ error: \'CSV precisa de nome e link_mp4\' }); return; }
      function parseLine(line) {
        const out = []; let cur = \'\'; let q = false;
        for (let i = 0; i < line.length; i++) {
          const ch = line[i];
          if (ch === \'"\') { q = !q; continue; }
          if (ch === \',\' && !q) { out.push(cur); cur = \'\'; continue; }
          cur += ch;
        }
        out.push(cur); return out;
      }
      function cleanTitle(t) {
        return String(t || \'\').replace(/\s*\\[(LEG|DUB)\\]\s*$/i, \'\').replace(/\s*\\((Legendado|Dublado)\\)\s*$/i, \'\').trim();
      }
      const existingRes = await fetch(`${SUPABASE_URL}/rest/v1/reel_stories?select=title`, { headers: h });
      const existingRows = await existingRes.json();
      const have = new Set((Array.isArray(existingRows) ? existingRows : []).map((r) => String(r.title || \'\').trim().toLowerCase()));
      let created = 0, skipped = 0, failed = 0;
      const errors = [];
      const rows = lines.slice(1).slice(0, limit);
      for (const line of rows) {
        const parts = parseLine(line);
        const title = cleanTitle(parts[iNome] || \'\');
        const url = String(parts[iMp4] || \'\').trim();
        const poster = iImg >= 0 ? String(parts[iImg] || \'\').trim().replace(/^"|"$/g, \'\') : \'\';
        const idioma = iIdioma >= 0 ? String(parts[iIdioma] || \'\').trim() : \'\';
        if (!title || !/^https?:\\/\\//i.test(url)) { failed += 1; continue; }
        if (have.has(title.toLowerCase())) { skipped += 1; continue; }
        const storyRow = { title, subtitle: idioma || null, poster_url: poster || null, genre: \'Reels\', language: \'pt-BR\', is_active: true, vip_only: true, use_addons: false, sort_order: 0, updated_at: new Date().toISOString() };
        const sr = await fetch(`${SUPABASE_URL}/rest/v1/reel_stories`, { method: \'POST\', headers: h, body: JSON.stringify(storyRow) });
        const sd = await sr.json();
        if (!sr.ok) { failed += 1; errors.push(sd); continue; }
        const story = Array.isArray(sd) ? sd[0] : sd;
        have.add(title.toLowerCase());
        const er = await fetch(`${SUPABASE_URL}/rest/v1/reel_episodes`, { method: \'POST\', headers: h, body: JSON.stringify({ story_id: story.id, episode: 1, title: title, video_url: url, is_active: true }) });
        if (!er.ok) failed += 1; else created += 1;
      }
      res.status(200).json({ ok: true, created, skipped, failed, errors: errors.slice(0, 3) });
      return;
    }

'''
js = Path(\'api/admin-reels.js\')
t = js.read_text()
if "action === \'import_csv\'" not in t:
    needle = "    res.status(400).json({ error: \'action invalida\' });"
    if needle not in t:
        raise SystemExit(\'needle js ausente\')
    js.write_text(t.replace(needle, JS_BLOCK + needle, 1))
    print(\'js import_csv inserido\')
else:
    print(\'js import_csv ja existe\')
