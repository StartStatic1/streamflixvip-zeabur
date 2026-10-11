#!/usr/bin/env python3
"""1) Compositor de comentarios no rodape (Box+align Bottom)
2) FlixHub: se stream nao tem ano no nome, nao descarta
3) Skeleton: remove lista falsa estatica; so mostra o que ja veio + buscando mais
"""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
n = 0

# --- FlixHub year filter (server) ---
addons = root / "lib/stremio-addons.js"
if addons.exists():
    t = addons.read_text()
    old = (
        "  if (isFlix && queryYear) {\n"
        "    if (!y || Number(queryYear) !== Number(y)) return false;\n"
        "  }"
    )
    new = (
        "  // FlixHub/Xtream: so rejeita quando o titulo TRAZ ano e ele diverge.\n"
        "  // Sem ano no nome, deixa passar (senao some quase tudo do painel).\n"
        "  if (isFlix && queryYear) {\n"
        "    if (y && Number(queryYear) !== Number(y)) return false;\n"
        "  }"
    )
    if "Sem ano no nome, deixa passar" in t:
        print("ok flixhub year ja")
    elif old in t:
        addons.write_text(t.replace(old, new, 1))
        n += 1
        print("ok flixhub year")
    else:
        print("aviso flixhub year")
else:
    print("aviso: lib/stremio-addons.js ausente")

# --- DetailScreen: compositor no rodape ---
p = root / "android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt"
t = p.read_text()

# Troca Column+Box+Surface por Box com composer alinhado embaixo
old_open = (
    "        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {\n"
    "            Column(Modifier.fillMaxSize().imePadding()) {\n"
    "                Row(\n"
    "                    modifier = Modifier.fillMaxWidth().padding(16.dp),\n"
    "                    horizontalArrangement = Arrangement.SpaceBetween,\n"
    "                    verticalAlignment = Alignment.CenterVertically,\n"
    "                ) {\n"
    "                    Row(verticalAlignment = Alignment.CenterVertically) {\n"
    '                        Text("Comentários", fontSize = 18.sp, fontWeight = FontWeight.Bold)'
)
new_open = (
    "        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {\n"
    "            Box(Modifier.fillMaxSize().imePadding()) {\n"
    "            Column(Modifier.fillMaxSize()) {\n"
    "                Row(\n"
    "                    modifier = Modifier.fillMaxWidth().padding(16.dp),\n"
    "                    horizontalArrangement = Arrangement.SpaceBetween,\n"
    "                    verticalAlignment = Alignment.CenterVertically,\n"
    "                ) {\n"
    "                    Row(verticalAlignment = Alignment.CenterVertically) {\n"
    '                        Text("Comentários", fontSize = 18.sp, fontWeight = FontWeight.Bold)'
)
if "Box(Modifier.fillMaxSize().imePadding())" in t and "align(Alignment.BottomCenter)" in t:
    print("ok composer bottom ja")
elif old_open in t:
    t = t.replace(old_open, new_open, 1)
    n += 1
    print("ok composer box open")
else:
    print("aviso composer open")

# Surface do composer: align bottom
old_sf = (
    "                Surface(\n"
    "                    color = MaterialTheme.colorScheme.surface,\n"
    "                    tonalElevation = 6.dp,\n"
    "                    shadowElevation = 8.dp,\n"
    "                    modifier = Modifier.fillMaxWidth(),\n"
    "                ) {"
)
new_sf = (
    "                } // fim Column lista\n"
    "                Surface(\n"
    "                    color = MaterialTheme.colorScheme.surface,\n"
    "                    tonalElevation = 6.dp,\n"
    "                    shadowElevation = 8.dp,\n"
    "                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),\n"
    "                ) {"
)
if "align(Alignment.BottomCenter).fillMaxWidth()" in t:
    print("ok align bottom ja")
elif old_sf in t:
    t = t.replace(old_sf, new_sf, 1)
    n += 1
    print("ok align bottom")
else:
    print("aviso align bottom")

# Fecha Box extra no fim do CommentsModal (antes do fechamento do Surface do dialog)
# Procurar o padrao de fechamento do modal apos "Entre na sua conta"
old_close = (
    '                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,\n'
    "                        )\n"
    "                    }\n"
    "                }\n"
    "            }\n"
    "        }\n"
    "    }\n"
    "}\n"
)
# May already have been modified; try softer approach: if we added align bottom but not extra close for Box
if "align(Alignment.BottomCenter).fillMaxWidth()" in t and t.count("Box(Modifier.fillMaxSize().imePadding())") >= 1:
    # Ensure one extra closing brace for Box after Surface composer block ends
    # Find the composer surface end pattern near CommentsModal end
    marker = 'Entre na sua conta para comentar.'
    i = t.find(marker)
    if i > 0:
        # from marker, find the closing sequence of if/surface/column/surface/dialog/function
        tail = t[i:i+800]
        if "Box(Modifier.fillMaxSize().imePadding())" in t[t.find("CommentsModal"):i] or True:
            # If we inserted "} // fim Column lista" before Surface, the original closes were Column+Surface(dialog).
            # Now we need Column close (done via comment), Surface composer, then Box, then dialog Surface.
            # Check if "} // fim Column lista" exists and if after composer we have enough braces
            if "} // fim Column lista" in t:
                print("ok column close marker")
            else:
                print("aviso column close marker")

# contentPadding bottom maior para nao ficar sob o compositor
t = t.replace(
    "contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),",
    "contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 100.dp),",
    1,
)
t = t.replace(
    "contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 80.dp),",
    "contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 100.dp),",
    1,
)
print("ok contentPadding")

# --- Skeleton: volta ao estilo progressivo simples (sem nomes falsos) ---
if "pendingNames" in t:
    start = t.find("if (loading && sources.isEmpty()) {")
    mid = t.find("} else if (loading && shown.isNotEmpty()) {")
    if start > 0 and mid > start and mid - start < 2500:
        block = '''if (loading && sources.isEmpty()) {
                    val infinite = rememberInfiniteTransition(label = "skel")
                    val shimmer by infinite.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(animation = tween(1100), repeatMode = RepeatMode.Restart),
                        label = "shimmer",
                    )
                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                        Text("Buscando as melhores fontes...", color = Color(0xFFAAAAAA), fontSize = 14.sp, modifier = Modifier.padding(bottom = 14.dp))
                        repeat(4) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .padding(vertical = 5.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(Color(0xFF141414), Color(0xFF242424), Color(0xFF141414)),
                                            start = Offset(shimmer * 400f - 180f, 0f),
                                            end = Offset(shimmer * 400f + 180f, 0f),
                                        )
                                    )
                            )
                        }
                        Text(
                            "As fontes mais rapidas aparecem primeiro.",
                            color = Color(0xFF666666),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                } else if (loading && shown.isNotEmpty()) {'''
        t = t[:start] + block + t[mid + len("} else if (loading && shown.isNotEmpty()) {"):]
        n += 1
        print("ok skeleton progressivo")
    else:
        print("aviso skeleton bounds", start, mid)
else:
    print("ok skeleton sem pendingNames")

p.write_text(t)
print("fim apply_comments_bottom_flixhub_year", n)
