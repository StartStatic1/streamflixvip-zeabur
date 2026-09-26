#!/usr/bin/env python3
"""Meta nos addons: qualidade, audio certo, origem (Nyan etc), tamanho.
Nao mexe no FlixHub/Nuvio. So na leitura do StreamFlix."""
from pathlib import Path

p = Path(__file__).resolve().parents[1] / "lib" / "stremio-addons.js"
t = p.read_text()

if "temporary loader" in t or len(t) < 3000:
    cache = p.parent / "stremio-addons.cache.js"
    if cache.exists() and cache.stat().st_size > 3000:
        t = cache.read_text()
        print("ok restaurou do cache")
    else:
        raise SystemExit("lib/stremio-addons.js esta stub — nao vou sobrescrever")

old_audio = """function detectAudio(text) {
  const t = String(text || '').toLowerCase();
  if (/dublad|\\bdub\\b|pt-?br|dual\\s*audio/.test(t)) return 'Dublado';
  if (/legendad|\\bleg\\b|subtitled|subtitle|\\blegend\\b/.test(t)) return 'Legendado';
  return null;
}"""

new_audio = """function detectAudio(text, addonName) {
  const t = String(text || '').toLowerCase();
  const name = String(addonName || '').toLowerCase();
  const dub = /dublad[oa]|dual\\s*[aá]udio|pt-?br\\s*dub|\\bdub\\s*pt|\\bpt\\s*dub/.test(t);
  const leg = /legendad[oa]|soft\\s*subs?|hard\\s*subs?|pt-?br\\s*subs?|\\bsubs?\\b|\\bleg\\b/.test(t);
  if (dub && !leg) return 'Dublado';
  if (leg && !dub) return 'Legendado';
  if (dub && leg) return 'Dublado';
  if (/animesub|anime[\\s._-]*sub|subs?\\s*br/.test(name) && !dub) return 'Legendado';
  return null;
}

function detectSize(text, stream) {
  const blob = String(text || '');
  const m = blob.match(/(\\d+(?:[.,]\\d+)?)\\s*(GiB|GB|MiB|MB)\\b/i);
  if (m) return m[1].replace(',', '.') + ' ' + m[2].toUpperCase().replace('GIB', 'GB').replace('MIB', 'MB');
  const bytes = stream && stream.behaviorHints && stream.behaviorHints.videoSize;
  const n = Number(bytes);
  if (Number.isFinite(n) && n > 0) {
    if (n >= 1073741824) return (n / 1073741824).toFixed(1) + ' GB';
    if (n >= 1048576) return Math.round(n / 1048576) + ' MB';
  }
  return null;
}

function detectOrigin(stream, addonName) {
  const host = shortAddonName(addonName);
  const hostLow = host.toLowerCase();
  const hints = (stream && stream.behaviorHints) || {};
  const rawBits = [
    stream && stream.name,
    stream && stream.title,
    hints.bingeGroup,
    stream && stream.bingeGroup,
    stream && stream.description,
  ].map(stripNoise).filter(Boolean);

  function tidy(s) {
    let x = stripNoise(s);
    x = x.split(/[\\n\\r]/)[0];
    x = x.replace(/[\\u00b7\\u2022|]+/g, ' ');
    x = x.replace(/\\b(4k|uhd|2160p|1080p|720p|480p|360p|fhd|hd|sd|web-?dl|blu-?ray|bluray|dublado|dublada|legendado|legendada|dual|audio|pt-?br|subs?|dub|hevc|x264|x265|h\\.?264|h\\.?265)\\b/ig, ' ');
    x = x.replace(/\\b(server|servidor)\\s*\\d+\\b/ig, ' ');
    x = x.replace(/\\bflixhub\\b/ig, ' ');
    x = x.replace(/\\s+/g, ' ').trim();
    return x;
  }

  for (const bit of rawBits) {
    const first = bit.split(/[\\n\\u00b7\\u2022|,]/)[0];
    const c = tidy(first);
    if (!c) continue;
    if (c.toLowerCase() === hostLow) continue;
    if (c.length < 2 || c.length > 18) continue;
    if (/^\\d+$/.test(c)) continue;
    if (/stream|addon|http|https/.test(c.toLowerCase())) continue;
    return c.slice(0, 16);
  }
  return null;
}"""

if "function detectOrigin" in t:
    print("ok detectOrigin ja tem")
elif old_audio in t:
    t = t.replace(old_audio, new_audio, 1)
    print("ok detectAudio + origin + size")
else:
    print("aviso detectAudio nao achado")

t = t.replace(
    "  const a = detectAudio(raw);\n",
    "  const a = detectAudio(raw, addonName);\n  const origin = detectOrigin(stream, addonName);\n  const size = detectSize(raw, stream);\n",
    1,
)

old_ret = """  return { source: {
    source_url: url,
    source_label: label,
    priority: Number.isFinite(Number(addonPriority)) ? Number(addonPriority) : ADDON_PRIORITY,
    _score: streamScore(q, a),
  }, reason: null };"""

new_ret = """  return { source: {
    source_url: url,
    source_label: label,
    priority: Number.isFinite(Number(addonPriority)) ? Number(addonPriority) : ADDON_PRIORITY,
    _score: streamScore(q, a),
    meta: { quality: q || null, audio: a || null, origin: origin || null, size: size || null },
  }, reason: null };"""

if "meta: { quality:" in t:
    print("ok meta ja no return")
elif old_ret in t:
    t = t.replace(old_ret, new_ret, 1)
    print("ok return com meta")
else:
    print("aviso return nao achado")

old_map = "return out.slice(0, MAX_PER_ADDON).map(({ source_url, source_label, priority }) => ({ source_url, source_label, priority }));"
new_map = "return out.slice(0, MAX_PER_ADDON).map(({ source_url, source_label, priority, meta }) => ({ source_url, source_label, priority, meta }));"
if old_map in t:
    t = t.replace(old_map, new_map, 1)
    print("ok map preserva meta")
elif "priority, meta" in t:
    print("ok map ja preserva meta")
else:
    print("aviso map MAX_PER_ADDON nao achado")

p.write_text(t)
print("fim apply_source_meta", p.stat().st_size)
