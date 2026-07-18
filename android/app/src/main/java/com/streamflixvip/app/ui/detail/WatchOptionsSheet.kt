package com.streamflixvip.app.ui.detail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamflixvip.app.BuildConfig
import com.streamflixvip.app.network.VipSource

/**
 * Bottom sheet "Como deseja assistir?" — aparece depois que a fonte já
 * foi decidida (seja porque só havia uma, seja porque o usuário escolheu
 * um servidor no seletor). Oferece Player interno (o Exoplayer do próprio
 * app, comportamento que já existia antes) ou Player externo (delega pro
 * Android escolher entre os apps de vídeo instalados, tipo VLC/MX Player).
 *
 * Fica num arquivo próprio porque é reaproveitado tanto pelo fluxo de
 * filme quanto pelo de episódio de série — mesma decisão, mesma UI, só
 * muda de onde é chamado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchOptionsSheet(
    source: VipSource,
    onDismiss: () -> Unit,
    onPlayInternal: (VipSource) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp).padding(bottom = 28.dp)) {
            Text(
                "Como deseja assistir?",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Escolha uma opção para reproduzir este título.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            WatchOptionRow(
                icon = Icons.Filled.PlayArrow,
                title = "Player interno",
                subtitle = "Assistir no player do app",
                highlighted = true,
                onClick = {
                    onPlayInternal(source)
                    onDismiss()
                },
            )

            // Player externo só faz sentido pra fonte de arquivo/stream
            // direto (mp4/m3u8/proxy) — um iframe de embed de terceiro não
            // é algo que o VLC ou o MX Player conseguem abrir, então nem
            // oferecemos a opção nesse caso, pra não gerar um toque que só
            // resulta em erro no player externo.
            if (source.isDirectPlayable) {
                Spacer(Modifier.height(10.dp))
                WatchOptionRow(
                    icon = Icons.Filled.OpenInNew,
                    title = "Player externo",
                    subtitle = "Abrir no VLC, MX Player ou outro app",
                    highlighted = false,
                    onClick = {
                        val playbackUrl = source.resolvedPlaybackUrl(BuildConfig.API_BASE_URL)
                        openInExternalPlayer(context, playbackUrl, source.displayName)
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
private fun WatchOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (highlighted) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = if (highlighted) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Dispara um Intent.ACTION_VIEW com o tipo MIME de vídeo — o Android
 * junta automaticamente todos os apps capazes de abrir esse tipo de
 * conteúdo (VLC, MX Player, e qualquer outro player instalado) e deixa
 * o próprio usuário escolher, sem o app precisar saber quais players
 * existem no aparelho. Se nenhum app souber abrir (nenhum player de
 * vídeo instalado), o Android lança ActivityNotFoundException — nesse
 * caso avisamos com um Toast em vez de deixar o app quebrar.
 */
private fun openInExternalPlayer(context: android.content.Context, url: String, title: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), "video/*")
            putExtra("title", title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Abrir com"))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Nenhum player externo encontrado. Instale o VLC ou MX Player.", Toast.LENGTH_LONG).show()
    }
}
