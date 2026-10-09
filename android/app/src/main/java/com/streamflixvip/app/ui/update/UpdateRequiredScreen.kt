package com.streamflixvip.app.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun UpdateRequiredScreen(
    versionName: String,
    releaseNotes: String,
    isDownloading: Boolean,
    downloadProgress: Int = -1,
    errorMessage: String? = null,
    onDownloadClick: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(88.dp).clip(CircleShape).background(Color(0xFF1A1A1A)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.CloudDownload, null, tint = Color.White, modifier = Modifier.size(44.dp))
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(28.dp))
            Text("Nova versão disponível", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
            androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
            Text(
                "Para continuar usando o StreamFlixVIP, baixe a versão $versionName. Essa atualização é obrigatória.",
                fontSize = 15.sp,
                color = Color(0xFFBDBDBD),
                textAlign = TextAlign.Center,
                lineHeight = 21.sp,
            )
            if (releaseNotes.isNotBlank()) {
                androidx.compose.foundation.layout.Spacer(Modifier.height(20.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF161616)).padding(16.dp),
                ) {
                    Column {
                        Text("O que mudou", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
                        Text(releaseNotes, fontSize = 14.sp, color = Color(0xFFE5E5E5), lineHeight = 20.sp)
                    }
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(32.dp))
            Button(
                onClick = onDownloadClick,
                enabled = !isDownloading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    disabledContainerColor = Color.White,
                    disabledContentColor = Color.Black,
                ),
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                    androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
                    Text(
                        if (downloadProgress in 0..100) "Baixando $downloadProgress%" else "Baixando...",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                    )
                } else {
                    Text("Baixar atualização", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
            errorMessage?.let { err ->
                Text(err, fontSize = 13.sp, color = Color(0xFFFF6B6B), textAlign = TextAlign.Center)
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            }
            Text(
                "Download dentro do app. Se pedir permissao, ative e toque Baixar de novo.",
                fontSize = 12.sp,
                color = Color(0xFF8A8A8A),
                textAlign = TextAlign.Center,
            )
        }
    }
}
