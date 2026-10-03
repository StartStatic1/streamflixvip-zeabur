#!/usr/bin/env python3
"""Aplica merge limpo + limit bridges 20 em api/live-tv.js"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
p = ROOT / "api" / "live-tv.js"
t = p.read_text(encoding="utf-8")

start = t.find("function channelMergeKey(name)")
end = t.find("function displayBaseName", start)
if start < 0 or end < 0:
    print("channelMergeKey block not found")
    raise SystemExit(1)

new_fn = r'''function channelMergeKey(name) {
  let n = normalizeName(name);
  if (!n) return '';
  n = n.replace(/^\d+\s+/, '');
  // pais no inicio (br, pt...)
  n = n.replace(/^(br|pt|us|uk|ar|mx|lat|latam|es|fr|de|it|cl|pe|co|uy|py)\s+/, '');
  // qualidade
  n = n.replace(/\b(sd|hd|fhd|uhd|4k|8k|h264|h265|hevc|hdr|lq|hq|full\s*hd|vip|premium|raw|aac|ac3)\b/g, ' ');
  // ruido
  n = n.replace(/\b(canais?|leg|legenda|legendado|legendada|legend|sub|subs|subtitle|legendas|dublado|dublada|multi|audio|server|opcao|opt|option|backup|alt|mirror)\b/g, ' ');
  // TV Globo / REDE Globo / Canal -> mesma base
  n = n.replace(/\b(tv|canal|channel|rede|network)\b/g, ' ');
  n = n.replace(/\b(ao\s*vivo|online|live|stream|iptv)\b/g, ' ');
  // "integracao" / "int" entre globo e cidade
  n = n.replace(/\b(integracao|integra|int)\b/g, ' ');
  n = n.replace(/\s+/g, ' ').trim();
  // cidade permanece: "globo uberlandia" vs "globo araxa"
  return n;
}

'''

t = t[:start] + new_fn + t[end:]

# bridges limit 8 -> 20
old_lim = "order=created_at.desc&limit=8"
if old_lim in t and "use_live=eq.true" in t:
    t = t.replace(
        "is_active=eq.true&use_live=eq.true&select=id,name,xtream_host,xtream_user,xtream_pass,live_cats&order=created_at.desc&limit=8",
        "is_active=eq.true&use_live=eq.true&select=id,name,xtream_host,xtream_user,xtream_pass,live_cats&order=created_at.desc&limit=20",
        1,
    )
    print("ok bridges limit 20")
elif "limit=20" in t:
    print("bridges limit already 20")
else:
    print("warn: bridges limit line not exact match")

p.write_text(t, encoding="utf-8")
print("ok channelMergeKey (rede/tv/integracao stripped, city kept)")
print("done")
