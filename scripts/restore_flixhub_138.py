#!/usr/bin/env python3
# Restaura api/flixhub.js v1.3.8 (formato limpo Nuvio)
import urllib.request
from pathlib import Path

URL = 'https://raw.githubusercontent.com/StartStatic1/streamflixvip-zeabur/b3aa1bfdba2a65de4a0d6a92d065d85dcb162bf8/api/flixhub.js'
root = Path(__file__).resolve().parents[1]
out = root / 'api' / 'flixhub.js'
print('baixando base...')
js = urllib.request.urlopen(URL, timeout=30).read().decode()
js = js.replace("name: brand + ' · ' + label,", 'name: brand,')
old_m = """                title:
                  '🎬 ' +
                  (hit.name || titles[0] || 'Filme') +
                  '\\n' +
                  qLabel,"""
new_m = """                title:
                  '🎬 ' +
                  (hit.name || titles[0] || 'Filme') +
                  '\\n' +
                  (server.color || '⚡') +
                  ' ' +
                  label +
                  '\\n' +
                  qLabel,"""
if old_m in js:
    js = js.replace(old_m, new_m)
    print('movie title ok')
else:
    print('movie title pattern skip')
old_s = """                  title:
                    '📺 ' +
                    (hit.name || titles[0] || 'Serie') +
                    ' S' +
                    seasonKey +
                    'E' +
                    wantEp +
                    '\\n🎯 FULL HD',"""
new_s = """                  title:
                    '📺 ' +
                    (hit.name || titles[0] || 'Serie') +
                    ' S' +
                    seasonKey +
                    'E' +
                    wantEp +
                    '\\n' +
                    (server.color || '⚡') +
                    ' ' +
                    label +
                    '\\n🎯 FULL HD',"""
if old_s in js:
    js = js.replace(old_s, new_s)
    print('series title ok')
else:
    print('series title pattern skip')
js = js.replace("version: '1.3.7'", "version: '1.3.8'")
out.write_text(js)
print('escrito', out, len(js))
