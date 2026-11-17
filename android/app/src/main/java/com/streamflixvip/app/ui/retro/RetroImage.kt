package com.streamflixvip.app.ui.retro

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import com.streamflixvip.app.R
import com.streamflixvip.app.data.RetroStats
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

const val RETRO_SITE = "streamflixvip.online"
const val RETRO_SITE_URL = "https://www.streamflixvip.online"

/** Ano da retrospectiva: em janeiro ainda mostra o ano que acabou. */
fun retroYear(): Int {
    val c = Calendar.getInstance()
    return if (c.get(Calendar.MONTH) == Calendar.JANUARY) c.get(Calendar.YEAR) - 1 else c.get(Calendar.YEAR)
}

fun isRetroSeason(): Boolean {
    val m = Calendar.getInstance().get(Calendar.MONTH)
    return m == Calendar.DECEMBER || m == Calendar.JANUARY
}

/** "pelo menos 12 h" / "45 min" — o tempo é o mínimo que o app registrou. */
fun formatWatchTime(minutes: Int): String = when {
    minutes >= 60 -> "${minutes / 60} h"
    else -> "$minutes min"
}

/** Linhas da arte: só entra o que tem número de verdade. */
data class RetroLine(val big: String, val label: String)

fun retroLines(s: RetroStats): List<RetroLine> {
    val out = mutableListOf<RetroLine>()
    if (s.titlesWatched > 0) out += RetroLine(
        s.titlesWatched.toString(),
        if (s.titlesWatched == 1) "título assistido" else "títulos assistidos",
    )
    if (s.minutesWatched >= 1) out += RetroLine(formatWatchTime(s.minutesWatched), "de tela, pelo menos")
    if (s.episodesWatched > 0) out += RetroLine(s.episodesWatched.toString(), if (s.episodesWatched == 1) "episódio" else "episódios")
    if (s.favoritesAdded > 0) out += RetroLine(s.favoritesAdded.toString(), "salvos na Minha Lista")
    if (s.comments > 0) out += RetroLine(s.comments.toString(), if (s.comments == 1) "comentário" else "comentários")
    return out
}

/** Arte vertical 1080x1920 para Stories/Status. */
fun renderRetroImage(context: Context, s: RetroStats): Bitmap {
    val w = 1080; val h = 1920
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val gold = 0xFFFFB300.toInt()
    val white = 0xFFFFFFFF.toInt()
    val muted = 0xB3FFFFFF.toInt()

    // fundo
    val bg = Paint().apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0xFF0B0F1A.toInt(), 0xFF1A1030.toInt(), 0xFF2A1A05.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP) }
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
    val glow = Paint().apply { shader = RadialGradient(w * 0.85f, h * 0.12f, 520f, 0x55FFB300, 0x00FFB300, Shader.TileMode.CLAMP) }
    c.drawCircle(w * 0.85f, h * 0.12f, 520f, glow)
    val glow2 = Paint().apply { shader = RadialGradient(w * 0.1f, h * 0.8f, 600f, 0x447C4DFF, 0x007C4DFF, Shader.TileMode.CLAMP) }
    c.drawCircle(w * 0.1f, h * 0.8f, 600f, glow2)

    val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    fun tp(size: Float, color: Int, tf: Typeface = bold) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = tf }
    fun fit(text: String, p: TextPaint, maxW: Float) = TextUtils.ellipsize(text, p, maxW, TextUtils.TruncateAt.END).toString()
    val left = 90f
    val maxW = w - 2 * left

    // logo + marca
    runCatching {
        val logo = BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher_fg)
        if (logo != null) {
            val size = 150f
            val r = RectF(left, 110f, left + size, 110f + size)
            c.save()
            c.clipPath(Path().apply { addRoundRect(r, 34f, 34f, Path.Direction.CW) })
            c.drawBitmap(logo, null, r, Paint(Paint.FILTER_BITMAP_FLAG))
            c.restore()
        }
    }
    c.drawText("StreamFlix VIP", left + 180f, 205f, tp(58f, white))
    c.drawText("filmes · séries · TV ao vivo", left + 182f, 252f, tp(30f, muted, Typeface.DEFAULT))

    var y = 420f
    c.drawText("Minha", left, y, tp(96f, white)); y += 110f
    c.drawText("Retrospectiva", left, y, tp(96f, white)); y += 120f
    c.drawText(s.year.toString(), left, y, tp(130f, gold)); y += 70f

    val lines = retroLines(s)
    if (lines.isEmpty()) {
        y += 80f
        c.drawText(fit("Meu ano no StreamFlix está só começando!", tp(44f, white), maxW), left, y, tp(44f, white))
    } else {
        y += 40f
        val bigP = tp(84f, gold); val labelP = tp(38f, white, Typeface.DEFAULT)
        lines.take(5).forEach { l ->
            y += 100f
            c.drawText(l.big, left, y, bigP)
            val bw = bigP.measureText(l.big)
            c.drawText(fit(l.label, labelP, maxW - bw - 24f), left + bw + 24f, y, labelP)
        }
    }

    val section = tp(34f, gold)
    val item = tp(42f, white)
    if (s.topGenres.isNotEmpty()) {
        y += 110f
        c.drawText("GÊNEROS FAVORITOS", left, y, section); y += 60f
        c.drawText(fit(s.topGenres.joinToString(" · "), item, maxW), left, y, item)
    }
    val serie = s.topSeries.firstOrNull()
    if (serie != null) {
        y += 90f
        c.drawText("SÉRIE MAIS VISTA", left, y, section); y += 60f
        val eps = if (serie.episodes > 0) "  (${serie.episodes} ep.)" else ""
        c.drawText(fit(serie.title + eps, item, maxW), left, y, item)
    }
    val tops = s.topTitles.take(3)
    if (tops.isNotEmpty() && y < h - 520f) {
        y += 90f
        c.drawText("MAIS ASSISTIDOS", left, y, section)
        tops.forEachIndexed { i, t ->
            if (y < h - 420f) { y += 58f; c.drawText(fit("${i + 1}. ${t.title}", item, maxW), left, y, item) }
        }
    }

    // rodapé
    val pill = RectF(left, h - 250f, w - left, h - 110f)
    c.drawRoundRect(pill, 70f, 70f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold })
    val dark = tp(38f, 0xFF1A1204.toInt(), Typeface.DEFAULT)
    val darkBold = tp(50f, 0xFF1A1204.toInt())
    val l1 = "Baixe grátis"
    c.drawText(l1, (w - dark.measureText(l1)) / 2f, h - 190f, dark)
    c.drawText(RETRO_SITE, (w - darkBold.measureText(RETRO_SITE)) / 2f, h - 132f, darkBold)
    return bmp
}

/** Salva a arte no cache e abre o compartilhar do Android. */
fun shareRetroImage(context: Context, bmp: Bitmap, year: Int) {
    runCatching {
        val dir = File(context.cacheDir, "retro").apply { mkdirs() }
        val file = File(dir, "minha-retrospectiva-$year.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Minha Retrospectiva $year no StreamFlix VIP 🎬 Baixe grátis: $RETRO_SITE_URL")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Compartilhar retrospectiva"))
    }.onFailure {
        android.widget.Toast.makeText(context, "Não foi possível compartilhar agora.", android.widget.Toast.LENGTH_SHORT).show()
    }
}
