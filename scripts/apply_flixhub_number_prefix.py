#!/usr/bin/env python3
from pathlib import Path
p = Path(__file__).resolve().parents[1] / 'lib/stremio-addons.js'
t = p.read_text()
old = '''function uniquifyLabels(sources) {
  const seen = {};
  return sources.map((s) => {
    const base = s.source_label || 'Servidor';
    seen[base] = (seen[base] || 0) + 1;
    if (seen[base] === 1) return s;
    return Object.assign({}, s, { source_label: base + ' ' + seen[base] });
  });
}'''
new = '''function uniquifyLabels(sources) {
  const seen = {};
  return sources.map((s) => {
    const base = s.source_label || 'Servidor';
    seen[base] = (seen[base] || 0) + 1;
    if (seen[base] === 1) return s;
    const parts = String(base).split(' \u00b7 ');
    parts[0] = String(parts[0] || 'Servidor').trim() + ' ' + seen[base];
    return Object.assign({}, s, { source_label: parts.join(' \u00b7 ') });
  });
}'''
if 'parts[0] =' in t and 'seen[base]' in t and 'split' in t[t.find('function uniquifyLabels'):t.find('function uniquifyLabels')+500]:
    print('ok ja prefixa numero')
elif old in t:
    p.write_text(t.replace(old, new, 1))
    print('ok prefixo FlixHub 2')
else:
    print('uniquify nao no formato esperado, tentando bloco curto')
    idx = t.find('function uniquifyLabels')
    print(repr(t[idx:idx+420]) if idx>=0 else 'sem uniquify')
