#!/usr/bin/env python3
from pathlib import Path

main = Path("android/app/src/main/java/com/streamflixvip/app/MainActivity.kt")
t = main.read_text()
if "PLACEHOLDER" in t:
    raise SystemExit("MainActivity still placeholder after git show")
old = """                    PlayerScreen(
                        sourceUrl = url,
                        isDirectPlayable = isDirect,
                        userId = userId,"""
new = """                    PlayerScreen(
                        sourceUrl = url,
                        isDirectPlayable = isDirect,
                        onBack = { navController.popBackStack() },
                        userId = userId,"""
if "onBack = { navController.popBackStack() }" not in t:
    if old not in t:
        raise SystemExit("MainActivity PlayerScreen call pattern missing")
    t = t.replace(old, new, 1)
    main.write_text(t)
    print("MainActivity onBack added")
else:
    print("MainActivity already has onBack")

player = Path("android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt")
p = player.read_text()
if "PLACEHOLDER" in p or "fun NativePlayer" not in p:
    raise SystemExit("PlayerScreen broken")

if "onBack: () -> Unit" not in p.split("fun PlayerScreen")[1][:600]:
    p = p.replace(
        "    resumeSeconds: Int = 0,\n) {",
        "    resumeSeconds: Int = 0,\n    onBack: () -> Unit = {},\n) {",
        1,
    )
    print("PlayerScreen onBack param")

if "import androidx.activity.compose.BackHandler" not in p:
    p = p.replace(
        "import androidx.compose.runtime.Composable",
        "import androidx.activity.compose.BackHandler\nimport androidx.compose.runtime.Composable",
        1,
    )
if "BackHandler { onBack() }" not in p:
    p = p.replace(
        "    val view = LocalView.current\n",
        "    val view = LocalView.current\n    BackHandler { onBack() }\n",
        1,
    )
    print("BackHandler")

if "onBack = onBack" not in p:
    p = p.replace(
        """        NativePlayer(
            url = currentResolvedUrl,
            userId = userId,
            accessToken = accessToken,
            tmdbId = tmdbId,
            mediaType = mediaType,
            season = season,
            episode = episode,
            title = title,
            posterPath = posterPath,
            resumeSeconds = resumeSeconds,
        )""",
        """        NativePlayer(
            url = currentResolvedUrl,
            userId = userId,
            accessToken = accessToken,
            tmdbId = tmdbId,
            mediaType = mediaType,
            season = season,
            episode = episode,
            title = title,
            posterPath = posterPath,
            resumeSeconds = resumeSeconds,
            onBack = onBack,
        )""",
        1,
    )
    print("NativePlayer wired")

if "onBack: () -> Unit" not in p.split("fun NativePlayer")[1][:500]:
    p = p.replace(
        """private fun NativePlayer(
    url: String,
    userId: String?,
    accessToken: String?,
    tmdbId: Int,
    mediaType: String,
    season: Int,
    episode: Int,
    title: String,
    posterPath: String?,
    resumeSeconds: Int,
) {""",
        """private fun NativePlayer(
    url: String,
    userId: String?,
    accessToken: String?,
    tmdbId: Int,
    mediaType: String,
    season: Int,
    episode: Int,
    title: String,
    posterPath: String?,
    resumeSeconds: Int,
    onBack: () -> Unit = {},
) {""",
        1,
    )
    print("NativePlayer sig")

if 'Text("Voltar"' not in p:
    old = """            Column {
                Text(currentTitle, color = Color.White, fontSize = 15.sp, maxLines = 1)
                if (epLabel != null) {
                    Text(epLabel, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }"""
    new = """            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                    modifier = Modifier.clickable { onBack() },
                ) {
                    Text("Voltar", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                }
                Column {
                    Text(currentTitle, color = Color.White, fontSize = 15.sp, maxLines = 1)
                    if (epLabel != null) {
                        Text(epLabel, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }
            }"""
    if old not in p:
        raise SystemExit("title block missing")
    p = p.replace(old, new, 1)
    print("Voltar button")

player.write_text(p)
print("OK", main.stat().st_size, player.stat().st_size)
assert "popBackStack" in main.read_text()
assert "BackHandler" in player.read_text()
print("verify OK")
