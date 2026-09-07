#!/usr/bin/env python3
from pathlib import Path
p = Path('android/app/src/main/java/com/streamflixvip/app/ui/home/HomeScreen.kt')
t = p.read_text()
if 'height(292.dp)' in t and 'Ver detalhes' in t:
    print('home ja no layout novo')
    raise SystemExit(0)
old_order = '''                if (s.heroItems.isNotEmpty()) {
                    item {
                        HeroBanner(
                            items = s.heroItems,
                            onClick = { item -> onItemClick(item.id, item.resolvedMediaType) },
                        )
                    }
                }

                if (s.continueWatching.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(28.dp))
                        ContinueWatchingRow(entries = s.continueWatching, onItemClick = onContinueWatchingClick, onItemDismiss = onContinueWatchingDismiss)
                        Spacer(Modifier.height(12.dp))
                    }
                }'''
new_order = '''                if (s.continueWatching.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(10.dp))
                        ContinueWatchingRow(entries = s.continueWatching, onItemClick = onContinueWatchingClick, onItemDismiss = onContinueWatchingDismiss)
                        Spacer(Modifier.height(8.dp))
                    }
                }

                if (s.heroItems.isNotEmpty()) {
                    item {
                        HeroBanner(
                            items = s.heroItems,
                            onClick = { item -> onItemClick(item.id, item.resolvedMediaType) },
                        )
                    }
                }'''
if old_order not in t:
    raise SystemExit('bloco hero/continuar nao encontrado — arquivo mudou')
t = t.replace(old_order, new_order, 1)
t = t.replace('.height(420.dp)', '.height(292.dp)', 1)
t = t.replace('Color(0xFFFF3D71)', 'Color(0xFFF27667)')
old_ov = '''            val overview = item.overview?.takeIf { it.isNotBlank() }
                ?: "Confira detalhes, nota e op\u00e7\u00f5es para assistir."

            Box('''
# keep ascii fallback
old_ov2 = 'val overview = item.overview?.takeIf { it.isNotBlank() }'
if old_ov2 in t:
    # drop overview text block if present
    import re
    t = re.sub(
        r'\s*val overview = item\.overview\?\.takeIf \{ it\.isNotBlank\(\) \}\n\s*\?: ".*?"\n',
        '\n',
        t,
        count=1,
        flags=re.S,
    )
    t = re.sub(
        r'\s*Spacer\(Modifier\.height\(8\.dp\)\)\n\s*Text\(\n\s*text = overview,[\s\S]*?Ellipsis,\n\s*\),\n\s*Spacer\(Modifier\.height\(14\.dp\)\)',
        '\n                    Spacer(Modifier.height(12.dp))',
        t,
        count=1,
    )
t = t.replace('Text("Assistir", fontWeight = FontWeight.Bold)', 'Text("Ver detalhes", fontWeight = FontWeight.Bold)', 1)
p.write_text(t)
print('home noir layout ok', p.stat().st_size)
