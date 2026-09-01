from pathlib import Path

js = Path('api/admin-reels.js')
t = js.read_text()
needle = "    res.status(400).json({ error: 'action invalida' });"
block = r'''    if (action === 'import_csv') {
      const raw = String(body.csv || body.text || '');
      if (!raw.trim()) {
        res.status(400).json({ error: 'Cole o CSV' });
        return;
      }
      const limit = Math.min(Number(body.limit || 200), 400);
      function parseCsv(text) {
        const lines = text.replace(/^\uFEFF/, '').split(/\r?\n/);
        const rows = [];
        for (const line of lines) {
          if (!line.trim()) continue;
          const cols = [];
          let cur = '';
          let q = false;
          for (let i = 0; i < line.length; i++) {
            const ch = line[i];
            if (ch === '"') {
              if (q && line[i + 1] === '"') { cur += '"'; i++; }
              else q = !q;
            } else if (ch === ',' && !q) {
              cols.push(cur.trim());
              cur = '';
            } else cur += ch;
          }
          cols.push(cur.trim());
          rows.push(cols);
        }
        return rows;
      }
      function cleanTitle(s) {
        return String(s || '')
          .replace(/\[LEG\]/gi, '')
          .replace(/\(Dublado\)/gi, '')
          .replace(/\s+/g, ' ')
          .trim();
      }
      const parsed = parseCsv(raw);
      if (!parsed.length) {
        res.status(400).json({ error: 'CSV vazio' });
        return;
      }
      let start = 0;
      const head = parsed[0].map((x) => x.toLowerCase());
      if (head.includes('nome') || head.includes('titulo') || head.includes('link_mp4')) start = 1;
      const existingR = await fetch(
        `${SUPABASE_URL}/rest/v1/reel_stories?select=title`,
        { headers: h },
      );
      const existingRows = await existingR.json();
      const seen = new Set(
        (Array.isArray(existingRows) ? existingRows : []).map((s) => String(s.title || '').toLowerCase()),
      );
      let created = 0;
      let skipped = 0;
      let failed = 0;
      const errors = [];
      for (let i = start; i < parsed.length; i++) {
        if (created >= limit) break;
        const cols = parsed[i];
        const title = cleanTitle(cols[0]);
        const idioma = String(cols[1] || '').toUpperCase();
        const poster = (cols[2] || '').replace(/^"|"$/g, '').trim();
        const url = (cols[3] || '').trim();
        if (!title || !/^https?:\/\//i.test(url)) { skipped += 1; continue; }
        if (seen.has(title.toLowerCase())) { skipped += 1; continue; }
        const storyRow = {
          title,
          subtitle: idioma || null,
          poster_url: poster || null,
          genre: 'Novelinha',
          language: idioma.indexOf('DUB') >= 0 ? 'pt-BR' : 'pt-BR',
          is_active: true,
          vip_only: true,
          use_addons: false,
          sort_order: created,
          updated_at: new Date().toISOString(),
        };
        const sr = await fetch(`${SUPABASE_URL}/rest/v1/reel_stories`, {
          method: 'POST', headers: h, body: JSON.stringify(storyRow),
        });
        const sd = await sr.json();
        if (!sr.ok || !sd || !sd[0] || !sd[0].id) {
          failed += 1;
          if (errors.length < 8) errors.push({ title, error: sd });
          continue;
        }
        const storyId = sd[0].id;
        seen.add(title.toLowerCase());
        const er = await fetch(`${SUPABASE_URL}/rest/v1/reel_episodes`, {
          method: 'POST',
          headers: h,
          body: JSON.stringify({
            story_id: storyId,
            episode: 1,
            title: 'Completo',
            video_url: url,
            is_active: true,
          }),
        });
        if (!er.ok) {
          failed += 1;
          const ed = await er.json();
          if (errors.length < 8) errors.push({ title, error: ed });
          continue;
        }
        created += 1;
      }
      res.status(200).json({ ok: true, created, skipped, failed, errors });
      return;
    }

    res.status(400).json({ error: 'action invalida' });'''
if needle not in t:
    raise SystemExit('js needle missing')
js.write_text(t.replace(needle, block, 1))
print('js ok')

html = Path('Public/admin-reels.html')
h = html.read_text()
old = '''  <h2>Nova historia</h2>'''
new = '''  <h2>Importar CSV (nome,idioma,capa,mp4)</h2>
  <p class="muted">Cola o arquivo inteiro ou so um pedaco. Cada linha vira uma historia + EP 1. Titulo repetido e pulado.</p>
  <textarea id="csvBox" placeholder="nome,idioma,link_imagem,link_mp4"></textarea>
  <div class="row">
    <input id="csvLimit" value="50" style="width:90px" title="max neste envio"/>
    <button class="btn-gold" onclick="importCsv()">Importar CSV</button>
  </div>
  <h2>Nova historia</h2>'''
if old not in h:
    raise SystemExit('html h2 missing')
h = h.replace(old, new, 1)
fn = '''async function saveStory() {'''
fn2 = '''async function importCsv() {
  const csv = document.getElementById('csvBox').value;
  if (!csv.trim()) { msg('Cole o CSV.'); return; }
  msg('Importando...');
  const data = await api('import_csv', {
    csv: csv,
    limit: Number(document.getElementById('csvLimit').value || 50),
  });
  if (data.error) { msg(JSON.stringify(data.error)); return; }
  msg('Criadas ' + data.created + ' · puladas ' + data.skipped + ' · falhas ' + data.failed, true);
  document.getElementById('csvBox').value = '';
  await load();
}
async function saveStory() {'''
if fn not in h:
    raise SystemExit('saveStory missing')
h = h.replace(fn, fn2, 1)
html.write_text(h)
print('html ok')
