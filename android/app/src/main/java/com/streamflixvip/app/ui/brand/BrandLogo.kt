package com.streamflixvip.app.ui.brand

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.streamflixvip.app.R

/** Marca StreamFlix: o ícone oficial (letreiro neon). accent/accent2 mantidos por compatibilidade. */
@Composable
fun BrandMark(size: Dp = 88.dp, accent: Color = Color.Unspecified, accent2: Color = Color.Unspecified, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_fg),
        contentDescription = "StreamFlix",
        modifier = modifier.size(size).clip(RoundedCornerShape(size * 0.22f)),
    )
}
