package com.streamflixvip.tv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.streamflixvip.tv.ui.theme.LocalPalette
import kotlinx.coroutines.delay

/** Aviso curto no rodape (substitui o Toast do Android, que fica feio na TV). */
@Stable
class Toaster {
    var message by mutableStateOf<String?>(null)
        private set
    var nonce by mutableIntStateOf(0)
        private set

    fun show(text: String) {
        message = text
        nonce++
    }

    fun clear() {
        message = null
    }
}

val LocalToaster = staticCompositionLocalOf { Toaster() }

@Composable
fun ToastHost(toaster: Toaster) {
    val p = LocalPalette.current
    val msg = toaster.message
    LaunchedEffect(toaster.nonce) {
        if (toaster.message != null) {
            delay(2600)
            toaster.clear()
        }
    }
    Box(Modifier.fillMaxSize().padding(bottom = 36.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = msg != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            Text(
                msg ?: "",
                color = p.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(p.surfaceHi.copy(alpha = 0.96f))
                    .border(1.dp, p.accent.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 22.dp, vertical = 11.dp),
            )
        }
    }
}
