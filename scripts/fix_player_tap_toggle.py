#!/usr/bin/env python3
"""Toque em qualquer lugar: abre menu; toque de novo (area do video): fecha."""
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

# Remove old conditional-only tap layer(s)
t = re.sub(
    r"\n        // So quando o menu esta oculto:[\s\S]*?if \(!controlsVisible\) \{\n            Box\([\s\S]*?\n            \)\n        \}\n",
    "\n",
    t,
    count=1,
)
t = re.sub(
    r"\n        // Toque no centro / qualquer area livre:[\s\S]*?Box\([\s\S]*?detectTapGestures[\s\S]*?\n        \)\n",
    "\n",
    t,
    count=1,
)

# Ensure playerViewRef + factory wiring
if "playerViewRef" not in t:
    raise SystemExit("playerViewRef missing — run previous fix first")

# Replace ControllerVisibilityListener to not fight manual toggle as hard
old_listener = """                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controlsVisible = visibility == android.view.View.VISIBLE
                            hideNativeSettingsButton()
                        },
                    )"""
new_listener = """                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            // Sincroniza quando o Exo esconde por timeout; nao forca false no show
                            if (visibility == android.view.View.VISIBLE) {
                                controlsVisible = true
                            } else if (visibility == android.view.View.GONE) {
                                controlsVisible = false
                            }
                            hideNativeSettingsButton()
                        },
                    )"""
if old_listener in t:
    t = t.replace(old_listener, new_listener, 1)
    print("listener updated")

# Insert robust tap toggle BEFORE gesture zones (gestures stay on top on the sides)
marker = "        // Zonas de gesto: 28% esquerda = brilho, 28% direita = volume"
if marker not in t:
    raise SystemExit("gesture marker missing")

if "togglePlayerControls" in t or "Toque em qualquer lugar: abre/fecha" in t:
    print("toggle already present")
else:
    block = '''        // Toque em qualquer lugar: abre/fecha menu + timeline
        // Com menu visivel, deixa livre o rodape (seek + chips) e o topo (Voltar)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = if (controlsVisible) 56.dp else 0.dp,
                    bottom = if (controlsVisible) 96.dp else 0.dp,
                )
                .pointerInput(controlsVisible) {
                    detectTapGestures(
                        onTap = {
                            if (controlsVisible) {
                                playerViewRef?.hideController()
                                controlsVisible = false
                            } else {
                                playerViewRef?.showController()
                                controlsVisible = true
                            }
                        },
                    )
                },
        )

'''
    t = t.replace(marker, block + marker, 1)
    print("toggle layer inserted")

# controllerHideOnTouch false, timeout ok
if "controllerHideOnTouch = true" in t:
    t = t.replace("controllerHideOnTouch = true", "controllerHideOnTouch = false", 1)

p.write_text(t)
print("size", p.stat().st_size)
assert "Toque em qualquer lugar: abre/fecha" in t
assert "detectTapGestures" in t
print("ok")
