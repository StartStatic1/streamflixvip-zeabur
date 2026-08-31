#!/usr/bin/env python3
from pathlib import Path
p = Path("android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt")
t = p.read_text()
old = "private fun CinemaServersLoading()"
new = "@Composable\nprivate fun CinemaServersLoading()"
if "@Composable\nprivate fun CinemaServersLoading()" in t:
    print("ja ok")
elif old not in t:
    raise SystemExit("trecho nao encontrado")
else:
    p.write_text(t.replace(old, new, 1))
    print("fix ok")
