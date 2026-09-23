#!/usr/bin/env python3
from pathlib import Path
p = Path(__file__).resolve().parents[1] / 'lib' / 'stremio-addons.js'
t = p.read_text()
old = '''function looksNumberedServer(name) {
  const n = String(name || '');
  return /server\\s*\\d+/i.test(n) || /servidor\\s*\\d+/i.test(n) || /flixhub/i.test(n);
}'''
new = '''function looksNumberedServer(name) {
  const n = String(name || '');
  return /server\\s*\\d+/i.test(n) || /servidor\\s*\\d+/i.test(n);
}
function pickStreamDisplayName(stream) {
  const title = stripNoise(stream && stream.title);
  const name = stripNoise(stream && stream.name);
  if (looksNumberedServer(title)) return title;
  if (looksNumberedServer(name)) return name;
  const blob = stripNoise([name, title, stream && stream.description].filter(Boolean).join(' '));
  const m = blob.match(/((?:flixhub\\s*)?(?:server|servidor)\\s*\\d+)/i);
  if (m) return m[1].replace(/\\s+/g, ' ');
  return name || title;
}'''
if old not in t:
    old = old.replace('\\\\s*', '\\s*').replace('\\\\d+', '\\d+')
# file on disk uses single backslashes
old = '''function looksNumberedServer(name) {
  const n = String(name || '');
  return /server\s*\d+/i.test(n) || /servidor\s*\d+/i.test(n) || /flixhub/i.test(n);
}'''
new = '''function looksNumberedServer(name) {
  const n = String(name || '');
  return /server\s*\d+/i.test(n) || /servidor\s*\d+/i.test(n);
}
function pickStreamDisplayName(stream) {
  const title = stripNoise(stream && stream.title);
  const name = stripNoise(stream && stream.name);
  if (looksNumberedServer(title)) return title;
  if (looksNumberedServer(name)) return name;
  const blob = stripNoise([name, title, stream && stream.description].filter(Boolean).join(' '));
  const m = blob.match(/((?:flixhub\s*)?(?:server|servidor)\s*\d+)/i);
  if (m) return m[1].replace(/\s+/g, ' ');
  return name || title;
}'''
if 'function pickStreamDisplayName' in t:
    print('ok pickStreamDisplayName ja tem')
elif old in t:
    t = t.replace(old, new, 1)
    print('ok looksNumbered + pickStreamDisplayName')
else:
    print('aviso looksNumbered nao achado')

old2 = '  const streamName = stripNoise(stream.name || stream.title || \'\');
'
new2 = '  const streamName = pickStreamDisplayName(stream);
'
if old2 in t:
    t = t.replace(old2, new2, 1)
    print('ok usa pickStreamDisplayName')
elif 'pickStreamDisplayName(stream)' in t:
    print('ok ja usa pick')
else:
    print('aviso streamName nao achado')
p.write_text(t)
print('fim apply_flixhub_server_title')
