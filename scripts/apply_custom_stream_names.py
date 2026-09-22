#!/usr/bin/env python3
from pathlib import Path

p = Path(__file__).resolve().parents[1] / 'lib/stremio-addons.js'
t = p.read_text()
old = """function looksNumberedServer(name) {
  const n = String(name || '');
  return /server\\s*\\d+/i.test(n) || /servidor\\s*\\d+/i.test(n) || /flixhub/i.test(n);
}"""
new = """function looksNumberedServer(name) {
  const n = stripNoise(name);
  return n.length >= 2;
}"""
if new in t:
    print('ok ja aceita nome custom')
elif old in t:
    p.write_text(t.replace(old, new, 1))
    print('ok patch nome custom')
else:
    print('trecho nao achado')
