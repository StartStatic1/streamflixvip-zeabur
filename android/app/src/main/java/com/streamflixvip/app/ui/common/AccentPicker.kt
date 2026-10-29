package com.streamflixvip.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamflixvip.app.ui.theme.AccentStore
import com.streamflixvip.app.ui.theme.AccentTheme
import com.streamflixvip.app.ui.theme.GlassCard

/** Escolha da cor de destaque do app (círculos com a cor; o escolhido tem ✓). */
@Composable
fun AccentPicker(modifier: Modifier = Modifier) {
    val current by AccentStore.state.collectAsState()
    GlassCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Cor do app", fontSize = 15.sp, color = Color.White)
            Text("Muda botões, destaques e a barra do player", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
            Row(
                Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AccentTheme.entries.forEach { t ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .background(Brush.linearGradient(listOf(t.accent, t.accent2)), CircleShape)
                                .border(if (t == current) 3.dp else 1.dp, if (t == current) Color.White else Color.White.copy(alpha = 0.2f), CircleShape)
                                .clickable { AccentStore.set(t) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (t == current) Icon(Icons.Filled.Check, null, tint = t.onAccent, modifier = Modifier.size(22.dp))
                        }
                        Text(t.label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f), modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}
