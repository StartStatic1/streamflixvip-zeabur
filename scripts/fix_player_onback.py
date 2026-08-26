#!/usr/bin/env python3
from pathlib import Path

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt")
t = p.read_text()

if "fun NativePlayer" not in t:
    raise SystemExit("PlayerScreen incompleto")

# Assinatura com onBack
if "onBack: () -> Unit" not in t.split("fun PlayerScreen")[1][:800]:
    old = "    resumeSeconds: Int = 0,\n) {"
    new = "    resumeSeconds: Int = 0,\n    onBack: () -> Unit = {},\n) {"
    if old not in t:
        # variante sem virgula final
        old2 = "    resumeSeconds: Int = 0\n) {"
        new2 = "    resumeSeconds: Int = 0,\n    onBack: () -> Unit = {},\n) {"
        if old2 not in t:
            raise SystemExit("assinatura resumeSeconds nao encontrada")
        t = t.replace(old2, new2, 1)
    else:
        t = t.replace(old, new, 1)
    print("onBack param ok")
else:
    print("onBack param ja existe")

# Import BackHandler
if "import androidx.activity.compose.BackHandler" not in t:
    t = t.replace(
        "import androidx.compose.runtime.Composable\n",
        "import androidx.activity.compose.BackHandler\nimport androidx.compose.runtime.Composable\n",
        1,
    )
    print("import BackHandler ok")

# Body: BackHandler no inicio de PlayerScreen (apos val view = LocalView)
if "BackHandler { onBack() }" not in t and "BackHandler(onBack = onBack)" not in t:
    marker = "    val view = LocalView.current\n"
    if marker not in t:
        raise SystemExit("LocalView marker missing")
    t = t.replace(
        marker,
        marker + "    BackHandler { onBack() }\n",
        1,
    )
    print("BackHandler body ok")
else:
    print("BackHandler ja existe")

p.write_text(t)
print("DONE", p.stat().st_size)
assert "onBack: () -> Unit" in t
