package com.streamflixvip.tv.ui.update

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults

private val Accent = Color(0xFF6366F1)
private val AccentSoft = Color(0xFF818CF8)

@Composable
fun UpdateRequiredTvScreen(
    versionName: String,
    releaseNotes: String,
    isDownloading: Boolean,
    downloadProgress: Int = 0,
    statusMessage: String? = null,
    onDownloadClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF12122A), Color(0xFF0B0B14), Color(0xFF0A0A12)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(48.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudDownload,
                    contentDescription = null,
                    tint = AccentSoft,
                    modifier = Modifier.size(48.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Atualizacao disponivel",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Versao $versionName",
                    color = AccentSoft,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (releaseNotes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        releaseNotes,
                        color = Color(0xFFA1A1B5),
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                    )
                }

                if (isDownloading) {
                    Spacer(modifier = Modifier.height(20.dp))
                    LinearProgressIndicator(
                        progress = { (downloadProgress.coerceIn(0, 100) / 100f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = AccentSoft,
                        trackColor = Color.White.copy(alpha = 0.12f),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (downloadProgress >= 100) {
                            "Download concluido — abrindo instalador…"
                        } else {
                            "Baixando… $downloadProgress%"
                        },
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 14.sp,
                    )
                }

                if (!statusMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        statusMessage,
                        color = Color(0xFFF87171),
                        fontSize = 14.sp,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onDownloadClick,
                    enabled = !isDownloading,
                    colors = ButtonDefaults.colors(
                        containerColor = Accent,
                        focusedContainerColor = AccentSoft,
                        contentColor = Color.White,
                        focusedContentColor = Color.White,
                        disabledContainerColor = Accent.copy(alpha = 0.4f),
                        disabledContentColor = Color.White.copy(alpha = 0.6f),
                    ),
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Baixando…")
                    } else {
                        Text("Baixar e instalar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "O download acontece dentro do app — sem abrir o navegador. Depois permita a instalacao.",
                    color = Color(0xFFA1A1B5).copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}
