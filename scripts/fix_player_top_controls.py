#!/usr/bin/env python3
from pathlib import Path

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt")
t = p.read_text()

idx = t.find("modifier = Modifier.align(Alignment.TopStart).statusBarsPadding()")
if idx < 0:
    raise SystemExit("top controls not found")
av = t.rfind("AnimatedVisibility(", 0, idx)
next_av = t.find("AnimatedVisibility(", av + 20)
if next_av < 0:
    raise SystemExit("next AnimatedVisibility not found")

new_block = """AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 12.dp, end = 16.dp, top = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = Color.White.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                    modifier = Modifier.size(40.dp).clickable { onBack() },
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                val epLabel = if (mediaType == "tv" && currentSeason > 0 && currentEpisode > 0) {
                    "S${currentSeason} E${currentEpisode}"
                } else null
                Column(Modifier.weight(1f)) {
                    Text(currentTitle, color = Color.White, fontSize = 15.sp, maxLines = 1)
                    if (epLabel != null) {
                        Text(epLabel, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }
            }
        }

        """

t = t[:av] + new_block + t[next_av:]

if "import androidx.compose.foundation.layout.Spacer" not in t:
    t = t.replace(
        "import androidx.compose.foundation.layout.Box",
        "import androidx.compose.foundation.layout.Box\nimport androidx.compose.foundation.layout.Spacer",
        1,
    )
if "import androidx.compose.foundation.layout.width" not in t:
    t = t.replace(
        "import androidx.compose.foundation.layout.size",
        "import androidx.compose.foundation.layout.size\nimport androidx.compose.foundation.layout.width",
        1,
    )

p.write_text(t)
print("top controls rewritten ok")
