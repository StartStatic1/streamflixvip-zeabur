#!/usr/bin/env python3
from pathlib import Path

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt")
t = p.read_text()

if "import androidx.compose.ui.graphics.Color" in t:
    print("already has Color import")
else:
    # Prefer after graphicsLayer import
    if "import androidx.compose.ui.graphics.graphicsLayer" in t:
        t = t.replace(
            "import androidx.compose.ui.graphics.graphicsLayer",
            "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.graphicsLayer",
            1,
        )
    elif "import androidx.compose.ui.Modifier" in t:
        t = t.replace(
            "import androidx.compose.ui.Modifier",
            "import androidx.compose.ui.Modifier\nimport androidx.compose.ui.graphics.Color",
            1,
        )
    else:
        raise SystemExit("no anchor for Color import")
    p.write_text(t)
    print("Color import added")

# sanity
t2 = p.read_text()
assert "import androidx.compose.ui.graphics.Color" in t2
assert "Color(0xFF25D366)" in t2 or "Pedir este filme no WhatsApp" in t2
print("ok")
