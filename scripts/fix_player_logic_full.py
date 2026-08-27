#!/usr/bin/env python3
from pathlib import Path
import re

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt")
t = p.read_text()
if "fun NativePlayer" not in t:
    raise SystemExit("PlayerScreen broken")

# 1) selectSubtitle persist
if 'persistSubtitleKey("off")' not in t:
    t2 = t.replace(
        'selectedSubtitleLabel = "Desligada"\n            trackSelector.parameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT)',
        'selectedSubtitleLabel = "Desligada"\n            persistSubtitleKey("off")\n            trackSelector.parameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT)',
        1,
    )
    t2 = t2.replace(
        'selectedSubtitleLabel = "Stream: ${option.label}"\n            trackSelector.parameters.buildUpon().setOverrideForType(TrackSelectionOverride(option.group, option.trackIndex))',
        'selectedSubtitleLabel = "Stream: ${option.label}"\n            persistSubtitleKey(option.label)\n            trackSelector.parameters.buildUpon().setOverrideForType(TrackSelectionOverride(option.group, option.trackIndex))',
        1,
    )
    if t2 == t:
        raise SystemExit("failed to inject persistSubtitleKey")
    t = t2
    print("selectSubtitle persist OK")
else:
    print("selectSubtitle already persists")

# 2) reapply: skip if online subtitle applied; use Stream: label
if "if (onlineSubtitleApplied)" not in t:
    old = '''        val subPref = preferredSubtitleKey
        if (subPref.isBlank() || subPref == "off") {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            selectedSubtitleLabel = "Desligada"
        } else {
            val match = subs.firstOrNull { trackMatchesPref(it.label, subPref) }
            if (match != null) {
                builder.setOverrideForType(TrackSelectionOverride(match.group, match.trackIndex))
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                selectedSubtitleLabel = match.label
            }
        }'''
    new = '''        if (!onlineSubtitleApplied) {
            val subPref = preferredSubtitleKey
            if (subPref.isBlank() || subPref == "off") {
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                selectedSubtitleLabel = "Desligada"
            } else {
                val match = subs.firstOrNull { trackMatchesPref(it.label, subPref) }
                if (match != null) {
                    builder.setOverrideForType(TrackSelectionOverride(match.group, match.trackIndex))
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    selectedSubtitleLabel = "Stream: ${match.label}"
                }
            }
        }'''
    if old not in t:
        raise SystemExit("reapply block not found")
    t = t.replace(old, new, 1)
    print("reapply guard OK")
else:
    print("reapply guard already present")

if 'persistSubtitleKey("online")' not in t and "onlineSubtitleApplied = true" in t:
    t = t.replace(
        "onlineSubtitleApplied = true\n            settingsPanel = SettingsPanel.MAIN",
        'onlineSubtitleApplied = true\n            persistSubtitleKey("online")\n            settingsPanel = SettingsPanel.MAIN',
        1,
    )
    print("online persist OK")

# 3) Remove broken toggle overlays
for pat in [
    r"\n        // Toque em qualquer lugar: abre/fecha[\s\S]*?\.pointerInput\(controlsVisible\) \{[\s\S]*?\n        \)\n",
    r"\n        // So quando o menu esta oculto:[\s\S]*?if \(!controlsVisible\) \{[\s\S]*?\n        \}\n",
    r"\n        // SHOW_ONLY_WHEN_HIDDEN:[\s\S]*?if \(!controlsVisible && settingsPanel == SettingsPanel\.NONE\) \{[\s\S]*?\n        \}\n",
]:
    t2 = re.sub(pat, "\n", t, count=1)
    if t2 != t:
        print("removed old overlay block")
        t = t2

marker = "        // Zonas de gesto: 28% esquerda = brilho, 28% direita = volume"
if marker not in t:
    raise SystemExit("gesture marker missing")

if "SHOW_ONLY_WHEN_HIDDEN" not in t:
    layer = '''        // SHOW_ONLY_WHEN_HIDDEN: toque so ABRE o menu.
        // Com menu aberto, PlayerView nativo cuida de pause/seek/timeline.
        // Fechar: timeout do Exo ou toque no video (controllerHideOnTouch).
        if (!controlsVisible && settingsPanel == SettingsPanel.NONE) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                playerViewRef?.showController()
                                controlsVisible = true
                            },
                        )
                    },
            )
        }

'''
    t = t.replace(marker, layer + marker, 1)
    print("show-only overlay OK")
else:
    print("show-only already present")

if "controllerHideOnTouch = false" in t:
    t = t.replace("controllerHideOnTouch = false", "controllerHideOnTouch = true", 1)
    print("hideOnTouch true")
elif "controllerHideOnTouch = true" in t:
    print("hideOnTouch already true")
else:
    print("WARN: hideOnTouch line missing")

p.write_text(t)
print("size", p.stat().st_size)
assert "SHOW_ONLY_WHEN_HIDDEN" in t
assert "persistSubtitleKey(" in t
assert "controllerHideOnTouch = true" in t
print("ok")
