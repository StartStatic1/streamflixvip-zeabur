from pathlib import Path
p = Path('Public/admin-reels.html')
t = p.read_text()
if 'id="q"' in t and 'csvSkip' in t:
    print('painel ja tem busca')
else:
    t = t.replace(
        '<h2>Lista</h2>\n  <div id="list"></div>',
        '<h2>Lista</h2>\n  <input id="q" placeholder="Buscar na lista" oninput="renderList()"/>\n  <p id="listMeta" class="muted"></p>\n  <div id="list"></div>',
        1,
    )
    t = t.replace(
        '<input id="csvMax" value="80" style="width:90px" title="max linhas desta vez"/>',
        '<input id="csvSkip" value="0" style="width:90px" title="pular primeiras linhas"/>\n    <input id="csvMax" value="50" style="width:90px" title="quantas desta vez"/>',
        1,
    )
    old = '''  const wrap = document.getElementById('list');
  const rows = data.stories || [];
  wrap.innerHTML = rows.map(function (s) {'''
    new = '''  window._stories = data.stories || [];
  renderList();
}
function renderList() {
  const wrap = document.getElementById('list');
  if (!wrap) return;
  const q = (document.getElementById('q') && document.getElementById('q').value || '').toLowerCase();
  const rows = (window._stories || []).filter(function (s) {
    return !q || String(s.title || '').toLowerCase().indexOf(q) >= 0;
  });
  const meta = document.getElementById('listMeta');
  if (meta) meta.textContent = rows.length + ' de ' + (window._stories || []).length;
  wrap.innerHTML = rows.slice(0, 40).map(function (s) {'''
    if old in t:
        t = t.replace(old, new, 1)
    t = t.replace(
        'let rows = lines.slice(1);\n  const max = Math.min(Math.max(Number(document.getElementById(\'csvMax\').value || 80), 10), 200);\n  if (rows.length > max) rows = rows.slice(0, max);',
        'const skip = Math.max(Number(document.getElementById(\'csvSkip\').value || 0), 0);\n  const max = Math.min(Math.max(Number(document.getElementById(\'csvMax\').value || 50), 10), 80);\n  let rows = lines.slice(1).slice(skip, skip + max);',
        1,
    )
    p.write_text(t)
    print('painel patched')
