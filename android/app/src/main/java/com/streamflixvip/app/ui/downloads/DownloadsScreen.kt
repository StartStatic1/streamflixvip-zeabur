package com.streamflixvip.app.ui.downloads

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.offline.Download
import coil.compose.AsyncImage
import com.streamflixvip.app.data.VipStatusHolder
import com.streamflixvip.app.network.TmdbImages
import com.streamflixvip.app.offline.OfflineDownloads
import com.streamflixvip.app.offline.OfflineVip
import kotlinx.coroutines.delay

/** Perfil → Downloads: o que foi baixado para ver sem internet, espaço usado e apagar. */
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onPlay: (OfflineDownloads.Item) -> Unit,
) {
    val context = LocalContext.current
    val items by OfflineDownloads.items.collectAsState()
    var confirmDelete by remember { mutableStateOf<OfflineDownloads.Item?>(null) }
    var freeBytes by remember { mutableStateOf(OfflineDownloads.freeBytes(context)) }
    // atualiza porcentagem/espaço enquanto a tela está aberta
    LaunchedEffect(Unit) {
        OfflineDownloads.init(context)
        while (true) {
            OfflineDownloads.refresh()
            freeBytes = OfflineDownloads.freeBytes(context)
            delay(1_500)
        }
    }
    val accent = com.streamflixvip.app.ui.theme.AccentStore.current.accent
    val used = items.sumOf { it.bytes }

    Column(Modifier.fillMaxSize().background(Color(0xFF070B12)).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = Color.White) }
            Text("Downloads", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        // espaço
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF111723)).padding(14.dp)) {
            Text("Espaço no aparelho", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            val total = (used + freeBytes).coerceAtLeast(1L)
            LinearProgressIndicator(
                progress = { (used.toFloat() / total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = accent,
                trackColor = Color.White.copy(alpha = 0.12f),
            )
            Spacer(Modifier.height(6.dp))
            Text("Downloads: ${formatBytes(used)} · Livre: ${formatBytes(freeBytes)}", color = Color.White, fontSize = 13.sp)
        }
        Text(
            "Dá para baixar filmes e episódios quando o servidor toca no player do app (arquivo MP4/MKV ou HLS): " +
                "abra o vídeo e toque em Baixar. Servidores que abrem em player de site, TV ao vivo e alguns links " +
                "protegidos não permitem baixar — aí é só escolher outro servidor. Para assistir sem internet o VIP precisa estar ativo.",
            color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
        )
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhum download ainda", color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp)
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.id }) { item ->
                DownloadRow(
                    item = item,
                    accent = accent,
                    onPlay = {
                        if (!item.isDone) return@DownloadRow
                        if (VipStatusHolder.isVipNow() || OfflineVip.isValid()) onPlay(item)
                        else Toast.makeText(context, "Seu VIP venceu. Renove para assistir os downloads.", Toast.LENGTH_LONG).show()
                    },
                    onRetry = { OfflineDownloads.retry(context, item) },
                    onDelete = { confirmDelete = item },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Apagar download?") },
            text = { Text("\"${label(item)}\" sai do aparelho e libera ${formatBytes(item.bytes)}.") },
            confirmButton = {
                TextButton(onClick = { OfflineDownloads.remove(context, item.id); confirmDelete = null }) { Text("Apagar") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun DownloadRow(
    item: OfflineDownloads.Item,
    accent: Color,
    onPlay: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F141E)).clickable(enabled = item.isDone, onClick = onPlay).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = TmdbImages.poster(item.meta.posterPath, "w185"),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(54.dp).height(80.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1A2230)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label(item), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            val status = when (item.state) {
                Download.STATE_COMPLETED -> "Pronto · ${formatBytes(item.bytes)}"
                Download.STATE_DOWNLOADING -> "Baixando ${item.percent.toInt()}% · ${formatBytes(item.bytes)}"
                Download.STATE_QUEUED -> "Na fila"
                Download.STATE_RESTARTING -> "Recomeçando"
                Download.STATE_STOPPED -> "Pausado"
                Download.STATE_REMOVING -> "Apagando…"
                Download.STATE_FAILED -> "Falhou — a fonte parou de responder. Tente de novo ou baixe por outro servidor"
                else -> ""
            }
            Text(status, color = if (item.state == Download.STATE_FAILED) Color(0xFFFF8A80) else Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
            if (item.state == Download.STATE_DOWNLOADING || item.state == Download.STATE_QUEUED) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (item.percent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = accent, trackColor = Color.White.copy(alpha = 0.12f),
                )
            }
        }
        if (item.isDone) IconButton(onClick = onPlay) { Icon(Icons.Filled.PlayArrow, "Assistir", tint = accent, modifier = Modifier.size(28.dp)) }
        if (item.state == Download.STATE_FAILED) IconButton(onClick = onRetry) { Icon(Icons.Filled.Refresh, "Tentar de novo", tint = Color.White) }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Apagar", tint = Color.White.copy(alpha = 0.7f)) }
    }
}

internal fun label(item: OfflineDownloads.Item): String {
    val m = item.meta
    return if (m.mediaType == "tv" && m.season > 0) "${m.title.replace(Regex("\\s+[—-]\\s+S\\d+E\\d+.*$"), "")} — T${m.season}E${m.episode}" else m.title
}

internal fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> String.format(java.util.Locale("pt", "BR"), "%.1f GB", b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> String.format(java.util.Locale("pt", "BR"), "%.0f MB", b / (1L shl 20).toDouble())
    b > 0 -> "${b / 1024} KB"
    else -> "0 MB"
}
