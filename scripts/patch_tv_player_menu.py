#!/usr/bin/env python3
"""Melhora menu do player Live TV: aspecto com 3 opcoes, lista de canais focavel, UI limpa."""
from pathlib import Path

p = Path("android-tv/app/src/main/java/com/streamflixvip/tv/ui/livetv/LivePlayerTvScreen.kt")
t = p.read_text()
if "aspectMenuVisible" in t:
    print("already patched")
    raise SystemExit(0)
if len(t) < 5000 or "LivePlayerTvScreen" not in t:
    raise SystemExit("LivePlayerTvScreen.kt invalid size")

old_state = (
    "    var controlsVisible by remember { mutableStateOf(true) }\n"
    "    var channelListVisible by remember { mutableStateOf(false) }\n"
    "    var aspect by remember { mutableStateOf(LiveAspect.FIT) }\n"
    "    var hideToken by remember { mutableIntStateOf(0) }\n"
)
new_state = (
    "    var controlsVisible by remember { mutableStateOf(true) }\n"
    "    var channelListVisible by remember { mutableStateOf(false) }\n"
    "    var aspectMenuVisible by remember { mutableStateOf(false) }\n"
    "    var aspect by remember { mutableStateOf(LiveAspect.FIT) }\n"
    "    var hideToken by remember { mutableIntStateOf(0) }\n"
)
if old_state not in t:
    raise SystemExit("state anchor missing")
t = t.replace(old_state, new_state, 1)

old_hide = (
    "    LaunchedEffect(controlsVisible, hideToken, channelListVisible) {\n"
    "        if (controlsVisible && !channelListVisible) {\n"
    "            if (!didInitialChipFocus) {\n"
    "                delay(40)\n"
    "                runCatching { aspectFocus.requestFocus() }\n"
    "                didInitialChipFocus = true\n"
    "            }\n"
    "            delay(6000)\n"
    "            controlsVisible = false\n"
    "            didInitialChipFocus = false\n"
    "            runCatching { rootFocus.requestFocus() }\n"
    "        }\n"
    "    }\n"
)
new_hide = (
    "    LaunchedEffect(controlsVisible, hideToken, channelListVisible, aspectMenuVisible) {\n"
    "        if (controlsVisible && !channelListVisible && !aspectMenuVisible) {\n"
    "            if (!didInitialChipFocus) {\n"
    "                delay(40)\n"
    "                runCatching { aspectFocus.requestFocus() }\n"
    "                didInitialChipFocus = true\n"
    "            }\n"
    "            delay(10000)\n"
    "            controlsVisible = false\n"
    "            aspectMenuVisible = false\n"
    "            didInitialChipFocus = false\n"
    "            runCatching { rootFocus.requestFocus() }\n"
    "        }\n"
    "    }\n"
)
if old_hide not in t:
    raise SystemExit("hide anchor missing")
t = t.replace(old_hide, new_hide, 1)

old_back = (
    "    BackHandler {\n"
    "        if (channelListVisible) channelListVisible = false else onBack()\n"
    "    }\n"
)
new_back = (
    "    BackHandler {\n"
    "        when {\n"
    "            channelListVisible -> channelListVisible = false\n"
    "            aspectMenuVisible -> aspectMenuVisible = false\n"
    "            else -> onBack()\n"
    "        }\n"
    "    }\n"
)
if old_back in t:
    t = t.replace(old_back, new_back, 1)

# controls row - use markers without bare ${ for safety in other contexts
old_row_start = "                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {\n                    LiveTvChip(Icons.Filled.AspectRatio, aspect.label, Modifier.focusRequester(aspectFocus)) {\n                        aspect = LiveAspect.entries[(aspect.ordinal + 1) % LiveAspect.entries.size]; showControls()\n                    }"
if old_row_start not in t:
    raise SystemExit("controls row start missing")

# Find end of that Row block carefully
idx = t.find(old_row_start)
if idx < 0:
    raise SystemExit("row not found")
# Find the closing of this Row - look for next unique end after Reload chip
end_marker = "                    LiveTvChip(Icons.Filled.Refresh, \"Reload\") {\n                        val cur = streamIndex; streamIndex = -1; streamIndex = cur.coerceAtLeast(0)\n                        isLoading = true; errorMessage = null; showControls()\n                    }\n                }\n"
end = t.find(end_marker, idx)
if end < 0:
    raise SystemExit("row end missing")
end = end + len(end_marker)

new_row = (
"                if (aspectMenuVisible) {\n"
"                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {\n"
"                        LiveAspect.entries.forEach { mode ->\n"
"                            val selected = aspect == mode\n"
"                            LiveTvChip(\n"
"                                Icons.Filled.AspectRatio,\n"
"                                mode.label,\n"
"                                selected = selected,\n"
"                            ) {\n"
"                                aspect = mode\n"
"                                aspectMenuVisible = false\n"
"                                showControls()\n"
"                            }\n"
"                        }\n"
"                    }\n"
"                    Spacer(Modifier.height(12.dp))\n"
"                }\n"
"\n"
"                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {\n"
"                    LiveTvChip(\n"
"                        Icons.Filled.AspectRatio,\n"
"                        if (aspectMenuVisible) \"Fechar\" else aspect.label,\n"
"                        Modifier.focusRequester(aspectFocus),\n"
"                        selected = aspectMenuVisible,\n"
"                    ) {\n"
"                        aspectMenuVisible = !aspectMenuVisible\n"
"                        showControls()\n"
"                    }\n"
"                    if (channelList.isNotEmpty()) {\n"
"                        if (channelList.size > 1) {\n"
"                            LiveTvChip(Icons.Filled.KeyboardArrowUp, \"CH+\") {\n"
"                                aspectMenuVisible = false\n"
"                                switchChannel(+1)\n"
"                            }\n"
"                            LiveTvChip(Icons.Filled.KeyboardArrowDown, \"CH-\") {\n"
"                                aspectMenuVisible = false\n"
"                                switchChannel(-1)\n"
"                            }\n"
"                        }\n"
"                        LiveTvChip(Icons.Filled.List, \"Lista\") {\n"
"                            aspectMenuVisible = false\n"
"                            channelListVisible = true\n"
"                            showControls()\n"
"                        }\n"
"                    }\n"
"                    if (activeStreams.size > 1) {\n"
"                        LiveTvChip(\n"
"                            Icons.Filled.SwapHoriz,\n"
"                            \"Fonte \" + (streamIndex + 1).toString() + \"/\" + activeStreams.size.toString(),\n"
"                        ) {\n"
"                            streamIndex = (streamIndex + 1) % activeStreams.size\n"
"                            isLoading = true\n"
"                            errorMessage = null\n"
"                            showControls()\n"
"                        }\n"
"                    }\n"
"                    LiveTvChip(Icons.Filled.Refresh, \"Reload\") {\n"
"                        val cur = streamIndex\n"
"                        streamIndex = -1\n"
"                        streamIndex = cur.coerceAtLeast(0)\n"
"                        isLoading = true\n"
"                        errorMessage = null\n"
"                        showControls()\n"
"                    }\n"
"                }\n"
)
t = t[:idx] + new_row + t[end:]

old_chip = (
    "@Composable\n"
    "private fun LiveTvChip(\n"
    "    icon: androidx.compose.ui.graphics.vector.ImageVector,\n"
    "    label: String,\n"
    "    modifier: Modifier = Modifier,\n"
    "    onClick: () -> Unit,\n"
    ") {\n"
    "    Surface(\n"
    "        onClick = onClick,\n"
    "        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(24.dp)),\n"
    "        colors = ClickableSurfaceDefaults.colors(\n"
    "            containerColor = Color.White.copy(alpha = 0.12f),\n"
    "            focusedContainerColor = Color(0xFF00E5FF).copy(alpha = 0.35f),\n"
    "        ),\n"
)
new_chip = (
    "@Composable\n"
    "private fun LiveTvChip(\n"
    "    icon: androidx.compose.ui.graphics.vector.ImageVector,\n"
    "    label: String,\n"
    "    modifier: Modifier = Modifier,\n"
    "    selected: Boolean = false,\n"
    "    onClick: () -> Unit,\n"
    ") {\n"
    "    Surface(\n"
    "        onClick = onClick,\n"
    "        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(24.dp)),\n"
    "        colors = ClickableSurfaceDefaults.colors(\n"
    "            containerColor = if (selected) Color(0xFF00E5FF).copy(alpha = 0.28f) else Color.White.copy(alpha = 0.12f),\n"
    "            focusedContainerColor = Color(0xFF00E5FF).copy(alpha = 0.40f),\n"
    "        ),\n"
)
if old_chip not in t:
    raise SystemExit("chip signature missing")
t = t.replace(old_chip, new_chip, 1)

old_list_focus = (
    "            LaunchedEffect(currentIndex) {\n"
    "                runCatching { listState.scrollToItem(currentIndex.coerceIn(0, channelList.lastIndex)) }\n"
    "                delay(80); runCatching { firstFocus.requestFocus() }\n"
    "            }\n"
)
new_list_focus = (
    "            LaunchedEffect(channelListVisible, currentIndex) {\n"
    "                if (!channelListVisible) return@LaunchedEffect\n"
    "                runCatching { listState.scrollToItem(currentIndex.coerceIn(0, channelList.lastIndex.coerceAtLeast(0))) }\n"
    "                delay(120)\n"
    "                runCatching { firstFocus.requestFocus() }\n"
    "            }\n"
)
if old_list_focus in t:
    t = t.replace(old_list_focus, new_list_focus, 1)
else:
    print("WARN list focus not found")

p.write_text(t)
print("patched", len(t))
assert "aspectMenuVisible" in t
assert "selected: Boolean" in t
print("ok")
