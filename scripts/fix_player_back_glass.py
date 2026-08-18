#!/usr/bin/env python3
from pathlib import Path
import re

# 1) MainActivity onBack — NAO usar find("PlayerScreen") pois pega LivePlayerScreen
ma = Path("android/app/src/main/java/com/streamflixvip/app/MainActivity.kt")
t = ma.read_text()
m = re.search(
    r"PlayerScreen\(\s*\n\s*sourceUrl = url,\s*\n\s*isDirectPlayable = isDirect,",
    t,
)
if not m:
    raise SystemExit("PlayerScreen(sourceUrl) not found")
after = t[m.end():m.end()+80]
if "onBack = { navController.popBackStack() }" not in after:
    t = t[:m.end()] + "\n                        onBack = { navController.popBackStack() }," + t[m.end():]
    ma.write_text(t)
    print("MA onBack ok")
else:
    print("MA already")

# 2) PlayerScreen glass button
ps = Path("android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt")
t = ps.read_text()

if "import androidx.compose.material.icons.automirrored.filled.ArrowBack" not in t:
    if "import androidx.compose.material.icons.filled.BrightnessHigh" in t:
        t = t.replace(
            "import androidx.compose.material.icons.filled.BrightnessHigh",
            "import androidx.compose.material.icons.filled.BrightnessHigh\nimport androidx.compose.material.icons.automirrored.filled.ArrowBack",
            1,
        )
    else:
        t = t.replace(
            "import androidx.compose.material.icons.Icons",
            "import androidx.compose.material.icons.Icons\nimport androidx.compose.material.icons.automirrored.filled.ArrowBack",
            1,
        )

if "import androidx.compose.foundation.layout.width" not in t and "layout.width" not in t:
    if "import androidx.compose.foundation.layout.size" in t:
        t = t.replace(
            "import androidx.compose.foundation.layout.size",
            "import androidx.compose.foundation.layout.size\nimport androidx.compose.foundation.layout.width",
            1,
        )

glass = """Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = Color.White.copy(alpha = 0.18f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                modifier = Modifier.size(40.dp).clickable { onBack() },
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
"""

if '"< Voltar"' in t:
    lines = t.splitlines(True)
    out = []
    i = 0
    replaced = False
    while i < len(lines):
        if not replaced and '"< Voltar"' in lines[i]:
            j = i
            while j > 0 and "Text(" not in lines[j]:
                j -= 1
            while i < len(lines):
                if lines[i].strip() in (")", "),") or lines[i].rstrip().endswith(")"):
                    if "Text(" in lines[j] or i > j:
                        i += 1
                        break
                i += 1
            out.append(glass)
            replaced = True
            continue
        out.append(lines[i])
        i += 1
    t = "".join(out)
    print("replaced Voltar text", replaced)
elif "Icons.AutoMirrored.Filled.ArrowBack" in t:
    print("glass already present")
else:
    marker = '            val epLabel = if (mediaType == "tv"'
    if marker in t:
        t = t.replace(marker, glass + "\n            " + marker, 1)
        print("inserted glass before epLabel")
    else:
        print("WARN no insertion point")

ps.write_text(t)
print("DONE")
