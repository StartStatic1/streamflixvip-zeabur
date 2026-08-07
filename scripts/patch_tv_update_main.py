#!/usr/bin/env python3
from pathlib import Path

p = Path("android-tv/app/src/main/java/com/streamflixvip/tv/MainTvActivity.kt")
t = p.read_text()
if "UpdateRequiredTvScreen" in t:
    print("already wired")
    raise SystemExit(0)

def add_import(text, line):
    if line in text:
        return text
    idx = text.rfind("\nimport ")
    end = text.find("\n", idx + 1)
    return text[:end] + "\n" + line + text[end:]

t = add_import(t, "import android.content.Intent")
t = add_import(t, "import android.net.Uri")
t = add_import(t, "import androidx.compose.ui.platform.LocalContext")
t = add_import(t, "import com.streamflixvip.tv.BuildConfig")
t = add_import(t, "import com.streamflixvip.tv.network.AppVersionResponse")
t = add_import(t, "import com.streamflixvip.tv.ui.update.UpdateRequiredTvScreen")

anchor = """            StreamFlixTvTheme {
                val navController = rememberNavController()
                val activationManager = remember { TvActivationManager(applicationContext) }
                val scope = rememberCoroutineScope()
"""

insert = """            StreamFlixTvTheme {
                val navController = rememberNavController()
                val activationManager = remember { TvActivationManager(applicationContext) }
                val scope = rememberCoroutineScope()
                val context = LocalContext.current

                var updateInfo by remember { mutableStateOf<AppVersionResponse?>(null) }
                var isDownloadingUpdate by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    try {
                        val response = NetworkModule.appVersionApi.getVersion()
                        if (response.forceUpdate and response.versionCode > BuildConfig.VERSION_CODE) {
                            updateInfo = response
                        }
                    } catch (_: Exception) {
                    }
                }

                if (updateInfo != null) {
                    UpdateRequiredTvScreen(
                        versionName = updateInfo!!.versionName,
                        releaseNotes = updateInfo!!.releaseNotes ?: "",
                        isDownloading = isDownloadingUpdate,
                        onDownloadClick = {
                            isDownloadingUpdate = true
                            try {
                                val url = updateInfo!!.apkUrl
                                if (!url.isNullOrBlank()) {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                                    )
                                }
                            } catch (_: Exception) {
                            } finally {
                                isDownloadingUpdate = false
                            }
                        },
                    )
                    return@StreamFlixTvTheme
                }
"""

# Fix Kotlin: use && not and
insert = insert.replace("response.forceUpdate and response.versionCode", "response.forceUpdate && response.versionCode")

if anchor not in t:
    raise SystemExit("anchor not found")
t = t.replace(anchor, insert, 1)
p.write_text(t)
print("patched", len(t))
assert "UpdateRequiredTvScreen" in t
print("ok")
