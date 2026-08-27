#!/usr/bin/env python3
"""Garante que toque na tela mostra controles + timeline do player."""
from pathlib import Path

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt")
t = p.read_text()
if "fun NativePlayer" not in t:
    raise SystemExit("PlayerScreen broken")

# imports para tap
if "import androidx.compose.foundation.gestures.detectTapGestures" not in t:
    t = t.replace(
        "import androidx.compose.foundation.gestures.detectVerticalDragGestures\n",
        "import androidx.compose.foundation.gestures.detectVerticalDragGestures\n"
        "import androidx.compose.foundation.gestures.detectTapGestures\n",
        1,
    )

# playerViewRef state
if "playerViewRef" not in t:
    anchor = "    var gestureHideJob by remember { mutableStateOf<Job?>(null) }\n"
    if anchor not in t:
        # fallback near controlsVisible
        anchor = "    var controlsVisible by remember { mutableStateOf(false) }\n"
        if anchor not in t:
            raise SystemExit("no anchor for playerViewRef")
        t = t.replace(
            anchor,
            anchor + "    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }\n",
            1,
        )
    else:
        t = t.replace(
            anchor,
            anchor + "    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }\n",
            1,
        )
    print("playerViewRef added")
else:
    print("playerViewRef already present")

# Save ref in factory
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
    print("factory ref already wired")

# Full-screen tap layer AFTER PlayerView, BEFORE gesture zones — toggles controls
# Insert right after AndroidView closing if not present
marker = """            update = { v -> v.resizeMode = aspectMode.resizeMode },
        )

        // Zonas de gesto:"""

tap_layer = """            update = { v -> v.resizeMode = aspectMode.resizeMode },
        )

        // Toque no centro / qualquer area livre: mostra ou esconde controles + timeline
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            val pv = playerViewRef
                            if (pv != null) {
                                if (pv.isControllerFullyVisible) {
                                    pv.hideController()
                                    controlsVisible = false
                                } else {
                                    pv.showController()
                                    controlsVisible = true
                                }
                            } else {
                                controlsVisible = !controlsVisible
                            }
                        },
                    )
                },
        )

        // Zonas de gesto:"""

if "detectTapGestures" in t and "isControllerFullyVisible" in t:
    print("tap layer already present")
elif marker not in t:
    # try without gesture comment
    marker2 = """            update = { v -> v.resizeMode = aspectMode.resizeMode },
        )

        // Zonas de gesto: 28% esquerda = brilho, 28% direita = volume"""
    if marker2 in t:
        t = t.replace(marker2, tap_layer.replace("// Zonas de gesto:", "// Zonas de gesto: 28% esquerda = brilho, 28% direita = volume"), 1)
        print("tap layer inserted (marker2)")
    else:
        raise SystemExit("insert marker missing")
else:
    t = t.replace(marker, tap_layer, 1)
    print("tap layer inserted")

# Bottom bar: show when controlsVisible — already does
# Also make bottom chips show with a slightly longer native timeout
if "controllerShowTimeoutMs = 3500" in t:
    t = t.replace("controllerShowTimeoutMs = 3500", "controllerShowTimeoutMs = 5000", 1)
    print("timeout 5000")

# controllerHideOnTouch = false so first tap always shows via our layer logic
if "controllerHideOnTouch = true" in t:
    t = t.replace("controllerHideOnTouch = true", "controllerHideOnTouch = false", 1)
    print("hideOnTouch false")

p.write_text(t)
print("size", p.stat().st_size)
for s in ["playerViewRef", "detectTapGestures", "showController", "isControllerFullyVisible"]:
    assert s in t, s
print("ok")
