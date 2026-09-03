from pathlib import Path
p = Path('Public/admin-bridge.html')
t = p.read_text()
old = "const url = (d.publicBase || '') + '/api/bridge/' + b.id + '/manifest.json';"
new = "const token = b.access_token || '';\n    const url = (d.publicBase || '') + '/api/bridge/' + b.id + '/' + token + '/manifest.json';"
if old in t:
    t = t.replace(old, new, 1)
    print('url token ok')
else:
    print('url ja estava ou nao achou')

if 'function rotateTok' not in t:
    t = t.replace(
        'checkSession();',
        '''async function rotateTok(id) {
  if (!confirm('Gera token novo e corta o link antigo?')) return;
  const d = await api('rotate-token', { id: id });
  toast(d.error ? (d.error.message || d.error) : 'Token novo');
  loadList();
}
checkSession();'''
    )
    print('rotateTok ok')

btn_old = "onclick=\"navigator.clipboard.writeText('"
# add button after Copiar JSON if missing
if 'rotateTok(' not in t:
    t = t.replace(
        ">Copiar JSON</button>'",
        ">Copiar JSON</button>'\n      + '<button class=\"btn-sec\" style=\"padding:6px 10px;font-size:.75rem\" onclick=\"rotateTok(\\\'' + b.id + '\\\')\">Novo token</button>'",
        1,
    )
    print('botao novo token ok')
else:
    print('botao ja existe')

p.write_text(t)
print('html bytes', p.stat().st_size)
