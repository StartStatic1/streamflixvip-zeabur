#!/usr/bin/env python3
from pathlib import Path
import re

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

p = Path("android/app/src/main/java/com/streamflixvip/app/MainActivity.kt")
t = p.read_text()

if "var downloadProgress" not in t:
    t = t.replace(
        "var isDownloadingUpdate by remember { mutableStateOf(false) }",
        "var isDownloadingUpdate by remember { mutableStateOf(false) }\n    var downloadProgress by remember { mutableStateOf(-1) }",
        1,
    )

start = t.find("            onDownloadClick = {")
if start < 0:
    raise SystemExit("onDownloadClick missing")
end = t.find("\n            },", start)
if end < 0:
    raise SystemExit("end missing")
end = t.find("\n", end + 1)

new = (
"            onDownloadClick = {\n"
"                val url = updateInfo!!.apkUrl\n"
"                if (url.isBlank()) {\n"
"                    updateError = \"URL vazia\"\n"
"                    return@UpdateRequiredScreen\n"
"                }\n"
"                updateError = null\n"
"                isDownloadingUpdate = true\n"
"                downloadProgress = 0\n"
"                updateScope.launch {\n"
"                    try {\n"
"                        if (!com.streamflixvip.app.update.ApkInstaller.canInstallPackages(context)) {\n"
"                            com.streamflixvip.app.update.ApkInstaller.openInstallPermissionSettings(context)\n"
"                            updateError = \"Ative permitir desta fonte e toque Baixar de novo\"\n"
"                            android.widget.Toast.makeText(context, updateError, android.widget.Toast.LENGTH_LONG).show()\n"
"                            return@launch\n"
"                        }\n"
"                        val file = com.streamflixvip.app.update.ApkInstaller.download(context, url) { pct ->\n"
"                            downloadProgress = pct\n"
"                        }\n"
"                        downloadProgress = 100\n"
"                        com.streamflixvip.app.update.ApkInstaller.install(context, file)\n"
"                        android.widget.Toast.makeText(context, \"Abrindo instalador...\", android.widget.Toast.LENGTH_SHORT).show()\n"
"                    } catch (e: Exception) {\n"
"                        updateError = (e.message ?: \"Falha\") + \" — abrindo navegador\"\n"
"                        android.widget.Toast.makeText(context, updateError, android.widget.Toast.LENGTH_LONG).show()\n"
"                        try {\n"
"                            context.startActivity(\n"
"                                android.content.Intent(\n"
"                                    android.content.Intent.ACTION_VIEW,\n"
"                                    android.net.Uri.parse(url),\n"
"                                )\n"
"                            )\n"
"                        } catch (_: Exception) {\n"
"                        }\n"
"                    } finally {\n"
"                        isDownloadingUpdate = false\n"
"                        downloadProgress = -1\n"
"                    }\n"
"                }\n"
"            },"
)
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
