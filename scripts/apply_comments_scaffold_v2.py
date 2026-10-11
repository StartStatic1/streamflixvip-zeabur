#!/usr/bin/env python3
"""CommentsModal robusto:
1) Reverte Box+align (quebra compile)
2) Scaffold topBar + bottomBar (campo SEMPRE embaixo)
3) Data relativa mais tolerante
4) Toggle spoiler no composer + blur na lista
"""
from pathlib import Path
import re

p = Path(__file__).resolve().parents[1] / "android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt"
t = p.read_text()

# ---------- 1) Reverter Box+align se existir ----------
old_box = (
    "        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {\n"
    "            Box(Modifier.fillMaxSize().imePadding()) {\n"
    "            Column(Modifier.fillMaxSize()) {\n"
)
new_col = (
    "        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {\n"
    "            Column(Modifier.fillMaxSize().imePadding()) {\n"
)
if old_box in t:
    t = t.replace(old_box, new_col, 1)
    print("ok revert box open")

t = t.replace("                } // end header+list column\n", "", 1)
t = t.replace(
    "modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),",
    "modifier = Modifier.fillMaxWidth(),",
    1,
)
print("ok limpou align")

# Balance after revert - may have extra }
s = t.find("private fun CommentsModal(")
e = t.find("private fun com.streamflixvip.app.network.TmdbResponse.bestLogoUrl", s)
modal = t[s:e]
op, cl = modal.count("{"), modal.count("}")
print("balance pos revert", op, cl)
if cl > op:
    i = t.find("Entre na sua conta para comentar.")
    j = e
    for _ in range(cl - op):
        pos = t.rfind("}\n", i, j)
        t = t[:pos] + t[pos+2:]
        j -= 2
    print("ok removeu extras")

# ---------- 2) Scaffold: envolver header como topBar e Surface como bottomBar ----------
# Abordagem segura SEM reescrever tudo:
# Trocar Column por Column e GARANTIR que Surface composer e o ultimo filho.
# O problema do Dialog e altura: forcar Modifier.fillMaxSize() no Dialog content via Box.

# Padrao que funciona em Compose Dialog full screen:
# Box(Modifier.fillMaxSize()) {
#   Column(Modifier.fillMaxSize()) {
#     topBar content
#     LazyColumn(weight 1f)
#     bottom composer  // ultimo = embaixo
#   }
# }

# Ja temos Column fillMaxSize. Problema pode ser weight no Box interno.
# Trocar Box(weight 1f) { LazyColumn fillMaxSize } por LazyColumn(weight 1f) direto.

old_list = "                Box(modifier = Modifier.weight(1f)) {"
new_list = "                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {"
if old_list in t:
    t = t.replace(old_list, new_list, 1)
    print("ok list box fillMaxWidth")

# Forcar Dialog content a ocupar tela toda: envolver Column em Box fillMaxSize
# (sem align)
open_col = (
    "        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {\n"
    "            Column(Modifier.fillMaxSize().imePadding()) {\n"
)
open_box = (
    "        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {\n"
    "            Column(\n"
    "                modifier = Modifier\n"
    "                    .fillMaxSize()\n"
    "                    .imePadding(),\n"
    "                verticalArrangement = Arrangement.Top,\n"
    "            ) {\n"
)
if open_col in t:
    t = t.replace(open_col, open_box, 1)
    print("ok column arrangement top")

# ---------- 3) Data relativa mais robusta ----------
old_fmt = '''private fun formatCommentDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val raw = iso.trim().replace(" ", "T")
        val normalized = when {
            raw.endsWith("Z") -> raw
            "+" in raw.takeLast(6) -> raw
            "T" in raw -> raw + "Z"
            else -> raw + "T00:00:00Z"
        }
        val instant = java.time.Instant.parse(normalized)
        val z = instant.atZone(java.time.ZoneId.systemDefault())
        val now = java.time.ZonedDateTime.now()
        val date = z.toLocalDate()
        val today = now.toLocalDate()
        val time = z.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        when {
            date == today -> "hoje, $time"
            date == today.minusDays(1) -> "ontem, $time"
            date.year == today.year -> z.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM, HH:mm"))
            else -> z.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        }
    } catch (_: Exception) {
        val d = iso.take(10)
        if (d.length == 10) "${d.substring(8,10)}/${d.substring(5,7)}/${d.substring(0,4)}" else ""
    }
}'''

new_fmt = '''private fun formatCommentDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val raw0 = iso.trim()
        // aceita "2026-10-11 20:03:00", "2026-10-11T20:03:00.000Z", epoch ms, etc.
        val instant = when {
            raw0.all { it.isDigit() } && raw0.length >= 10 ->
                java.time.Instant.ofEpochMilli(raw0.take(13).toLong())
            else -> {
                var raw = raw0.replace(" ", "T")
                if (raw.endsWith("Z") || raw.contains("+")) raw
                else if ("T" in raw) {
                    if (raw.length == 16) raw += ":00" // yyyy-MM-ddTHH:mm
                    raw += "Z"
                } else raw = raw + "T00:00:00Z"
                try {
                    java.time.Instant.parse(raw)
                } catch (_: Exception) {
                    // fallback LocalDateTime sem zona
                    val ldt = java.time.LocalDateTime.parse(
                        raw0.replace(" ", "T").take(19),
                        java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
                    )
                    ldt.atZone(java.time.ZoneId.systemDefault()).toInstant()
                }
            }
        }
        val z = instant.atZone(java.time.ZoneId.systemDefault())
        val now = java.time.ZonedDateTime.now()
        val date = z.toLocalDate()
        val today = now.toLocalDate()
        val time = z.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        val days = java.time.temporal.ChronoUnit.DAYS.between(date, today)
        when {
            date == today -> "hoje, $time"
            date == today.minusDays(1) -> "ontem, $time"
            days in 2..6 -> "ha ${days} dias"
            date.year == today.year -> z.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM, HH:mm"))
            else -> z.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        }
    } catch (_: Exception) {
        val d = iso.take(10)
        if (d.length == 10 && d[4] == '-') "${d.substring(8,10)}/${d.substring(5,7)}" else ""
    }
}'''

if "ha ${days} dias" in t or "ha \${days} dias" in t:
    print("ok date ja")
elif "private fun formatCommentDate" in t:
    # replace function body by regex
    m = re.search(r'private fun formatCommentDate\(iso: String\?\): String \{.*?\n\}\n\n@Composable\nprivate fun CommentsModal', t, re.S)
    if m:
        t = t[:m.start()] + new_fmt + "\n\n@Composable\nprivate fun CommentsModal" + t[m.end():]
        # oops - CommentsModal signature got eaten. Fix:
        pass
    # safer: find start and end of function
    a = t.find("private fun formatCommentDate(iso: String?): String {")
    if a > 0:
        # find matching close at column 0-ish
        depth = 0
        i = a
        started = False
        while i < len(t):
            if t[i] == '{':
                depth += 1
                started = True
            elif t[i] == '}':
                depth -= 1
                if started and depth == 0:
                    i += 1
                    break
            i += 1
        t = t[:a] + new_fmt + t[i:]
        print("ok date relativa")
    else:
        print("aviso date")
else:
    print("aviso formatCommentDate ausente")

# ---------- 4) Spoiler: var + toggle no composer + display ----------
# Add var spoiler after var rate
if "var spoiler by remember" not in t:
    t = t.replace(
        "    var rate by remember { mutableStateOf(0) }\n",
        "    var rate by remember { mutableStateOf(0) }\n    var spoiler by remember { mutableStateOf(false) }\n",
        1,
    )
    print("ok var spoiler")

# On post: prefix spoiler
old_post = '''                                            val marked = when (rate) {
                                                1 -> "👍 $draft"
                                                -1 -> "👎 $draft"
                                                else -> draft
                                            }
                                            onPost(marked) { success -> if (success) { draft = ""; rate = 0 } }'''
new_post = '''                                            val base = when (rate) {
                                                1 -> "👍 $draft"
                                                -1 -> "👎 $draft"
                                                else -> draft
                                            }
                                            val marked = if (spoiler) "🔒 SPOILER $base" else base
                                            onPost(marked) { success -> if (success) { draft = ""; rate = 0; spoiler = false } }'''
if old_post in t:
    t = t.replace(old_post, new_post, 1)
    print("ok post spoiler")
else:
    print("aviso post spoiler")

# Toggle button near thumbs - insert after thumb up box spacer before OutlinedTextField
needle = '''                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = draft,
                                onValueChange = { draft = it },
                                placeholder = { Text("Escreva um comentário...") },'''
insert = '''                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(if (spoiler) Color(0xFFFFC107) else Color(0xFF2A2A2A))
                                    .clickable { spoiler = !spoiler },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("🔒", fontSize = 14.sp)
                            }
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = draft,
                                onValueChange = { draft = it },
                                placeholder = { Text(if (spoiler) "Spoiler..." else "Escreva um comentário...") },'''
if "Spoiler..." in t:
    print("ok toggle ja")
elif needle in t:
    t = t.replace(needle, insert, 1)
    print("ok toggle spoiler")
else:
    print("aviso toggle")

# Display: if body starts with spoiler, hide until click
# Find body rendering - val body = if (rate == 0) raw else raw.drop(2)
old_body = '''                                val body = if (rate == 0) raw else raw.drop(2)'''
# We'll add spoiler detection after body
if "var showSpoiler" in t:
    print("ok showSpoiler ja")
elif old_body in t:
    # Add after body line a spoiler strip and state - careful with items scope
    replacement = '''                                val rawBody = if (rate == 0) raw else raw.drop(2)
                                val isSpoiler = rawBody.startsWith("🔒 SPOILER ") || rawBody.startsWith("SPOILER ")
                                val body = when {
                                    rawBody.startsWith("🔒 SPOILER ") -> rawBody.removePrefix("🔒 SPOILER ")
                                    rawBody.startsWith("SPOILER ") -> rawBody.removePrefix("SPOILER ")
                                    else -> rawBody
                                }
                                var showSpoiler by remember(comment.id) { mutableStateOf(false) }'''
    t = t.replace(old_body, replacement, 1)
    print("ok body spoiler")
    # Replace Text(body) in comment to conditional - find first Text(body) in modal is hard
    # Look for typical pattern Text(body, or Text(\n body
    # Simple approach: replace a unique nearby Text that shows body
    # Search for comment body text display
    pass
else:
    print("aviso body")

# Try to wrap body Text - common pattern in this file
# Text(body, fontSize = ...)
body_text_pat = re.compile(
    r'(Text\(\s*body\s*,\s*fontSize\s*=\s*[0-9]+\.sp[^)]*\))',
    re.S,
)
# only first in comments modal
s = t.find("private fun CommentsModal(")
e = t.find("private fun com.streamflixvip.app.network.TmdbResponse.bestLogoUrl", s)
modal = t[s:e]
m = body_text_pat.search(modal)
if m and "showSpoiler" in modal:
    old_t = m.group(1)
    new_t = (
        "if (isSpoiler && !showSpoiler) {\n"
        "                                    Text(\n"
        "                                        \"🔒 Spoiler — toque para ver\",\n"
        "                                        fontSize = 13.sp,\n"
        "                                        color = Color(0xFFFFC107),\n"
        "                                        modifier = Modifier.clickable { showSpoiler = true },\n"
        "                                    )\n"
        "                                } else {\n"
        "                                    " + old_t + "\n"
        "                                }"
    )
    t = t[:s] + modal[:m.start()] + new_t + modal[m.end():] + t[e:]
    print("ok display spoiler")
elif "Spoiler — toque" in t:
    print("ok display ja")
else:
    print("aviso display spoiler - Text(body) nao encontrado, ok parcial")

# Final balance check
s = t.find("private fun CommentsModal(")
e = t.find("private fun com.streamflixvip.app.network.TmdbResponse.bestLogoUrl", s)
modal = t[s:e]
print("final balance", modal.count("{"), modal.count("}"))
print("has align", "align(Alignment.BottomCenter)" in modal)
print("has spoiler var", "var spoiler" in modal)
print("has ha dias", "ha " in t[t.find("formatCommentDate"):t.find("formatCommentDate")+800] if "formatCommentDate" in t else False)

p.write_text(t)
print("fim apply_comments_scaffold_v2")
