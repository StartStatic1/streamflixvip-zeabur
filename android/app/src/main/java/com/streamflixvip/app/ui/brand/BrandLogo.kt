package com.streamflixvip.app.ui.brand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Marca StreamFlix: disco com degradê da cor de destaque + play. Desenhada (sem imagem). */
@Composable
fun BrandMark(size: Dp = 88.dp, accent: Color, accent2: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent), c, r * 1.6f), r * 1.6f, c)
        drawCircle(Brush.linearGradient(listOf(accent, accent2), Offset(0f, 0f), Offset(this.size.width, this.size.height)), r, c)
        val p = Path().apply {
            moveTo(c.x - r * 0.28f, c.y - r * 0.45f)
            lineTo(c.x - r * 0.28f, c.y + r * 0.45f)
            lineTo(c.x + r * 0.5f, c.y)
            close()
        }
        drawPath(p, Color(0xF00A0A0A))
    }
}
