#!/usr/bin/env python3
from pathlib import Path
p = Path("android-tv/app/src/main/java/com/streamflixvip/tv/ui/livetv/LiveTvScreen.kt")
t = p.read_text()
if "isAdultCat" in t:
    print("already"); raise SystemExit(0)
if len(t) < 1000 or "LiveTvViewModel" not in t:
    raise SystemExit("LiveTvScreen.kt corrupted — restore from git first")
old = (
"                .onSuccess { res ->\n"
"                    _uiState.update {\n"
"                        it.copy(\n"
"                            isLoading = false,\n"
"                            categories = res.categories.ifEmpty { listOf(LiveCategory(\"all\", \"Todos\")) },\n"
"                            channels = res.channels,\n"
"                            sourcesUsed = res.sourcesUsed,\n"
"                            error = if (res.channels.isEmpty()) \"Nenhum canal disponivel.\" else null,\n"
"                        )\n"
"                    }\n"
"                }\n"
)
new = (
"                .onSuccess { res ->\n"
"                    fun isAdultCat(id: String?, name: String?): Boolean {\n"
"                        val n = (name ?: \"\").lowercase()\n"
"                        if (id == \"000\" || id == \"00\") return true\n"
"                        val keys = listOf(\"adult\", \"xxx\", \"porn\", \"erotic\", \"onlyfans\", \"+18\", \"18+\", \"adulto\", \"sexy\")\n"
"                        return keys.any { n.contains(it) }\n"
"                    }\n"
"                    val cats = res.categories\n"
"                        .filter { !isAdultCat(it.id, it.name) }\n"
"                        .ifEmpty { listOf(LiveCategory(\"all\", \"Todos\")) }\n"
"                    val chans = res.channels.filter { !isAdultCat(it.categoryId, it.name) }\n"
"                    _uiState.update {\n"
"                        it.copy(\n"
"                            isLoading = false,\n"
"                            categories = cats,\n"
"                            channels = chans,\n"
"                            sourcesUsed = res.sourcesUsed,\n"
"                            error = if (chans.isEmpty()) \"Nenhum canal disponivel.\" else null,\n"
"                        )\n"
"                    }\n"
"                }\n"
)
if old not in t:
    raise SystemExit("anchor not found")
p.write_text(t.replace(old, new, 1))
print("ok adult")
