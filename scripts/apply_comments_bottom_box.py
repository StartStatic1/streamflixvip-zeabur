#!/usr/bin/env python3
"""CommentsModal: Box + Column(lista) + Surface align BottomCenter."""
from pathlib import Path

p = Path(__file__).resolve().parents[1] / "android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt"
t = p.read_text()

s = t.find("private fun CommentsModal(")
e = t.find("private fun com.streamflixvip.app.network.TmdbResponse.bestLogoUrl", s)
modal = t[s:e]

if "align(Alignment.BottomCenter)" in modal and "Box(Modifier.fillMaxSize().imePadding())" in modal:
    print("ok ja aplicado")
    raise SystemExit(0)

# 1) Outer: Column(ime) -> Box(ime) { Column {
old = (
    "        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {\n"
    "            Column(Modifier.fillMaxSize().imePadding()) {\n"
)
new = (
    "        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {\n"
    "            Box(Modifier.fillMaxSize().imePadding()) {\n"
    "            Column(Modifier.fillMaxSize()) {\n"
)
if old not in t:
    print("aviso open")
    raise SystemExit(1)
t = t.replace(old, new, 1)
print("ok open Box+Column")

# 2) Before composer Surface, close Column; Surface gets align BottomCenter
marker = "                // Campo de digitar:"
idx = t.find(marker)
if idx < 0:
    print("aviso marker campo")
    raise SystemExit(1)

surf = t.find("                Surface(", idx)
if surf < 0:
    print("aviso surface")
    raise SystemExit(1)

if "} // end header+list column" not in t[idx-20:surf+10]:
    t = t[:surf] + "                } // end header+list column\n" + t[surf:]
    print("ok close column")
    surf = t.find("                Surface(", idx)

old_mod = "                    modifier = Modifier.fillMaxWidth(),"
pos = t.find(old_mod, surf)
if pos < 0 or pos > surf + 300:
    print("aviso mod", pos)
    raise SystemExit(1)
t = t[:pos] + "                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()," + t[pos+len(old_mod):]
print("ok align")

# 3) Balance braces
i = t.find("Entre na sua conta para comentar.")
j = t.find("private fun com.streamflixvip.app.network.TmdbResponse.bestLogoUrl", i)
s = t.find("private fun CommentsModal(")
e = t.find("private fun com.streamflixvip.app.network.TmdbResponse.bestLogoUrl", s)
modal = t[s:e]
op, cl = modal.count("{"), modal.count("}")
print("balance", op, cl, "diff", op - cl)
if op > cl:
    pos = t.rfind("}\n", i, j)
    t = t[:pos+2] + ("}\n" * (op - cl)) + t[pos+2:]
    print("ok added", op - cl, "closes")
elif cl > op:
    for _ in range(cl - op):
        pos = t.rfind("}\n", i, j)
        t = t[:pos] + t[pos+2:]
    print("ok removed extras")
else:
    print("ok balance")

p.write_text(t)
s = t.find("private fun CommentsModal(")
e = t.find("private fun com.streamflixvip.app.network.TmdbResponse.bestLogoUrl", s)
modal = t[s:e]
print("final balance", modal.count("{"), modal.count("}"))
print("align", "align(Alignment.BottomCenter)" in modal)
print("box ime", "Box(Modifier.fillMaxSize().imePadding())" in modal)
print("fim")
