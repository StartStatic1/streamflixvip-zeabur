#!/usr/bin/env python3
"""Restaura CommentsModal para Column simples (composer no fim).
Remove Box+align que quebrou o build.
"""
from pathlib import Path

p = Path(__file__).resolve().parents[1] / "android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt"
t = p.read_text()

# 1) Box+Column open -> Column com imePadding
old1 = (
    "        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {\n"
    "            Box(Modifier.fillMaxSize().imePadding()) {\n"
    "            Column(Modifier.fillMaxSize()) {\n"
)
new1 = (
    "        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {\n"
    "            Column(Modifier.fillMaxSize().imePadding()) {\n"
)
if old1 in t:
    t = t.replace(old1, new1, 1)
    print("ok open Column")
elif "Box(Modifier.fillMaxSize().imePadding())" in t:
    t = t.replace(
        "            Box(Modifier.fillMaxSize().imePadding()) {\n            Column(Modifier.fillMaxSize()) {\n",
        "            Column(Modifier.fillMaxSize().imePadding()) {\n",
        1,
    )
    print("ok open Column (alt)")
else:
    print("ok open ja")

# 2) Remove '} // fim Column lista' e restaura Surface sem align
old2 = (
    "                } // fim Column lista\n"
    "                Surface(\n"
    "                    color = MaterialTheme.colorScheme.surface,\n"
    "                    tonalElevation = 6.dp,\n"
    "                    shadowElevation = 8.dp,\n"
    "                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),\n"
    "                ) {"
)
new2 = (
    "                Surface(\n"
    "                    color = MaterialTheme.colorScheme.surface,\n"
    "                    tonalElevation = 6.dp,\n"
    "                    shadowElevation = 8.dp,\n"
    "                    modifier = Modifier.fillMaxWidth(),\n"
    "                ) {"
)
if old2 in t:
    t = t.replace(old2, new2, 1)
    print("ok surface sem align")
elif "align(Alignment.BottomCenter)" in t:
    t = t.replace(
        "modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),",
        "modifier = Modifier.fillMaxWidth(),",
        1,
    )
    t = t.replace("                } // fim Column lista\n", "", 1)
    print("ok surface sem align (alt)")
else:
    print("ok surface ja")

# 3) Extra closing brace no fim do CommentsModal (linha extra }
# Padrao tipico apos o patch: 7 fecha-chaves seguidas antes do bestLogoUrl
marker = "Entre na sua conta para comentar."
i = t.find(marker)
if i > 0:
    # procura sequencia de } apos o marker ate bestLogoUrl
    j = t.find("private fun com.streamflixvip.app.network.TmdbResponse.bestLogoUrl", i)
    block = t[i:j]
    # conta linhas que sao so }
    lines = block.splitlines()
    close_run = []
    for idx, ln in enumerate(lines):
        if ln.strip() == "}":
            close_run.append(idx)
        elif close_run and ln.strip() != "":
            # interrupted
            if len(close_run) >= 5:
                break
            close_run = []
    # queremos exatamente 5 closes: surface, column, dialogSurface, dialog, function
    # se tiver 6, remove um
    if len(close_run) >= 6:
        # remove the last extra } before blank/comment
        # find last consecutive }
        # rebuild: keep 5 of the trailing closes
        # simpler: if block ends with \n}\n}\n\n before bestLogo
        tail = block.rstrip()
        # count trailing }
        k = 0
        while tail.endswith("}"):
            tail = tail[:-1].rstrip()
            k += 1
        print("trailing closes", k)
        if k >= 6:
            # rebuild with 5 closes
            # find start of trailing closes in original
            pos = j
            # walk back
            tmp = t[:j].rstrip()
            removed = 0
            while removed < (k - 5) and tmp.endswith("}"):
                tmp = tmp[:-1].rstrip()
                removed += 1
            t = tmp + "\n}\n}\n}\n}\n}\n\n" + t[j:]
            print("ok removeu", removed, "chaves extra")
        else:
            print("ok closes", k)
    else:
        print("ok closes run", len(close_run))

# garante contentPadding bottom 100
if "bottom = 100.dp" not in t:
    t = t.replace(
        "contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 80.dp),",
        "contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 100.dp),",
        1,
    )
    t = t.replace(
        "contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),",
        "contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 100.dp),",
        1,
    )
print("ok padding")

p.write_text(t)
print("fim apply_fix_comments_column")
