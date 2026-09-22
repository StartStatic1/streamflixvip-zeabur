#!/usr/bin/env python3
from pathlib import Path

p = Path(__file__).resolve().parents[1] / 'lib/stremio-addons.js'
t = p.read_text()

old = '''function looksNumberedServer(name) {
  const n = stripNoise(name);
  return n.length >= 2;
}'''

new = '''function looksNumberedServer(name) {
  const n = stripNoise(name);
  if (n.length < 2) return false;
  if (/server\\s*\\d+/i.test(n) || /servidor\\s*\\d+/i.test(n)) return true;
  if (/\\[([^\\]]*tb|tb\\+)/i.test(n)) return false;
  if (/estrela|\\blua\\b|\\bsol\\b/i.test(n)) return true;
  if (/[a-z\\u00c0-\\u00ff]{3,}\\s*\\d+/i.test(n) && !/(1080p|720p|480p|2160p|4k)/i.test(n.split(' ')[0] || '')) return true;
  return false;
}

function uniquifyLabels(sources) {
  const seen = {};
  return sources.map((s) => {
    const base = s.source_label || 'Servidor';
    seen[base] = (seen[base] || 0) + 1;
    if (seen[base] === 1) return s;
    return Object.assign({}, s, { source_label: base + ' ' + seen[base] });
  });
}'''

if 'function uniquifyLabels' in t and 'estrela' in t:
    print('ok ja tem uniquify')
else:
    if old in t:
        t = t.replace(old, new, 1)
        print('ok looksNumbered')
    else:
        old2 = '''function looksNumberedServer(name) {
  const n = String(name || '');
  return /server\\s*\\d+/i.test(n) || /servidor\\s*\\d+/i.test(n) || /flixhub/i.test(n);
}'''
        if old2 in t:
            t = t.replace(old2, new, 1)
            print('ok looksNumbered v2')
        else:
            print('aviso: looksNumbered nao achado')

old_ret = '''  const numbered = out.some((s) => looksNumberedServer(s.source_label));
  if (!numbered) out.sort((a, b) => (b._score || 0) - (a._score || 0));
  return out.slice(0, MAX_PER_ADDON).map(({ source_url, source_label, priority }) => ({ source_url, source_label, priority }));'''

new_ret = '''  const numbered = out.some((s) => looksNumberedServer(s.source_label));
  if (!numbered) out.sort((a, b) => (b._score || 0) - (a._score || 0));
  const uniq = uniquifyLabels(out).slice(0, MAX_PER_ADDON);
  return uniq.map(({ source_url, source_label, priority }) => ({ source_url, source_label, priority }));'''

if old_ret in t:
    t = t.replace(old_ret, new_ret, 1)
    print('ok uniquify no fetch')
elif 'uniquifyLabels(out)' in t:
    print('ok fetch ja uniquify')
else:
    print('aviso: fetch ret nao achado')

p.write_text(t)
print('fim apply_flixhub_unique_labels')
