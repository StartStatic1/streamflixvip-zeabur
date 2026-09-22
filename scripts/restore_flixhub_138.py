#!/usr/bin/env python3
# Restaura api/flixhub.js v1.3.9
# - formato limpo Nuvio (name=FlixHub, server no title)
# - aceita ID so numerico (tmdb) que o Nuvio manda em alguns filmes
import urllib.request
from pathlib import Path

URL = 'https://raw.githubusercontent.com/StartStatic1/streamflixvip-zeabur/b3aa1bfdba2a65de4a0d6a92d065d85dcb162bf8/api/flixhub.js'
root = Path(__file__).resolve().parents[1]
out = root / 'api' / 'flixhub.js'
print('baixando base...')
js = urllib.request.urlopen(URL, timeout=30).read().decode()

# nomes limpos
js = js.replace("name: brand + ' \u00b7 ' + label,", 'name: brand,')

old_m = """                title:
                  '\U0001f3ac ' +
                  (hit.name || titles[0] || 'Filme') +
                  '\\n' +
                  qLabel,"""
new_m = """                title:
                  '\U0001f3ac ' +
                  (hit.name || titles[0] || 'Filme') +
                  '\\n' +
                  (server.color || '\u26a1') +
                  ' ' +
                  label +
                  '\\n' +
                  qLabel,"""
# Use actual emoji in file
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
    print('movie title skip')

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
    print('series title skip')

# ID numerico TMDB (filme) — antes so aceitava numero com S:E
old_p = """  } else if (/^\\d+$/.test(parts[0]) && parts.length >= 3) {
    tmdbId = parts[0];
    season = Number(parts[1]);
    episode = Number(parts[2]);
  }
  return { type, id: raw, tmdbId, imdbId, season, episode };
}"""
new_p = """  } else if (/^\\d+$/.test(parts[0])) {
    tmdbId = parts[0];
    if (parts.length >= 3) {
      season = Number(parts[1]);
      episode = Number(parts[2]);
    }
  } else if (/^imdb$/i.test(parts[0]) && parts.length >= 2 && /^tt\\d+$/i.test(parts[1])) {
    imdbId = parts[1];
    if (parts.length >= 4) {
      season = Number(parts[2]);
      episode = Number(parts[3]);
    }
  }
  return { type, id: raw, tmdbId, imdbId, season, episode };
}"""
if old_p in js:
    js = js.replace(old_p, new_p)
    print('parse id ok')
else:
    print('parse id skip')
    # show nearby
    i = js.find('parts.length >= 3')
    print(repr(js[i-80:i+200]))

js = js.replace("version: '1.3.7'", "version: '1.3.9'")
js = js.replace("version: '1.3.8'", "version: '1.3.9'")
out.write_text(js)
print('escrito', out, len(js), 'v', js.count("1.3.9"))
