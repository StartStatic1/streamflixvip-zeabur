from pathlib import Path
p = Path('Public/admin-addons.html')
t = p.read_text()
old = '''      <h2>Testar streams</h2>
      <p class="hint">Busca streams dos add-ons ativos para um TMDB (padrão 550 = Clube da Luta).</p>
      <input type="number" id="testTmdb" value="550" placeholder="tmdb_id"/>
      <button class="btn-sec" onclick="testStreams()">Buscar streams</button>'''
new = '''      <h2>Testar streams</h2>
      <p class="hint">Catalogo so lista titulo. Stream e o que aparece no play. Anime usa Kitsu automaticamente.</p>
      <div style="display:flex;gap:8px;flex-wrap:wrap">
        <select id="testType" style="flex:1;min-width:120px">
          <option value="movie">Filme TMDB</option>
          <option value="tv">Serie TMDB</option>
          <option value="anime">Anime (Kitsu)</option>
        </select>
        <input type="number" id="testTmdb" value="550" placeholder="tmdb_id" style="flex:1"/>
      </div>
      <p class="hint">Presets: Filme 550 · Serie 1396 · Anime 95479 (Jujutsu). S T1 E1 no anime/serie.</p>
      <button class="btn-sec" onclick="testStreams()">Buscar streams</button>'''
if old not in t:
    # try without accent
    old2 = old.replace('ão', 'ao')
    if old2 in t:
        t = t.replace(old2, new, 1)
        print('replaced ascii')
    else:
        print('bloco nao encontrado')
        raise SystemExit(1)
else:
    t = t.replace(old, new, 1)
    print('replaced accent')
oldjs = '''  const tmdb = parseInt(document.getElementById('testTmdb').value, 10) || 550;
  const out = document.getElementById('testOut');
  out.style.display = 'block';
  out.textContent = 'Buscando…';
  const d = await api('test-addon', { tmdb_id: tmdb, type: 'movie' });'''
newjs = '''  const type = document.getElementById('testType').value || 'movie';
  const presets = { movie: 550, tv: 1396, anime: 95479 };
  let tmdb = parseInt(document.getElementById('testTmdb').value, 10);
  if (!tmdb) tmdb = presets[type] || 550;
  const out = document.getElementById('testOut');
  out.style.display = 'block';
  out.textContent = 'Buscando…';
  const extra = { tmdb_id: tmdb, type: type };
  if (type !== 'movie') { extra.season = 1; extra.episode = 1; }
  const d = await api('test-addon', extra);'''
if oldjs in t:
    t = t.replace(oldjs, newjs, 1)
    print('js ok')
else:
    print('js skip')
p.write_text(t)
