from pathlib import Path
js = Path('api/admin-reels.js')
t = js.read_text()
if "action === 'import_csv'" not in t:
    needle = "    res.status(400).json({ error: 'action invalida' });"
    block = Path('scripts/patch_reels_csv_import.py').read_text()  # fallback marker
    raise SystemExit('rode antes patch_reels_csv_import.py — import_csv ausente')
print('js ja tem import_csv')

html = Path('Public/admin-reels.html')
h = html.read_text()
if 'csvFile' in h and 'importFile' in h:
    print('html ja tem picker')
else:
    old = '  <h2>Nova historia</h2>'
    new = '''  <h2>Importar CSV</h2>
  <p class="muted">Escolhe o arquivo no celular. Sobe sozinho em lotes de 50. Titulo repetido e pulado. Deixa a aba aberta.</p>
  <input id="csvFile" type="file" accept=".csv,text/csv,text/plain"/>
  <div class="row">
    <input id="csvLimit" value="50" style="width:90px" title="por lote"/>
    <button class="btn-gold" onclick="document.getElementById('csvFile').click()">Escolher CSV</button>
  </div>
  <p class="muted" style="margin-top:10px">Ou cola um pedaco:</p>
  <textarea id="csvBox" placeholder="nome,idioma,link_imagem,link_mp4"></textarea>
  <button class="btn-sec" onclick="importCsv()">Importar texto colado</button>
  <h2>Nova historia</h2>'''
    if old not in h:
        raise SystemExit('h2 nova historia ausente')
    h = h.replace(old, new, 1)
    if 'async function importCsv()' not in h:
        h = h.replace(
            'async function saveStory() {',
            '''function splitCsvLines(text) {
  return String(text || '').replace(/^\uFEFF/, '').split(/\r?\n/).filter(function (l) { return l.trim(); });
}
async function sendCsvChunk(chunk, limit) {
  return api('import_csv', { csv: chunk, limit: limit });
}
async function importCsvText(text, limit) {
  const lines = splitCsvLines(text);
  if (lines.length < 2) { msg('CSV vazio.'); return; }
  const header = lines[0];
  const rows = lines.slice(1);
  let created = 0, skipped = 0, failed = 0;
  const batch = Math.min(Math.max(Number(limit || 50), 10), 80);
  for (let i = 0; i < rows.length; i += batch) {
    const part = rows.slice(i, i + batch);
    msg('Lote ' + (i + 1) + '-' + (i + part.length) + ' de ' + rows.length + '...');
    const data = await sendCsvChunk([header].concat(part).join('\n'), batch);
    if (data.error) { msg('Parou: ' + JSON.stringify(data.error)); return; }
    created += Number(data.created || 0);
    skipped += Number(data.skipped || 0);
    failed += Number(data.failed || 0);
  }
  msg('Pronto. Criadas ' + created + ' · puladas ' + skipped + ' · falhas ' + failed, true);
  await load();
}
async function importCsv() {
  await importCsvText(document.getElementById('csvBox').value, document.getElementById('csvLimit').value);
}
async function importFile(ev) {
  const f = ev.target.files && ev.target.files[0];
  if (!f) return;
  msg('Lendo ' + f.name + '...');
  const text = await f.text();
  await importCsvText(text, document.getElementById('csvLimit').value);
  ev.target.value = '';
}
async function saveStory() {'''
        )
    if 'csvFile' in h and 'onchange' not in h[h.find('csvFile'):h.find('csvFile')+80]:
        h = h.replace(
            '<input id="csvFile" type="file" accept=".csv,text/csv,text/plain"/>',
            '<input id="csvFile" type="file" accept=".csv,text/csv,text/plain" onchange="importFile(event)"/>',
            1,
        )
    html.write_text(h)
    print('html picker ok')
