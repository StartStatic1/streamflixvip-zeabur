package com.streamflixvip.app.ui.retro

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamflixvip.app.data.ReferralRepository
import com.streamflixvip.app.data.RetroStats
import com.streamflixvip.app.ui.theme.StreamFlixColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Gold = StreamFlixColors.Amber

/** Cartão de entrada (Perfil sempre; Início em dezembro/janeiro). */
@Composable
fun RetroEntryCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val year = retroYear()
    Row(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3A1C71), Color(0xFFB8860B))))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.AutoAwesome, null, tint = Color.White) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Sua Retrospectiva $year", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("O que você mais assistiu no ano — e uma arte para compartilhar", fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
        }
        Icon(Icons.Filled.ChevronRight, null, tint = Color.White)
    }
}

private sealed interface RetroUi {
    data object Loading : RetroUi
    data object Error : RetroUi
    data class Ready(val stats: RetroStats, val image: Bitmap) : RetroUi
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RetroScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val year = remember { retroYear() }
    var ui by remember { mutableStateOf<RetroUi>(RetroUi.Loading) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        ui = RetroUi.Loading
        val stats = ReferralRepository.retro(year)
        ui = if (stats == null) RetroUi.Error else {
            val bmp = withContext(Dispatchers.Default) { runCatching { renderRetroImage(context, stats) }.getOrNull() }
            if (bmp == null) RetroUi.Error else RetroUi.Ready(stats, bmp)
        }
    }

    Column(Modifier.fillMaxSize().background(StreamFlixColors.Background)) {
        TopAppBar(
            title = { Text("Sua Retrospectiva $year", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = StreamFlixColors.Background, titleContentColor = Color.White, navigationIconContentColor = Color.White),
        )
        when (val u = ui) {
            RetroUi.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Gold)
                    Spacer(Modifier.height(12.dp))
                    Text("Montando o seu ano…", color = Color.White.copy(alpha = 0.8f))
                }
            }
            RetroUi.Error -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Não foi possível carregar a retrospectiva agora.", color = Color.White, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { reload++ }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF1A1204))) {
                        Text("Tentar de novo", fontWeight = FontWeight.Bold)
                    }
                }
            }
            is RetroUi.Ready -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val s = u.stats
                if (!s.hasData) {
                    Text(
                        "Ainda não temos muita coisa do seu $year por aqui. Assista, salve na Minha Lista e comente — sua retrospectiva vai ganhando forma.",
                        color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                }
                Image(
                    bitmap = u.image.asImageBitmap(),
                    contentDescription = "Arte da sua retrospectiva",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(0.82f).aspectRatio(1080f / 1920f)
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp)),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { shareRetroImage(context, u.image, s.year) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF1A1204)),
                ) {
                    Icon(Icons.Filled.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Compartilhar nos Stories / Status", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(18.dp))
                if (s.topTitles.isNotEmpty()) Section("Mais assistidos") {
                    s.topTitles.forEachIndexed { i, t ->
                        val extra = when {
                            t.episodes > 0 -> " · ${t.episodes} ep."
                            t.minutes > 0 -> " · ${formatWatchTime(t.minutes)}"
                            else -> ""
                        }
                        Text("${i + 1}. ${t.title}$extra", color = Color.White, fontSize = 14.sp, maxLines = 1)
                    }
                }
                if (s.favoriteTitles.isNotEmpty()) Section("Salvos na Minha Lista este ano") {
                    s.favoriteTitles.forEach { Text("• ${it.title}", color = Color.White, fontSize = 14.sp, maxLines = 1) }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Com base no que ficou salvo no app (continuar assistindo, Minha Lista e comentários) na conta principal. " +
                        "O tempo de tela é um mínimo: o app guarda o ponto onde você parou de tempos em tempos.",
                    color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 12.dp)
            .clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.05f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title.uppercase(), color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        content()
    }
}
