#!/usr/bin/env python3
from pathlib import Path

p = Path(__file__).resolve().parents[1] / "lib" / "stremio-addons.js"
t = p.read_text()

old = (
    "function looksNumberedServer(name) {\n"
    "  const n = String(name || '');\n"
    "  return /server\\s*\\d+/i.test(n) || /servidor\\s*\\d+/i.test(n) || /flixhub/i.test(n);\n"
    "}\n"
)
new = (
    "function looksNumberedServer(name) {\n"
    "  const n = String(name || '');\n"
    "  return /server\\s*\\d+/i.test(n) || /servidor\\s*\\d+/i.test(n);\n"
    "}\n"
    "function pickStreamDisplayName(stream) {\n"
    "  const title = stripNoise(stream && stream.title);\n"
    "  const name = stripNoise(stream && stream.name);\n"
    "  if (looksNumberedServer(title)) return title;\n"
    "  if (looksNumberedServer(name)) return name;\n"
    "  const blob = stripNoise([name, title, stream && stream.description].filter(Boolean).join(' '));\n"
    "  const m = blob.match(/((?:flixhub\\s*)?(?:server|servidor)\\s*\\d+)/i);\n"
    "  if (m) return m[1].replace(/\\s+/g, ' ');\n"
    "  return name || title;\n"
    "}\n"
)

if "function pickStreamDisplayName" in t:
    print("ok pickStreamDisplayName ja tem")
elif old in t:
    t = t.replace(old, new, 1)
    print("ok looksNumbered + pickStreamDisplayName")
else:
    print("aviso looksNumbered nao achado")

old2 = "  const streamName = stripNoise(stream.name || stream.title || '');\n"
new2 = "  const streamName = pickStreamDisplayName(stream);\n"
if old2 in t:
    t = t.replace(old2, new2, 1)
    print("ok usa pickStreamDisplayName")
elif "pickStreamDisplayName(stream)" in t:
    print("ok ja usa pick")
else:
    print("aviso streamName nao achado")

p.write_text(t)
print("fim apply_flixhub_server_title")
