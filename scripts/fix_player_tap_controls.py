#!/usr/bin/env python3
"""Garante que toque na tela mostra controles + timeline do player."""
from pathlib import Path
import re

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt")
t = p.read_text()
if "fun NativePlayer" not in t:
    raise SystemExit("PlayerScreen broken")

if "import androidx.compose.foundation.gestures.detectTapGestures" not in t:
    t = t.replace(
        "import androidx.compose.foundation.gestures.detectVerticalDragGestures\n",
        "import androidx.compose.foundation.gestures.detectVerticalDragGestures\n"
        "import androidx.compose.foundation.gestures.detectTapGestures\n",
        1,
    )

if "playerViewRef" not in t:
    anchor = "    var gestureHideJob by remember { mutableStateOf<Job?>(null) }\n"
    if anchor not in t:
        anchor = "    var controlsVisible by remember { mutableStateOf(false) }\n"
        if anchor not in t:
            raise SystemExit("no anchor for playerViewRef")
    t = t.replace(
        anchor,
        anchor + "    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }\n",
        1,
    )
    print("playerViewRef added")
else:
    print("playerViewRef already present")

old_factory_start = """            factory = {
                PlayerView(context).apply {
                    player = exoPlayer"""
new_factory_start = """            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
                    playerViewRef = this
                    useController = true
                    controllerAutoShow = true"""
if "playerViewRef = this" not in t:
    if old_factory_start not in t:
        raise SystemExit("factory block missing")
    t = t.replace(old_factory_start, new_factory_start, 1)
    print("factory ref wired")
else:
    if "useController = true" not in t:
        t = t.replace(
            "playerViewRef = this\n",
            "playerViewRef = this\n                    useController = true\n                    controllerAutoShow = true\n",
            1,
        )
    print("factory ref already wired")

# Remove previous always-on full screen tap layer if present
t2 = re.sub(
    r"\n        // Toque no centro / qualquer area livre:.*?\n        Box\(\n            modifier = Modifier\n                \.fillMaxSize\(\)\n                \.pointerInput\(Unit\) \{\n                    detectTapGestures\([\s\S]*?\n                    \)\n                \},\n        \)\n",
    "\n",
    t,
    count=1,
)
if t2 != t:
    print("removed old always-on tap layer")
    t = t2

marker = """            update = { v -> v.resizeMode = aspectMode.resizeMode },
        )

        // Zonas de gesto: 28% esquerda = brilho, 28% direita = volume"""

tap_layer = """            update = { v -> v.resizeMode = aspectMode.resizeMode },
        )

        // So quando o menu esta oculto: toque mostra controles/timeline
        // (nao fica por cima da barra de progresso quando visivel)
        if (!controlsVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                val pv = playerViewRef
                                if (pv != null) {
                                    pv.showController()
                                }
                                controlsVisible = true
                            },
                        )
                    },
            )
        }

        // Zonas de gesto: 28% esquerda = brilho, 28% direita = volume"""

if "So quando o menu esta oculto" in t:
    print("conditional tap layer already present")
elif marker not in t:
    raise SystemExit("insert marker missing")
else:
    t = t.replace(marker, tap_layer, 1)
    print("conditional tap layer inserted")

if "controllerShowTimeoutMs = 3500" in t:
    t = t.replace("controllerShowTimeoutMs = 3500", "controllerShowTimeoutMs = 5000", 1)
    print("timeout 5000")

if "controllerHideOnTouch = true" in t:
    t = t.replace("controllerHideOnTouch = true", "controllerHideOnTouch = false", 1)
    print("hideOnTouch false")

p.write_text(t)
print("size", p.stat().st_size)
for s in ["playerViewRef", "detectTapGestures", "showController", "So quando o menu esta oculto"]:
    assert s in t, s
print("ok")
