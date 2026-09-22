#!/usr/bin/env python3
from pathlib import Path
p = Path(__file__).resolve().parents[1] / 'api/subtitles.js'
t = p.read_text()
a = t.count("'Online · '") + t.count('Online ·')
if a >= 2 and 'Addon ·' in t or t.count("source: addon.name") and 'Online ·' in t:
    pass
# official release prefix
old = "release: item.attributes?.release || item.attributes?.feature_details?.title || 'Legenda PT-BR',"
new = "release: 'Online · ' + (item.attributes?.release || item.attributes?.feature_details?.title || 'Legenda PT-BR'),"
if old in t and "'Online · ' + (item.attributes" not in t:
    t = t.replace(old, new, 1)
    print('ok prefix official')
else:
    print('official ja ok ou nao achou')

old = "release: s.subtitleFileName || s.movieReleaseName || `${addon.name || 'Addon'} · Legenda PT-BR`,"
new = "release: (addon.name || 'Addon') + ' · ' + (s.subtitleFileName || s.movieReleaseName || 'Legenda PT-BR'),"
if old in t:
    t = t.replace(old, new, 1)
    print('ok prefix addon')
else:
    print('addon release ja ok ou nao achou')

old = "release: s.subtitleFileName || s.movieReleaseName || 'Legenda PT-BR (online)',"
new = "release: 'Online · ' + (s.subtitleFileName || s.movieReleaseName || 'Legenda PT-BR'),"
if old in t:
    t = t.replace(old, new, 1)
    print('ok prefix stremio os')
else:
    print('stremio os ja ok ou nao achou')
p.write_text(t)
print('fim apply_sub_source_prefix')
