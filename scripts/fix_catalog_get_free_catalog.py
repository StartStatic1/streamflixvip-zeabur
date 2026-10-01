#!/usr/bin/env python3
"""Restaura CatalogRepository de commit bom e coloca getFreeCatalog na class."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
p = ROOT / "android/app/src/main/java/com/streamflixvip/app/data/CatalogRepository.kt"

# 1) recupera versao com prioritize (antes dos PLACEHOLDER)
shas = subprocess.check_output(
    ["git", "log", "--pretty=format:%H", "--", str(p.relative_to(ROOT))],
    cwd=str(ROOT), text=True,
).strip().splitlines()

content = None
for sha in shas:
    try:
        c = subprocess.check_output(
            ["git", "show", f"{sha}:android/app/src/main/java/com/streamflixvip/app/data/CatalogRepository.kt"],
            cwd=str(ROOT), text=True, stderr=subprocess.DEVNULL,
        )
    except Exception:
        continue
    if c.strip() in ("", "PLACEHOLDER") or len(c) < 500:
        continue
    if "private fun prioritize" in c and "class CatalogRepository" in c:
        content = c
        print("base:", sha[:7], len(c))
        break

if not content:
    raise SystemExit("nao achei CatalogRepository valido no git")

# 2) tira getFreeCatalog de dentro do enum (se existir)
old_enum = '''    val mediaTypeOrDefault: String get() = mediaType ?: "movie"

    /** Area Free \u2014 filmes is_free no painel. */
    suspend fun getFreeCatalog(limit: Int = 50): List<TmdbItem> {
        return try {
            NetworkModule.freeCatalogApi.getFreeCatalog(limit).items
        } catch (_: Exception) {
            emptyList()
        }
    }

}'''
# try common dash variants
for dash in ["\u2014", "-", "—"]:
    block = old_enum.replace("\u2014", dash)
    if block in content:
        content = content.replace(block, '''    val mediaTypeOrDefault: String get() = mediaType ?: "movie"
}''', 1)
        print("removido do enum")
        break
else:
    # looser
    if "suspend fun getFreeCatalog" in content.split("enum class GenreCategory")[-1]:
        import re
        content2, n = re.subn(
            r"(\s+val mediaTypeOrDefault: String get\(\) = mediaType \?: \"movie\")\s+/\*\*[^*]*\*/\s*suspend fun getFreeCatalog\(limit: Int = 50\): List<TmdbItem> \{.*?\n\s*\}\n",
            r"\1\n",
            content,
            count=1,
            flags=re.S,
        )
        if n:
            content = content2
            print("removido do enum (regex)")

# 3) garante metodo na class CatalogRepository
class_part = content.split("data class GenreDefinition")[0]
if "suspend fun getFreeCatalog" not in class_part:
    method = '''
    /** Area Free - filmes is_free no painel. */
    suspend fun getFreeCatalog(limit: Int = 50): List<TmdbItem> {
        return try {
            NetworkModule.freeCatalogApi.getFreeCatalog(limit).items
        } catch (_: Exception) {
            emptyList()
        }
    }
'''
    idx = content.find("\ndata class GenreDefinition")
    if idx < 0:
        raise SystemExit("GenreDefinition nao achado")
    before = content[:idx].rstrip()
    if not before.endswith("}"):
        raise SystemExit("fechamento da class inesperado")
    content = before[:-1] + method + "}\n" + content[idx:]
    print("inserido na class")
else:
    print("ja na class")

p.write_text(content, encoding="utf-8")
print("wrote", p.stat().st_size)

t = p.read_text(encoding="utf-8")
assert "private fun prioritize" in t
assert "suspend fun getFreeCatalog" in t.split("data class GenreDefinition")[0]
assert "getFreeCatalog" not in t.split("enum class GenreCategory")[-1]
print("ok")
