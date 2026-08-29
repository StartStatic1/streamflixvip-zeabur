#!/usr/bin/env python3
"""Evita flash 'filme nao esta no catalogo' enquanto isLoadingMovieSources."""
from pathlib import Path

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt")
t = p.read_text()

OLD = '''            } else if (state.movieSources.isEmpty()) {
                item {
                    MovieRequestCard()
                }
            }'''

NEW = '''            } else if (state.isLoadingMovieSources) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Buscando fontes…",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (state.movieSources.isEmpty()) {
                item {
                    MovieRequestCard()
                }
            }'''

if OLD not in t:
    if "isLoadingMovieSources" in t and "Buscando fontes" in t:
        print("already fixed")
        raise SystemExit(0)
    raise SystemExit("pattern not found")

t = t.replace(OLD, NEW, 1)

# imports se faltarem
if "CircularProgressIndicator" not in t.split("fun DetailContent")[0] and "import androidx.compose.material3.CircularProgressIndicator" not in t:
    if "import androidx.compose.material3.MaterialTheme" in t:
        t = t.replace(
            "import androidx.compose.material3.MaterialTheme",
            "import androidx.compose.material3.CircularProgressIndicator\nimport androidx.compose.material3.MaterialTheme",
            1,
        )

if "Arrangement" not in t[:2500]:
    if "import androidx.compose.foundation.layout.Column" in t:
        t = t.replace(
            "import androidx.compose.foundation.layout.Column",
            "import androidx.compose.foundation.layout.Arrangement\nimport androidx.compose.foundation.layout.Column",
            1,
        )

p.write_text(t)
print("detail loading OK")
