#!/usr/bin/env python3
"""channelMergeKey mais limpo: cidade local separada; Globo/SBT genericos unem em 1 linha + multi fontes."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
p = ROOT / "api" / "live-tv.js"
t = p.read_text(encoding="utf-8")

start = t.find("function channelMergeKey(name)")
end = t.find("function displayBaseName", start)
if start < 0 or end < 0:
    print("block not found")
    raise SystemExit(1)

new_fn = r'''function channelMergeKey(name) {
  let n = normalizeName(name);
  if (!n) return '';
  n = n.replace(/^\d+\s+/, '');
  n = n.replace(/^(br|pt|us|uk|ar|mx|lat|latam|es|fr|de|it|cl|pe|co|uy|py)\s+/, '');
  // qualidade / codec
  n = n.replace(/\b(sd|hd|fhd|uhd|4k|8k|h264|h265|hevc|hdr|lq|hq|full\s*hd|vip|premium|raw|aac|ac3)\b/g, ' ');
  // ruido lista
  n = n.replace(/\b(canais?|leg|legenda|legendado|legendada|legend|sub|subs|subtitle|legendas|dublado|dublada|multi|audio|server|opcao|opt|option|backup|alt|mirror)\b/g, ' ');
  // TV Globo / Canal Globo / Rede -> mesma base
  n = n.replace(/\b(tv|canal|channel|rede|network)\b/g, ' ');
  n = n.replace(/\b(ao\s*vivo|online|live|stream|iptv)\b/g, ' ');
  n = n.replace(/\b(integracao|integra)\b/g, ' ');
  n = n.replace(/\s+/g, ' ').trim();
  // cidade/regiao permanece no key (uberlandia, araxa, sp, rj...)
  return n;
}

'''

t = t[:start] + new_fn + t[end:]
p.write_text(t, encoding="utf-8")
print("ok channelMergeKey cleaned")
print("done")
