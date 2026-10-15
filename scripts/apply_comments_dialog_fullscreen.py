#!/usr/bin/env python3
"""Forca o Dialog de comentarios a ocupar a tela inteira (MATCH_PARENT).
Sem isso, weight/fillMaxSize nao empurram o campo para o rodape.
"""
from pathlib import Path

p = Path(__file__).resolve().parents[1] / "android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt"
t = p.read_text()

if "DialogWindowProvider" in t and "MATCH_PARENT" in t[t.find("CommentsModal"):t.find("CommentsModal")+2500]:
    print("ok ja fullscreen")
    raise SystemExit(0)

old = '''    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                verticalArrangement = Arrangement.Top,
            ) {
'''

new = '''    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        // Dialog do Compose muitas vezes NAO entrega altura maxima;
        // sem MATCH_PARENT o weight nao empurra o campo para o rodape.
        val dialogView = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.SideEffect {
            val window = (dialogView.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
            window?.setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                verticalArrangement = Arrangement.Top,
            ) {
'''

if old in t:
    t = t.replace(old, new, 1)
    print("ok dialog fullscreen")
else:
    # try simpler match
    old2 = '''    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
'''
    new2 = '''    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val dialogView = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.SideEffect {
            val window = (dialogView.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
            window?.setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
'''
    if old2 in t:
        t = t.replace(old2, new2, 1)
        print("ok dialog fullscreen alt")
    else:
        print("aviso: padrao Dialog nao encontrado")
        i = t.find("private fun CommentsModal(")
        print(repr(t[i:i+600][200:450]))
        raise SystemExit(1)

p.write_text(t)
s = t.find("private fun CommentsModal(")
e = t.find("private fun com.streamflixvip.app.network.TmdbResponse.bestLogoUrl", s)
m = t[s:e]
print("balance", m.count("{"), m.count("}"))
print("has MATCH_PARENT", "MATCH_PARENT" in m)
print("has DialogWindowProvider", "DialogWindowProvider" in m)
print("fim")
