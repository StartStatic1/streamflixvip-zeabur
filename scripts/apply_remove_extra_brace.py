#!/usr/bin/env python3
from pathlib import Path
p = Path(__file__).resolve().parents[1] / "android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt"
t = p.read_text()
# Remove a chave extra logo apos o fechamento do CommentsModal
old = (
    "                    }\n"
    "                }\n"
    "            }\n"
    "        }\n"
    "    }\n"
    "}\n"
    "}\n"
    "\n"
    "/**\n"
    " * Cabe\u00e7alho da tela de detalhes"
)
new = (
    "                    }\n"
    "                }\n"
    "            }\n"
    "        }\n"
    "    }\n"
    "}\n"
    "\n"
    "/**\n"
    " * Cabe\u00e7alho da tela de detalhes"
)
if old in t:
    t = t.replace(old, new, 1)
    p.write_text(t)
    print("ok removeu chave extra")
else:
    # try without accent escape
    idx = t.find("Entre na sua conta para comentar.")
    if idx < 0:
        print("aviso: marker nao achado")
    else:
        j = t.find("private fun com.streamflixvip.app.network.TmdbResponse.bestLogoUrl", idx)
        block = t[idx:j]
        # count trailing bare }
        stripped = block.rstrip()
        closes = 0
        while stripped.endswith("}"):
            stripped = stripped[:-1].rstrip()
            closes += 1
        print("closes after marker:", closes)
        if closes == 6:
            # remove one }
            # find last }\n before bestLogo
            pos = t.rfind("}\n", idx, j)
            # find the one before that at same level - remove the last consecutive
            t2 = t[:pos] + t[pos+2:]  # drop one }\n
            p.write_text(t2)
            print("ok removeu 1 chave (alt)")
        elif closes == 5:
            print("ok ja tem 5")
        else:
            print("aviso closes", closes)
print("fim")
