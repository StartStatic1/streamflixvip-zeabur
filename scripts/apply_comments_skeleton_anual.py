#!/usr/bin/env python3
"""Data detalhada nos comentarios, compositor com imePadding, skeleton nomeado, frase Anual."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]

# --- Pix phrase ---
pix = root / "android/app/src/main/java/com/streamflixvip/app/ui/vip/PixPaymentSheet.kt"
t = pix.read_text()
old = (
    '            planLabel.contains("Vitalicio", ignoreCase = true) ->\n'
    '                "O final feliz: acesso ilimitado para sempre."'
)
new = (
    '            planLabel.contains("Anual", ignoreCase = true) || planLabel.contains("Vitalicio", ignoreCase = true) ->\n'
    '                "Um ano de sessao VIP. Luz, camera, acao o ano inteiro."'
)
if "ano de sessao VIP" in t:
    print("ok pix ja")
elif old in t:
    pix.write_text(t.replace(old, new, 1))
    print("ok pix")
else:
    print("aviso pix")

# --- DetailScreen ---
p = root / "android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt"
t = p.read_text()
n = 0

if "fun formatCommentDate" not in t:
    helper = r'''
private fun formatCommentDate(iso: String?): String {
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
}

'''
    t = t.replace("private fun CommentsModal(", helper + "private fun CommentsModal(", 1)
    n += 1
    print("ok date helper")
else:
    print("ok date helper ja")

old_date = (
    "                                        Text(\n"
    "                                            comment.created_at.take(10).let { d ->\n"
    '                                                if (d.length == 10) "${d.substring(8,10)}/${d.substring(5,7)}" else ""\n'
    "                                            },\n"
    "                                            fontSize = 11.sp,\n"
    "                                            color = MaterialTheme.colorScheme.onSurfaceVariant,\n"
    "                                        )"
)
new_date = (
    "                                        Text(\n"
    "                                            formatCommentDate(comment.created_at),\n"
    "                                            fontSize = 11.sp,\n"
    "                                            color = MaterialTheme.colorScheme.onSurfaceVariant,\n"
    "                                        )"
)
if "formatCommentDate(comment.created_at)" in t:
    print("ok date display ja")
elif old_date in t:
    t = t.replace(old_date, new_date, 1)
    n += 1
    print("ok date display")
else:
    print("aviso date display")

if "Column(Modifier.fillMaxSize().imePadding())" in t:
    print("ok ime ja")
elif 'Text("Coment\u00e1rios", fontSize = 18.sp, fontWeight = FontWeight.Bold)' in t or 'Text("Comentários", fontSize = 18.sp, fontWeight = FontWeight.Bold)' in t:
    needle = "Column(Modifier.fillMaxSize()) {\n                Row(\n                    modifier = Modifier.fillMaxWidth().padding(16.dp),\n                    horizontalArrangement = Arrangement.SpaceBetween,\n                    verticalAlignment = Alignment.CenterVertically,\n                ) {\n                    Row(verticalAlignment = Alignment.CenterVertically) {\n                        Text(\"Comentários\""
    rep = "Column(Modifier.fillMaxSize().imePadding()) {\n                Row(\n                    modifier = Modifier.fillMaxWidth().padding(16.dp),\n                    horizontalArrangement = Arrangement.SpaceBetween,\n                    verticalAlignment = Alignment.CenterVertically,\n                ) {\n                    Row(verticalAlignment = Alignment.CenterVertically) {\n                        Text(\"Comentários\""
    if needle in t:
        t = t.replace(needle, rep, 1)
        n += 1
        print("ok ime")
    else:
        print("aviso ime")
else:
    print("aviso ime")

old_sf = (
    "                Surface(\n"
    "                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),\n"
    "                    modifier = Modifier.fillMaxWidth(),\n"
    "                ) {"
)
if "tonalElevation = 6.dp" in t and "Campo de digitar fixo" in t:
    print("ok surface ja")
elif old_sf in t:
    count = t.count(old_sf)
    t = t.replace(old_sf, (
        "                Surface(\n"
        "                    color = MaterialTheme.colorScheme.surface,\n"
        "                    tonalElevation = 6.dp,\n"
        "                    shadowElevation = 8.dp,\n"
        "                    modifier = Modifier.fillMaxWidth(),\n"
        "                ) {"
    ), 1)
    n += 1
    print("ok surface", "matches_were", count)
else:
    print("aviso surface")

if "pendingNames" in t:
    print("ok skel ja")
else:
    start = t.find("if (loading && sources.isEmpty()) {")
    mid = t.find("} else if (loading && shown.isNotEmpty()) {")
    if start > 0 and mid > start and mid - start < 2000:
        block = '''if (loading && sources.isEmpty()) {
                    val infinite = rememberInfiniteTransition(label = "skel")
                    val shimmer by infinite.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(animation = tween(1200), repeatMode = RepeatMode.Restart),
                        label = "shimmer",
                    )
                    val pendingNames = listOf("GuIndex", "FlixHub", "FrostStream", "Hyper", "Goldvip")
                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                        Text("\U0001f50d Buscando as melhores fontes...", color = Color(0xFFAAAAAA), fontSize = 14.sp, modifier = Modifier.padding(bottom = 12.dp))
                        pendingNames.forEach { name ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(Color(0xFF141414), Color(0xFF222222), Color(0xFF141414)),
                                            start = Offset(shimmer * 420f - 200f, 0f),
                                            end = Offset(shimmer * 420f + 200f, 0f),
                                        )
                                    )
                                    .padding(horizontal = 14.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Buscando\u2026", color = Color(0xFF8A8A8A), fontSize = 12.sp)
                            }
                        }
                    }
                } else if (loading && shown.isNotEmpty()) {'''
        t = t[:start] + block + t[mid + len("} else if (loading && shown.isNotEmpty()) {"):]
        n += 1
        print("ok skel")
    else:
        print("aviso skel", start, mid)

p.write_text(t)
print("fim apply_comments_skeleton_anual", n)
