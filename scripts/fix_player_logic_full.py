#!/usr/bin/env python3
"""
Corrige logica do player:
- Toque com menu fechado: abre (tela toda)
- Com menu aberto: PlayerView nativo cuida de pause/seek; toque no video fecha (hideOnTouch)
- Overlay Compose NAO fica no meio quando menu aberto (nao rouba pause)
- Legenda: persiste preferencia e nao desliga sozinha
"""
from pathlib import Path
import re

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt")
t = p.read_text()
if "fun NativePlayer" not in t:
    raise SystemExit("PlayerScreen broken")

# --- 1) selectSubtitle: persistir preferencia ---
old_sel = '''    fun selectSubtitle(option: TrackOption?) {
        onlineSubtitleApplied = false
        trackSelector.parameters = if (option == null) {
            selectedSubtitleLabel = "Desligada"
            trackSelector.parameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        } else {
            selectedSubtitleLabel = "Stream: ${option.label}"
            trackSelector.parameters.buildUpon().setOverrideForType(TrackSelectionOverride(option.group, option.trackIndex)).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
        }
    }'''

new_sel = '''    fun selectSubtitle(option: TrackOption?) {
        onlineSubtitleApplied = false
        trackSelector.parameters = if (option == null) {
            selectedSubtitleLabel = "Desligada"
            persistSubtitleKey("off")
            trackSelector.parameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        } else {
            selectedSubtitleLabel = "Stream: ${option.label}"
            persistSubtitleKey(option.label)
            trackSelector.parameters.buildUpon().setOverrideForType(TrackSelectionOverride(option.group, option.trackIndex)).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
        }
    }'''

if "persistSubtitleKey(\"off\")" not in t:
    if old_sel not in t:
        # try without exact whitespace - regex
        m = re.search(r"    fun selectSubtitle\(option: TrackOption\?\) \{[\s\S]*?\n    \}\n\n    suspend fun searchOnlineSubtitles", t)
        if not m:
            raise SystemExit("selectSubtitle block not found")
        t = t[:m.start()] + new_sel + "\n\n    suspend fun searchOnlineSubtitles" + t[m.end():]
        # wait, m.end already includes searchOnlineSubtitles start - fix
        raise SystemExit("use replace path")
    else:
        t = t.replace(old_sel, new_sel, 1)
        print("selectSubtitle persist fixed")
else:
    print("selectSubtitle already persists")

# Fix if first path failed style - ensure persist lines exist
if 'persistSubtitleKey("off")' not in t and "fun selectSubtitle" in t:
    t = t.replace(
        'selectedSubtitleLabel = "Desligada"\n            trackSelector.parameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT)',
        'selectedSubtitleLabel = "Desligada"\n            persistSubtitleKey("off")\n            trackSelector.parameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT)',
        1,
    )
    t = t.replace(
        'selectedSubtitleLabel = "Stream: ${option.label}"\n            trackSelector.parameters.buildUpon().setOverrideForType(TrackSelectionOverride(option.group, option.trackIndex))',
        'selectedSubtitleLabel = "Stream: ${option.label}"\n            persistSubtitleKey(option.label)\n            trackSelector.parameters.buildUpon().setOverrideForType(TrackSelectionOverride(option.group, option.trackIndex))',
        1,
    )
    print("selectSubtitle persist via line inject")

# --- 2) reapplyTrackPreferences: nao sobrescrever legenda online; label Stream: ---
old_reapply_sub = '''        val subPref = preferredSubtitleKey
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

new_reapply_sub = '''        if (onlineSubtitleApplied) {
            // legenda online ativa: nao desliga/reaplica faixa do stream
        } else {
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

if "if (onlineSubtitleApplied)" not in t:
    if old_reapply_sub in t:
        t = t.replace(old_reapply_sub, new_reapply_sub, 1)
        print("reapply online guard added")
    else:
        print("WARN: reapply block pattern mismatch")
else:
    print("reapply guard already present")

# applyOnlineSubtitle: persist key so reapply doesn't fight
if 'persistSubtitleKey("online")' not in t and "onlineSubtitleApplied = true" in t:
    t = t.replace(
        "onlineSubtitleApplied = true\n            settingsPanel = SettingsPanel.MAIN",
        'onlineSubtitleApplied = true\n            persistSubtitleKey("online")\n            settingsPanel = SettingsPanel.MAIN',
        1,
    )
    print("online persist added")

# --- 3) Touch layer: only SHOW when hidden; when visible let PlayerView work ---
# Remove any existing toggle/tap overlay blocks
t = re.sub(
    r"\n        // Toque em qualquer lugar: abre/fecha[\s\S]*?pointerInput\(controlsVisible\) \{[\s\S]*?\n        \)\n\n",
    "\n",
    t,
    count=1,
)
t = re.sub(
    r"\n        // So quando o menu esta oculto:[\s\S]*?if \(!controlsVisible\) \{[\s\S]*?\n        \}\n\n",
    "\n",
    t,
    count=1,
)
t = re.sub(
    r"\n        // Toque no centro / qualquer area livre:[\s\S]*?Box\([\s\S]*?detectTapGestures[\s\S]*?\n        \)\n\n",
    "\n",
    t,
    count=1,
)

marker = "        // Zonas de gesto: 28% esquerda = brilho, 28% direita = volume"
if marker not in t:
    raise SystemExit("gesture marker missing")

if "SHOW_ONLY_WHEN_HIDDEN" not in t:
    show_layer = '''        // SHOW_ONLY_WHEN_HIDDEN: toque so abre; com menu aberto o PlayerView nativo
        // recebe pause/seek. Fechar = timeout do Exo ou toque no video (hideOnTouch).
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
    t = t.replace(marker, show_layer + marker, 1)
    print("show-only overlay inserted")
else:
    print("show-only overlay already present")

# PlayerView: hideOnTouch true so second tap on video (native) hides without stealing pause buttons
if "controllerHideOnTouch = false" in t:
    t = t.replace("controllerHideOnTouch = false", "controllerHideOnTouch = true", 1)
    print("hideOnTouch true (nativo fecha no toque do video)")

# Listener stays syncing controlsVisible

p.write_text(t)
print("size", p.stat().st_size)

# validations
assert "SHOW_ONLY_WHEN_HIDDEN" in t
assert 'persistSubtitleKey("off")' in t or "persistSubtitleKey(\"off\")" in t or 'persistSubtitleKey("off")' in t
# kotlin source has normal quotes
assert "persistSubtitleKey(" in t
assert "controllerHideOnTouch = true" in t
print("ok")
