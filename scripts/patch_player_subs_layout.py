#!/usr/bin/env python3
"""Reaplica legendas online, corrige label und e barra inferior estilo chips."""
from pathlib import Path
import re
import subprocess
import sys

# 1) Online subtitles (script ja existente)
r = subprocess.run([sys.executable, "scripts/patch_online_subtitles.py"], capture_output=True, text=True)
print(r.stdout)
print(r.stderr)
if r.returncode not in (0,):
    # already wired exits 0; real failure !=0
    if "already wired" not in (r.stdout or "") and r.returncode != 0:
        print("online patch rc", r.returncode)
        # continue anyway if already partially there

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt")
t = p.read_text()
if "fun NativePlayer" not in t:
    raise SystemExit("PlayerScreen broken")

# 2) Helper for human track labels (und -> Sem idioma)
if "fun humanTrackLabel" not in t:
    helper = '''
private fun humanTrackLabel(label: String?, language: String?, index: Int): String {
    val raw = (label ?: language ?: "").trim()
    val low = raw.lowercase()
    if (raw.isBlank() || low in setOf("und", "undefined", "null", "unknown")) {
        return "Faixa ${index + 1}"
    }
    val map = mapOf(
        "pt" to "Portugues",
        "por" to "Portugues",
        "pt-br" to "Portugues (BR)",
        "pt-pt" to "Portugues (PT)",
        "en" to "Ingles",
        "eng" to "Ingles",
        "es" to "Espanhol",
        "spa" to "Espanhol",
        "fr" to "Frances",
        "de" to "Alemao",
        "it" to "Italiano",
        "ja" to "Japones",
        "ko" to "Coreano",
        "zh" to "Chines",
        "ru" to "Russo",
    )
    map[low]?.let { return it }
    // language codes like por-BR
    val base = low.split("-", "_").firstOrNull() ?: low
    map[base]?.let { return it }
    return raw
}

'''
    t = t.replace(
        "private data class TrackOption(val label: String, val group: TrackGroup, val trackIndex: Int)\n",
        "private data class TrackOption(val label: String, val group: TrackGroup, val trackIndex: Int)\n" + helper,
        1,
    )
    print("humanTrackLabel added")

# Fix track extraction labels
old_sub = 'subtitles += TrackOption(f.label ?: f.language ?: "Faixa ${i + 1}", group.mediaTrackGroup, i)'
new_sub = 'subtitles += TrackOption(humanTrackLabel(f.label, f.language, i), group.mediaTrackGroup, i)'
if old_sub in t:
    t = t.replace(old_sub, new_sub)
    print("subtitle labels fixed")
old_aud = 'audios += TrackOption(f.label ?: f.language ?: "Faixa ${i + 1}", group.mediaTrackGroup, i)'
new_aud = 'audios += TrackOption(humanTrackLabel(f.label, f.language, i), group.mediaTrackGroup, i)'
if old_aud in t:
    t = t.replace(old_aud, new_aud)
    print("audio labels fixed")

# 3) Bottom action bar (chips) — replace bottom-end Ajustes row when present
old_bottom = '''        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 12.dp, bottom = 8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (mediaType == "tv" && currentEpisode > 0) {
                    Surface(
                        color = Color.White.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                        modifier = Modifier.clickable { if (!isLoadingNext) MainScope().launch { playNextEpisode() } },
                    ) {
                        Text(
                            if (isLoadingNext) "..." else "Proximo",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
                Surface(
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.clickable { settingsPanel = SettingsPanel.MAIN },
                ) {
                    Text("Ajustes", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                }
            }
        }'''

new_bottom = '''        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    fun chip(label: String, onClick: () -> Unit) {
                        Surface(
                            color = Color.White.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.clickable(onClick = onClick),
                        ) {
                            Text(
                                label,
                                color = Color.White,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                    // local chips as Surfaces (Compose local fun not allowed in lambda — inline)
                    Surface(color = Color.White.copy(alpha = 0.10f), shape = RoundedCornerShape(16.dp), modifier = Modifier.clickable {
                        aspectMode = AspectMode.entries[(aspectMode.ordinal + 1) % AspectMode.entries.size]
                    }) {
                        Text(aspectMode.label, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                    Surface(color = Color.White.copy(alpha = 0.10f), shape = RoundedCornerShape(16.dp), modifier = Modifier.clickable {
                        settingsPanel = SettingsPanel.SPEED
                    }) {
                        Text("${playbackSpeed}x", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                    Surface(color = Color.White.copy(alpha = 0.10f), shape = RoundedCornerShape(16.dp), modifier = Modifier.clickable {
                        settingsPanel = SettingsPanel.SUBTITLE
                    }) {
                        Text("Legendas", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                    if (audioOptions.isNotEmpty()) {
                        Surface(color = Color.White.copy(alpha = 0.10f), shape = RoundedCornerShape(16.dp), modifier = Modifier.clickable {
                            settingsPanel = SettingsPanel.AUDIO
                        }) {
                            Text("Audio", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                    Surface(color = Color.White.copy(alpha = 0.10f), shape = RoundedCornerShape(16.dp), modifier = Modifier.clickable {
                        MainScope().launch {
                            if (alternateSources.isEmpty()) loadAlternateSources()
                            if (alternateSources.isEmpty()) {
                                Toast.makeText(context, "Sem outra fonte", Toast.LENGTH_SHORT).show()
                            } else {
                                val next = alternateSources[alternateIndex % alternateSources.size]
                                alternateIndex += 1
                                reloadWithUrl(next)
                                Toast.makeText(context, "Trocando fonte…", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Text("Fontes", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                    if (mediaType == "tv" && currentEpisode > 0) {
                        Surface(
                            color = Color.White.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.30f)),
                            modifier = Modifier.clickable { if (!isLoadingNext) MainScope().launch { playNextEpisode() } },
                        ) {
                            Text(
                                if (isLoadingNext) "…" else "Episodios",
                                color = Color.White,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                    Surface(color = Color.White.copy(alpha = 0.10f), shape = RoundedCornerShape(16.dp), modifier = Modifier.clickable {
                        settingsPanel = SettingsPanel.MAIN
                    }) {
                        Text("Mais", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
        }'''

if old_bottom in t:
    t = t.replace(old_bottom, new_bottom, 1)
    print("bottom bar replaced")
elif "Legendas" in t and "BottomCenter" in t:
    print("bottom bar already present")
else:
    print("WARN: bottom bar pattern not found — online/labels still applied")

p.write_text(t)
print("size", p.stat().st_size)
checks = ["humanTrackLabel", "searchOnlineSubtitles", "Online PT-BR"]
for c in checks:
    print(c, c in t)
