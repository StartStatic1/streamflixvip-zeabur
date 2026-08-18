#!/usr/bin/env python3
from pathlib import Path
import re

# --- UpdateRequiredScreen ---
p = Path("android/app/src/main/java/com/streamflixvip/app/ui/update/UpdateRequiredScreen.kt")
t = p.read_text()

if "downloadProgress" not in t:
    t = t.replace(
        "    isDownloading: Boolean,\n    onDownloadClick: () -> Unit,",
        "    isDownloading: Boolean,\n    downloadProgress: Int = -1,\n    errorMessage: String? = null,\n    onDownloadClick: () -> Unit,",
        1,
    )

pat = r'if \(isDownloading\) \{.*?\} else \{\s*Text\(\s*text = "Baixar atualiza[^"]*",\s*fontSize = 16\.sp,\s*fontWeight = FontWeight\.Bold,\s*\)\s*\}'
repl = (
    "if (isDownloading) {\n"
    "                    CircularProgressIndicator(\n"
    "                        modifier = Modifier.size(20.dp),\n"
    "                        color = Color(0xFF0A0A10),\n"
    "                        strokeWidth = 2.dp,\n"
    "                    )\n"
    "                    androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))\n"
    "                    Text(\n"
    "                        text = if (downloadProgress in 0..100) \"Baixando $downloadProgress%\" else \"Baixando...\",\n"
    "                        fontSize = 15.sp,\n"
    "                        fontWeight = FontWeight.Bold,\n"
    "                    )\n"
    "                } else {\n"
    "                    Text(\n"
    "                        text = \"Baixar atualizacao\",\n"
    "                        fontSize = 16.sp,\n"
    "                        fontWeight = FontWeight.Bold,\n"
    "                    )\n"
    "                }"
)
t2, n = re.subn(pat, repl, t, count=1, flags=re.S)
print("button", n)
if n:
    t = t2

t = t.replace(
    "O download abre no seu navegador. Depois de instalar, abra o app de novo.",
    "Download dentro do app. Se pedir permissao, ative e toque Baixar de novo.",
)
t = t.replace(
    "O download acontece dentro do app. Se pedir permissao, ative e toque Baixar de novo.",
    "Download dentro do app. Se pedir permissao, ative e toque Baixar de novo.",
)

if "errorMessage?.let" not in t:
    marker = '            Text(\n                text = "Download dentro do app.'
    if marker in t:
        inject = (
            "            errorMessage?.let { err ->\n"
            "                androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))\n"
            "                Text(\n"
            "                    text = err,\n"
            "                    fontSize = 13.sp,\n"
            "                    color = Color(0xFFFF6B6B),\n"
            "                    textAlign = TextAlign.Center,\n"
            "                )\n"
            "            }\n\n"
        )
        t = t.replace(marker, inject + marker, 1)
        print("error ui ok")

p.write_text(t)
print("UpdateRequiredScreen done")

# --- MainActivity ---
p = Path("android/app/src/main/java/com/streamflixvip/app/MainActivity.kt")
t = p.read_text()

if "var downloadProgress" not in t:
    t = t.replace(
        "var isDownloadingUpdate by remember { mutableStateOf(false) }",
        "var isDownloadingUpdate by remember { mutableStateOf(false) }\n    var downloadProgress by remember { mutableStateOf(-1) }",
        1,
    )

# Find onDownloadClick with flexible indent
idx = t.find("onDownloadClick = {")
if idx < 0:
    raise SystemExit("onDownloadClick missing")
# start of line
start = t.rfind("\n", 0, idx) + 1
# Find closing "}," after launch block — look for line that is only spaces + },
rest = t[idx:]
m = re.search(r"\n[ \t]*\},", rest)
if not m:
    raise SystemExit("end missing")
end = idx + m.end()

new = """            onDownloadClick = {
                val url = updateInfo!!.apkUrl
                if (url.isBlank()) {
                    updateError = "URL vazia"
                    return@UpdateRequiredScreen
                }
                updateError = null
                isDownloadingUpdate = true
                downloadProgress = 0
                updateScope.launch {
                    try {
                        if (!com.streamflixvip.app.update.ApkInstaller.canInstallPackages(context)) {
                            com.streamflixvip.app.update.ApkInstaller.openInstallPermissionSettings(context)
                            updateError = "Ative permitir desta fonte e toque Baixar de novo"
                            android.widget.Toast.makeText(context, updateError, android.widget.Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        val file = com.streamflixvip.app.update.ApkInstaller.download(context, url) { pct ->
                            downloadProgress = pct
                        }
                        downloadProgress = 100
                        com.streamflixvip.app.update.ApkInstaller.install(context, file)
                        android.widget.Toast.makeText(context, "Abrindo instalador...", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        updateError = (e.message ?: "Falha") + " — abrindo navegador"
                        android.widget.Toast.makeText(context, updateError, android.widget.Toast.LENGTH_LONG).show()
                        try {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(url),
                                )
                            )
                        } catch (_: Exception) {
                        }
                    } finally {
                        isDownloadingUpdate = false
                        downloadProgress = -1
                    }
                }
            },"""
t = t[:start] + new + t[end:]

if "downloadProgress = downloadProgress" not in t:
    t = t.replace(
        "isDownloading = isDownloadingUpdate,",
        "isDownloading = isDownloadingUpdate,\n            downloadProgress = downloadProgress,\n            errorMessage = updateError,",
        1,
    )

p.write_text(t)
print("MainActivity done")
print("ALL OK")
