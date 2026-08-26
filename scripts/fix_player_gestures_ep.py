#!/usr/bin/env python3
"""Restore volume/brightness gestures and rename Episodios chip to Proximo."""
from pathlib import Path

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt")
t = p.read_text()
if "fun NativePlayer" not in t:
    raise SystemExit("PlayerScreen broken")

# --- imports ---
def ensure_import(needle: str, insert_after: str, block: str):
    global t
    if needle not in t:
        if insert_after not in t:
            raise SystemExit(f"import anchor missing: {insert_after}")
        t = t.replace(insert_after, insert_after + block, 1)

ensure_import("import android.media.AudioManager", "import android.view.WindowManager\n", "import android.media.AudioManager\n")
ensure_import(
    "import androidx.compose.foundation.gestures.detectVerticalDragGestures",
    "import androidx.compose.foundation.background\n",
    "import androidx.compose.foundation.gestures.detectVerticalDragGestures\n",
)
ensure_import(
    "import androidx.compose.ui.input.pointer.pointerInput",
    "import androidx.compose.ui.Modifier\n",
    "import androidx.compose.ui.input.pointer.pointerInput\n",
)
ensure_import(
    "import androidx.compose.foundation.layout.fillMaxHeight",
    "import androidx.compose.foundation.layout.Box\n",
    "import androidx.compose.foundation.layout.fillMaxHeight\nimport androidx.compose.foundation.layout.size\nimport androidx.compose.foundation.layout.Spacer\nimport androidx.compose.foundation.layout.height\n",
)
if "import androidx.compose.material.icons.Icons" not in t:
    t = t.replace(
        "import androidx.compose.material3.Text\n",
        "import androidx.compose.material3.Text\n"
        "import androidx.compose.material.icons.Icons\n"
        "import androidx.compose.material.icons.filled.BrightnessHigh\n"
        "import androidx.compose.material.icons.filled.VolumeUp\n"
        "import androidx.compose.material3.Icon\n",
        1,
    )
if "import kotlinx.coroutines.Job" not in t:
    t = t.replace("import kotlinx.coroutines.delay\n", "import kotlinx.coroutines.Job\nimport kotlinx.coroutines.delay\n", 1)

# --- state after isLoadingNext ---
if "gestureKind" not in t:
    anchor = '    var showNextPrompt by remember { mutableStateOf(false) }\n'
    if anchor not in t:
        raise SystemExit("showNextPrompt anchor missing")
    state = '''    var showNextPrompt by remember { mutableStateOf(false) }

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
'''
    t = t.replace(anchor, state, 1)
    print("gesture state added")
else:
    print("gesture state already present")

# --- gesture overlay after PlayerView ---
end_marker = """            update = { v -> v.resizeMode = aspectMode.resizeMode },
        )

        if (errorMessage != null || isRecovering) {"""

if "gestureKind" in t and "detectVerticalDragGestures" in t and "Zonas de gesto" in t:
    print("gesture UI already present")
elif end_marker not in t:
    raise SystemExit("PlayerView end marker missing")
else:
    gesture_block = '''            update = { v -> v.resizeMode = aspectMode.resizeMode },
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
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = if (gestureKind == "brightness") Icons.Filled.BrightnessHigh else Icons.Filled.VolumeUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        if (gestureKind == "brightness") {
                            "Brilho ${(gestureValue * 100).toInt()}%"
                        } else {
                            "Volume ${(gestureValue * 100).toInt()}%"
                        },
                        color = Color.White,
                        fontSize = 15.sp,
                    )
                }
            }
        }

        if (errorMessage != null || isRecovering) {'''
    t = t.replace(end_marker, gesture_block, 1)
    print("gesture UI added")

# --- rename Episodios chip to Proximo (it advances episode) ---
if 'else "Episodios"' in t:
    t = t.replace('else "Episodios"', 'else "Proximo"', 1)
    print("chip renamed to Proximo")
elif 'else "Próximo"' in t or 'else "Proximo"' in t:
    print("chip already Proximo")
else:
    print("WARN: Episodios chip text not found")

p.write_text(t)
print("size", p.stat().st_size)
for s in ["gestureKind", "AudioManager.STREAM_MUSIC", "detectVerticalDragGestures", "Brilho", "Volume"]:
    assert s in t, s
print("ok")
