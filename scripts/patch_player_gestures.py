#!/usr/bin/env python3
"""Add brightness (left) + volume (right) vertical swipe gestures to PlayerScreen."""
from pathlib import Path

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt")
t = p.read_text()
assert "package com.streamflixvip.app.ui.player" in t
assert len(t) > 20000

if "gestureKind" in t and "AudioManager.STREAM_MUSIC" in t:
    print("gestures already present")
    raise SystemExit(0)

# --- imports ---
if "import android.media.AudioManager" not in t:
    t = t.replace(
        "import android.view.WindowManager\n",
        "import android.media.AudioManager\nimport android.view.WindowManager\n",
    )
if "import androidx.compose.foundation.gestures.detectVerticalDragGestures" not in t:
    t = t.replace(
        "import androidx.compose.foundation.background\n",
        "import androidx.compose.foundation.background\nimport androidx.compose.foundation.gestures.detectVerticalDragGestures\n",
    )
if "import androidx.compose.ui.input.pointer.pointerInput" not in t:
    t = t.replace(
        "import androidx.compose.ui.Modifier\n",
        "import androidx.compose.ui.Modifier\nimport androidx.compose.ui.input.pointer.pointerInput\n",
    )
if "import androidx.compose.material.icons.Icons" not in t:
    t = t.replace(
        "import androidx.compose.material3.Text\n",
        "import androidx.compose.material3.Text\n"
        "import androidx.compose.material.icons.Icons\n"
        "import androidx.compose.material.icons.filled.BrightnessHigh\n"
        "import androidx.compose.material.icons.filled.VolumeUp\n"
        "import androidx.compose.material3.Icon\n",
    )
if "import kotlinx.coroutines.Job" not in t:
    t = t.replace(
        "import kotlinx.coroutines.delay\n",
        "import kotlinx.coroutines.Job\nimport kotlinx.coroutines.delay\n",
    )

# --- state ---
old_state = "    var nextCountdown by remember { mutableStateOf(10) }\n"
new_state = """    var nextCountdown by remember { mutableStateOf(10) }

    // Gestos: esquerda = brilho, direita = volume
    val audioManager = remember {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var brightnessLevel by remember {
        val cur = (context as? android.app.Activity)?.window?.attributes?.screenBrightness ?: -1f
        mutableStateOf(if (cur in 0f..1f) cur else 0.5f)
    }
    var volumeLevel by remember {
        mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume)
    }
    var gestureKind by remember { mutableStateOf<String?>(null) }
    var gestureValue by remember { mutableStateOf(0f) }
    var gestureHideJob by remember { mutableStateOf<Job?>(null) }
"""
if old_state not in t:
    raise SystemExit("state anchor not found")
t = t.replace(old_state, new_state, 1)

end_marker = """            update = { v -> v.resizeMode = aspectMode.resizeMode },
        )

        if (errorMessage != null || isRecovering) {"""

gesture_block = """            update = { v -> v.resizeMode = aspectMode.resizeMode },
        )

        // Zonas de gesto: 28% esquerda = brilho, 28% direita = volume
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.28f)
                .align(Alignment.CenterStart)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            gestureHideJob?.cancel()
                            gestureKind = "brightness"
                            gestureValue = brightnessLevel
                        },
                        onVerticalDrag = { _, dragAmount ->
                            val delta = -dragAmount / size.height.toFloat()
                            brightnessLevel = (brightnessLevel + delta).coerceIn(0.01f, 1f)
                            gestureValue = brightnessLevel
                            val act = context as? android.app.Activity
                            act?.window?.let { w ->
                                val lp = w.attributes
                                lp.screenBrightness = brightnessLevel
                                w.attributes = lp
                            }
                        },
                        onDragEnd = {
                            gestureHideJob?.cancel()
                            gestureHideJob = MainScope().launch {
                                delay(900)
                                gestureKind = null
                            }
                        },
                        onDragCancel = { gestureKind = null },
                    )
                },
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.28f)
                .align(Alignment.CenterEnd)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            gestureHideJob?.cancel()
                            gestureKind = "volume"
                            gestureValue = volumeLevel
                        },
                        onVerticalDrag = { _, dragAmount ->
                            val delta = -dragAmount / size.height.toFloat()
                            volumeLevel = (volumeLevel + delta).coerceIn(0f, 1f)
                            gestureValue = volumeLevel
                            val vol = (volumeLevel * maxVolume).toInt().coerceIn(0, maxVolume)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0)
                        },
                        onDragEnd = {
                            gestureHideJob?.cancel()
                            gestureHideJob = MainScope().launch {
                                delay(900)
                                gestureKind = null
                            }
                        },
                        onDragCancel = { gestureKind = null },
                    )
                },
        )

        if (gestureKind != null) {
            Surface(
                color = Color.Black.copy(alpha = 0.72f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = if (gestureKind == "brightness") Icons.Filled.BrightnessHigh else Icons.Filled.VolumeUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${(gestureValue * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 16.sp,
                    )
                }
            }
        }

        if (errorMessage != null || isRecovering) {"""

if end_marker not in t:
    raise SystemExit("end marker not found")
t = t.replace(end_marker, gesture_block, 1)

if "fillMaxHeight()" in t and "import androidx.compose.foundation.layout.fillMaxHeight" not in t:
    if "import androidx.compose.foundation.layout.*" not in t:
        t = t.replace(
            "import androidx.compose.foundation.layout.Box\n",
            "import androidx.compose.foundation.layout.Box\n"
            "import androidx.compose.foundation.layout.fillMaxHeight\n"
            "import androidx.compose.foundation.layout.size\n",
        )

p.write_text(t)
print("patched", len(t))
for s in ["gestureKind", "AudioManager.STREAM_MUSIC", "BrightnessHigh", "detectVerticalDragGestures", "screenBrightness"]:
    assert s in t, s
print("all gesture features present")
