#!/usr/bin/env python3
from pathlib import Path

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt")
t = p.read_text()
assert "gestureKind" in t

if "awaitEachGesture" in t and "playerViewRef" in t:
    print("tap+gesture already fixed")
    raise SystemExit(0)

if "import androidx.compose.foundation.gestures.awaitEachGesture" not in t:
    t = t.replace(
        "import androidx.compose.foundation.gestures.detectVerticalDragGestures\n",
        "import androidx.compose.foundation.gestures.awaitEachGesture\n"
        "import androidx.compose.foundation.gestures.awaitFirstDown\n"
        "import androidx.compose.foundation.gestures.drag\n"
        "import androidx.compose.foundation.gestures.detectVerticalDragGestures\n",
    )
if "import androidx.compose.ui.input.pointer.positionChange" not in t:
    t = t.replace(
        "import androidx.compose.ui.input.pointer.pointerInput\n",
        "import androidx.compose.ui.input.pointer.pointerInput\nimport androidx.compose.ui.input.pointer.positionChange\n",
    )
if "import kotlin.math.abs" not in t:
    t = t.replace(
        "import kotlinx.coroutines.DelicateCoroutinesApi\n",
        "import kotlin.math.abs\nimport kotlinx.coroutines.DelicateCoroutinesApi\n",
    )

if "playerViewRef" not in t:
    t = t.replace(
        "    var gestureHideJob by remember { mutableStateOf<Job?>(null) }\n",
        "    var gestureHideJob by remember { mutableStateOf<Job?>(null) }\n"
        "    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }\n",
    )

old_factory = (
    "                PlayerView(context).apply {\n"
    "                    player = exoPlayer\n"
    "                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)\n"
    "                    controllerShowTimeoutMs = 2800\n"
    "                    controllerHideOnTouch = true\n"
    "                    post {\n"
    "                        showController()\n"
    "                        hideNativeSettingsButtonSafe(this)\n"
    "                    }"
)
new_factory = (
    "                PlayerView(context).apply {\n"
    "                    player = exoPlayer\n"
    "                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)\n"
    "                    controllerShowTimeoutMs = 2800\n"
    "                    controllerHideOnTouch = true\n"
    "                    playerViewRef = this\n"
    "                    post {\n"
    "                        showController()\n"
    "                        hideNativeSettingsButtonSafe(this)\n"
    "                    }"
)
if old_factory not in t:
    raise SystemExit("factory block not found")
t = t.replace(old_factory, new_factory, 1)

start = t.find("        // Zonas de gesto: 28% esquerda = brilho, 28% direita = volume")
end = t.find("        if (errorMessage != null || isRecovering) {")
if start < 0 or end < 0:
    raise SystemExit(f"gesture bounds start={start} end={end}")

new_gestures = "        // Zonas de gesto: 28% esquerda = brilho, 28% direita = volume\n        // Toque simples = mostra/oculta controles; deslize vertical = ajusta\n        Box(\n            modifier = Modifier\n                .fillMaxHeight()\n                .fillMaxWidth(0.28f)\n                .align(Alignment.CenterStart)\n                .pointerInput(brightnessLevel) {\n                    awaitEachGesture {\n                        val down = awaitFirstDown(requireUnconsumed = false)\n                        var totalY = 0f\n                        var dragging = false\n                        val startLevel = brightnessLevel\n                        drag(down.id) { change ->\n                            totalY += change.positionChange().y\n                            if (!dragging && abs(totalY) > 16f) {\n                                dragging = true\n                                gestureHideJob?.cancel()\n                                gestureKind = \"brightness\"\n                            }\n                            if (dragging) {\n                                change.consume()\n                                val delta = -totalY / size.height.toFloat()\n                                brightnessLevel = (startLevel + delta).coerceIn(0.01f, 1f)\n                                gestureValue = brightnessLevel\n                                val act = context as? android.app.Activity\n                                act?.window?.let { w ->\n                                    val lp = w.attributes\n                                    lp.screenBrightness = brightnessLevel\n                                    w.attributes = lp\n                                }\n                            }\n                        }\n                        if (!dragging) {\n                            val pv = playerViewRef\n                            if (pv != null) {\n                                if (pv.isControllerFullyVisible) pv.hideController() else pv.showController()\n                            } else {\n                                controlsVisible = !controlsVisible\n                            }\n                        } else {\n                            gestureHideJob?.cancel()\n                            gestureHideJob = MainScope().launch {\n                                delay(900)\n                                gestureKind = null\n                            }\n                        }\n                    }\n                },\n        )\n        Box(\n            modifier = Modifier\n                .fillMaxHeight()\n                .fillMaxWidth(0.28f)\n                .align(Alignment.CenterEnd)\n                .pointerInput(volumeLevel, maxVolume) {\n                    awaitEachGesture {\n                        val down = awaitFirstDown(requireUnconsumed = false)\n                        var totalY = 0f\n                        var dragging = false\n                        val startLevel = volumeLevel\n                        drag(down.id) { change ->\n                            totalY += change.positionChange().y\n                            if (!dragging && abs(totalY) > 16f) {\n                                dragging = true\n                                gestureHideJob?.cancel()\n                                gestureKind = \"volume\"\n                            }\n                            if (dragging) {\n                                change.consume()\n                                val delta = -totalY / size.height.toFloat()\n                                volumeLevel = (startLevel + delta).coerceIn(0f, 1f)\n                                gestureValue = volumeLevel\n                                val vol = (volumeLevel * maxVolume).toInt().coerceIn(0, maxVolume)\n                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0)\n                            }\n                        }\n                        if (!dragging) {\n                            val pv = playerViewRef\n                            if (pv != null) {\n                                if (pv.isControllerFullyVisible) pv.hideController() else pv.showController()\n                            } else {\n                                controlsVisible = !controlsVisible\n                            }\n                        } else {\n                            gestureHideJob?.cancel()\n                            gestureHideJob = MainScope().launch {\n                                delay(900)\n                                gestureKind = null\n                            }\n                        }\n                    }\n                },\n        )\n\n        if (gestureKind != null) {\n            Surface(\n                color = Color.Black.copy(alpha = 0.72f),\n                shape = RoundedCornerShape(16.dp),\n                modifier = Modifier.align(Alignment.Center),\n            ) {\n                Column(\n                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),\n                    horizontalAlignment = Alignment.CenterHorizontally,\n                ) {\n                    Icon(\n                        imageVector = if (gestureKind == \"brightness\") Icons.Filled.BrightnessHigh else Icons.Filled.VolumeUp,\n                        contentDescription = null,\n                        tint = Color.White,\n                        modifier = Modifier.size(28.dp),\n                    )\n                    Spacer(Modifier.height(8.dp))\n                    Text(\n                        \"${(gestureValue * 100).toInt()}%\",\n                        color = Color.White,\n                        fontSize = 16.sp,\n                    )\n                }\n            }\n        }\n\n"
t = t[:start] + new_gestures + t[end:]
p.write_text(t)
print("patched", len(t))
assert "awaitEachGesture" in t
assert "playerViewRef" in t
assert "isControllerFullyVisible" in t
print("ok")
