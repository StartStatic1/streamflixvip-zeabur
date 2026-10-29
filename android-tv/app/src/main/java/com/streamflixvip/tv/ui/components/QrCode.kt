package com.streamflixvip.tv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** QR desenhado (preto no branco). Se não der para gerar, não mostra nada — nunca derruba a tela. */
@Composable
fun QrCode(data: String, modifier: Modifier = Modifier) {
    val matrix = remember(data) {
        runCatching {
            QRCodeWriter().encode(
                data, BarcodeFormat.QR_CODE, 0, 0,
                mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0),
            )
        }.getOrNull()
    } ?: return
    Box(modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(Color.White).padding(8.dp)) {
        Canvas(Modifier.matchParentSizeSafe()) {
            val n = matrix.width
            val cell = size.minDimension / n
            val ox = (size.width - cell * n) / 2f
            val oy = (size.height - cell * n) / 2f
            for (y in 0 until n) for (x in 0 until n) {
                if (matrix.get(x, y)) drawRect(Color.Black, Offset(ox + x * cell, oy + y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

private fun Modifier.matchParentSizeSafe(): Modifier = this.then(Modifier.fillMaxSize())
