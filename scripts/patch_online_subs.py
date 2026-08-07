#!/usr/bin/env python3
from pathlib import Path
import json

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt")
t = p.read_text()
assert "fun selectSubtitle" in t

if "fetchOnlineSubtitles" in t and "Online (OpenSubtitles)" in t:
    print("online subs already present")
    raise SystemExit(0)

if "import androidx.media3.common.MimeTypes" not in t:
    t = t.replace(
        "import androidx.media3.common.MediaItem\n",
        "import androidx.media3.common.MediaItem\nimport androidx.media3.common.MimeTypes\n",
    )
if "import android.net.Uri" not in t:
    t = t.replace(
        "import android.content.Intent\n",
        "import android.content.Intent\nimport android.net.Uri\n",
    )
if "import java.io.File" not in t:
    t = t.replace(
        "import kotlinx.coroutines.DelicateCoroutinesApi\n",
        "import java.io.File\nimport kotlinx.coroutines.DelicateCoroutinesApi\n",
    )

old_state = '    var selectedSubtitleLabel by remember { mutableStateOf("Desligada") }\n'
new_state = "    var selectedSubtitleLabel by remember { mutableStateOf(\"Desligada\") }\n    var onlineSubtitles by remember { mutableStateOf(listOf<com.streamflixvip.app.network.SubtitleSearchItem>()) }\n    var onlineSubsLoading by remember { mutableStateOf(false) }\n    var onlineSubsError by remember { mutableStateOf<String?>(null) }\n    var onlineSubsFetched by remember { mutableStateOf(false) }\n    var applyingOnlineSub by remember { mutableStateOf(false) }\n"
if old_state not in t:
    raise SystemExit("state not found")
t = t.replace(old_state, new_state, 1)

old_select = """    fun selectSubtitle(option: TrackOption?) {
        trackSelector.parameters = if (option == null) {
            selectedSubtitleLabel = \"Desligada\"
            trackSelector.parameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        } else {
            selectedSubtitleLabel = option.label
            trackSelector.parameters.buildUpon().setOverrideForType(TrackSelectionOverride(option.group, option.trackIndex)).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
        }
    }
"""
# Load from artifact file via embedded json in this update - use simpler approach:
# re-read was tested - write the tested script from artifacts using run in CI

# Actually the tested script is at artifacts - push binary-safe by reading the file content that was tested
raise SystemExit('use full script from artifacts')
