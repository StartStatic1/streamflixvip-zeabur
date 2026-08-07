#!/usr/bin/env python3
from pathlib import Path
p = Path("android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt")
t = p.read_text()
if "import androidx.compose.foundation.layout.Spacer" in t and "import androidx.compose.foundation.layout.height" in t:
    print("already fixed")
    raise SystemExit(0)
if "import androidx.compose.foundation.layout.Spacer" not in t:
    t = t.replace(
        "import androidx.compose.foundation.layout.Box\n",
        "import androidx.compose.foundation.layout.Box\nimport androidx.compose.foundation.layout.Spacer\nimport androidx.compose.foundation.layout.height\n",
    )
if "import androidx.compose.foundation.layout.height\n" not in t:
    t = t.replace(
        "import androidx.compose.foundation.layout.Spacer\n",
        "import androidx.compose.foundation.layout.Spacer\nimport androidx.compose.foundation.layout.height\n",
    )
p.write_text(t)
assert "import androidx.compose.foundation.layout.Spacer" in t
assert "import androidx.compose.foundation.layout.height" in t
print("fixed imports", len(t))
