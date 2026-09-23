#!/usr/bin/env python3
"""StreamFlix: card = FlixHub Server 1/2/3 extraído do title. Nuvio não muda."""
from pathlib import Path
import re

p = Path(__file__).resolve().parents[1] / "lib" / "stremio-addons.js"
t = p.read_text()

# remove pickStreamDisplayName antigo (que devolvia o title inteiro = nome do filme)
t = re.sub(
    r"function pickStreamDisplayName\(stream\) \{.*?\n\}\n",
    "",
    t,
    count=1,
    flags=re.S,
)

NEW_FN = r'''function looksNumberedServer(name) {
  const n = String(name || '');
  return /server\s*\d+/i.test(n) || /servidor\s*\d+/i.test(n);
}
function pickStreamDisplayName(stream) {
  const title = stripNoise(stream && stream.title);
  const name = stripNoise(stream && stream.name);
  const blob = stripNoise([name, title, stream && stream.description].filter(Boolean).join(' '));
  // extrai SÓ "FlixHub Server 1" — nunca o nome do filme
  const m = blob.match(/((?:flixhub\s+)?(?:server|servidor)\s*\d+)/i);
  if (m) {
    let s = m[1].replace(/\s+/g, ' ').trim();
    if (/^server\s*\d+/i.test(s)) s = 'FlixHub ' + s.replace(/^server/i, 'Server');
    else if (/^servidor\s*\d+/i.test(s)) s = 'FlixHub Server ' + (s.match(/\d+/) || [''])[0];
    else {
      s = s.replace(/flixhub/ig, 'FlixHub').replace(/\bserver\b/ig, 'Server');
    }
    return s;
  }
  if (looksNumberedServer(name)) return name;
  return name || title;
}
'''

old_looks = (
    "function looksNumberedServer(name) {\n"
    "  const n = String(name || '');\n"
    "  return /server\\s*\\d+/i.test(n) || /servidor\\s*\\d+/i.test(n) || /flixhub/i.test(n);\n"
    "}\n"
)
old_looks2 = (
    "function looksNumberedServer(name) {\n"
    "  const n = String(name || '');\n"
    "  return /server\\s*\\d+/i.test(n) || /servidor\\s*\\d+/i.test(n);\n"
    "}\n"
)

if old_looks in t:
    t = t.replace(old_looks, NEW_FN, 1)
    print("ok looksNumbered (com flixhub) + pick novo")
elif old_looks2 in t:
    t = t.replace(old_looks2, NEW_FN, 1)
    print("ok looksNumbered + pick novo")
elif "function looksNumberedServer" in t:
    t2 = re.sub(
        r"function looksNumberedServer\(name\) \{.*?\n\}",
        NEW_FN.rstrip(),
        t,
        count=1,
        flags=re.S,
    )
    if t2 != t:
        t = t2
        print("ok looksNumbered regex + pick")
    else:
        print("aviso looksNumbered nao substituido")
else:
    idx = t.find("const streamName = ")
    if idx < 0:
        idx = t.find("stripNoise(stream.name")
    if idx >= 0:
        t = t[:idx] + NEW_FN + "\n" + t[idx:]
        print("ok inseriu pickStreamDisplayName")
    else:
        print("ERRO ponto de insercao")

for a, b in [
    (
        "  const streamName = stripNoise(stream.name || stream.title || '');\n",
        "  const streamName = pickStreamDisplayName(stream);\n",
    ),
    (
        "  const streamName = stripNoise(stream.title || stream.name || '');\n",
        "  const streamName = pickStreamDisplayName(stream);\n",
    ),
]:
    if a in t:
        t = t.replace(a, b, 1)
        print("ok usa pickStreamDisplayName")
        break
else:
    if "pickStreamDisplayName(stream)" in t:
        print("ok ja usa pick")
    else:
        print("aviso streamName nao ligado")

p.write_text(t)
print("fim", p.stat().st_size)
