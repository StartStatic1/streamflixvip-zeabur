#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
p = ROOT / "android/app/src/main/java/com/streamflixvip/app/ui/home/HomeViewModel.kt"
t = p.read_text(encoding="utf-8")
old = '''                val freeArea = runCatching {
                    repository.getFreeCatalog(50)
                }.getOrElse { emptyList() }'''
new = '''                val freeArea: List<TmdbItem> = runCatching {
                    repository.getFreeCatalog(50)
                }.getOrElse { emptyList() }'''
if old in t:
    t = t.replace(old, new, 1)
    print("ok typed freeArea")
elif "val freeArea: List<TmdbItem>" in t:
    print("already typed")
else:
    print("block not found")
# ensure TmdbItem import
if "import com.streamflixvip.app.network.TmdbItem" not in t:
    t = t.replace(
        "import com.streamflixvip.app.network.WatchProgressEntry\n",
        "import com.streamflixvip.app.network.TmdbItem\nimport com.streamflixvip.app.network.WatchProgressEntry\n",
    )
    print("import TmdbItem")
p.write_text(t, encoding="utf-8")
print("done")
