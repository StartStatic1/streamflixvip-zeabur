package com.streamflixvip.app.ui.theme

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Cartão "vidro": fundo translúcido + borda fina. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    strong: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .clip(shape)
            .background(if (strong) StreamFlixColors.GlassStrong else StreamFlixColors.Glass, shape)
            .border(1.dp, StreamFlixColors.GlassBorder, shape),
        content = content,
    )
}

/** Cache simples (url -> cor) para não recalcular a cada tela. */
private val posterColorCache = java.util.concurrent.ConcurrentHashMap<String, Int>()

/** Cor marcante do pôster (média dos pixels mais saturados). Nunca lança: null se falhar. */
suspend fun posterColor(context: android.content.Context, url: String?): Color? {
    if (url.isNullOrBlank()) return null
    posterColorCache[url]?.let { return Color(it) }
    return runCatching {
        val req = ImageRequest.Builder(context).data(url).allowHardware(false).size(48, 72).build()
        val res = context.imageLoader.execute(req) as? SuccessResult ?: return null
        val bmp = (res.drawable as? BitmapDrawable)?.bitmap ?: return null
        withContext(Dispatchers.Default) { dominant(bmp) }
    }.getOrNull()?.also { posterColorCache[url] = it }?.let { Color(it) }
}

private fun dominant(src: Bitmap): Int {
    val bmp = Bitmap.createScaledBitmap(src, 24, 36, true)
    var r = 0L; var g = 0L; var b = 0L; var n = 0L
    val hsv = FloatArray(3)
    for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
        val c = bmp.getPixel(x, y)
        android.graphics.Color.colorToHSV(c, hsv)
        if (hsv[1] < 0.25f || hsv[2] < 0.18f) continue
        r += android.graphics.Color.red(c); g += android.graphics.Color.green(c); b += android.graphics.Color.blue(c); n++
    }
    if (n == 0L) return 0xFF1A1A1A.toInt()
    val avg = android.graphics.Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    // escurece para servir de fundo sem brigar com o texto
    android.graphics.Color.colorToHSV(avg, hsv)
    hsv[2] = hsv[2].coerceAtMost(0.45f)
    return android.graphics.Color.HSVToColor(hsv)
}

/**
 * Fundo com a cor do pôster: brilho colorido no topo que some no preto.
 * Se a cor não puder ser lida, mostra só o fundo escuro padrão.
 */
@Composable
fun PosterBackdrop(posterUrl: String?, modifier: Modifier = Modifier, height: Dp? = null) {
    val context = LocalContext.current
    var target by remember(posterUrl) { mutableStateOf(Color(0xFF141414)) }
    LaunchedEffect(posterUrl) { posterColor(context, posterUrl)?.let { target = it } }
    val color by animateColorAsState(target, tween(600), label = "posterBg")
    Box(
        modifier.then(Modifier.fillMaxSize()).background(
            Brush.verticalGradient(
                0f to color.copy(alpha = 0.85f),
                0.45f to color.copy(alpha = 0.28f),
                1f to StreamFlixColors.Background,
            ),
        ),
    )
}
