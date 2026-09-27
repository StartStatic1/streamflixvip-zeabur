#!/usr/bin/env python3
from pathlib import Path
import re
p = Path(__file__).resolve().parents[1] / "android/app/src/main/java/com/streamflixvip/app/ui/detail/ServerPickerUi.kt"
t = p.read_text(encoding="utf-8")
# remove buildChips function entirely
t2, n = re.subn(
    r"\nprivate fun buildChips\([\s\S]*?return out\n\}\n\n",
    "\n",
    t,
    count=1,
)
if n:
    t = t2
    print("ok removeu buildChips")
else:
    print("buildChips nao achado ou ja removido")
# remove unused import widthIn
t = t.replace("import androidx.compose.foundation.layout.widthIn\n", "")
p.write_text(t, encoding="utf-8")
print("fim", len(t))
