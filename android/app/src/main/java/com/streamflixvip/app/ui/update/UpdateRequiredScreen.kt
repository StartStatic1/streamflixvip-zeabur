package com.streamflixvip.app.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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

/**
 * Tela de bloqueio total mostrada quando existe uma versão mais nova do
 * app do que a instalada (ver AppRoot, que decide se mostra esta tela ou
 * segue o fluxo normal). De propósito, esta tela NÃO tem botão de
 * voltar, gesto de dispensar ou qualquer saída — a única ação possível
 * é tocar em "Baixar atualização", que sai para o navegador/downloads.
 *
 * Isso só é aceitável porque é o próprio dono do produto controlando
 * quando forçar isso (via app-version.json no backend); não é um padrão
 * a generalizar para apps de terceiros.
 */
@Composable
fun UpdateRequiredScreen(
    versionName: String,
    releaseNotes: String,
    isDownloading: Boolean,
    onDownloadClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A0A10), Color(0xFF15151C)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp),
                )
            }

            androidx.compose.foundation.layout.Spacer(Modifier.height(28.dp))

            Text(
                text = "Nova versão disponível",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))

            Text(
                text = "Para continuar usando o StreamFlixVIP, baixe a versão $versionName. Essa atualização é obrigatória.",
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = 21.sp,
            )

            if (releaseNotes.isNotBlank()) {
                androidx.compose.foundation.layout.Spacer(Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(16.dp),
                ) {
                    Column {
                        Text(
                            text = "O que mudou",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
                        Text(
                            text = releaseNotes,
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            lineHeight = 20.sp,
                        )
                    }
                }
            }

            androidx.compose.foundation.layout.Spacer(Modifier.height(32.dp))

            Button(
                onClick = onDownloadClick,
                enabled = !isDownloading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color(0xFF0A0A10),
                ),
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color(0xFF0A0A10),
                        strokeWidth = 2.dp,
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.height(0.dp))
                } else {
                    Text(
                        text = "Baixar atualização",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))

            Text(
                text = "O download acontece dentro do app. Se pedir permissao, ative e toque Baixar de novo.",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.45f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
