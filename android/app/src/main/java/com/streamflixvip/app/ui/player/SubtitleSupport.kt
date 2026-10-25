package com.streamflixvip.app.ui.player

import android.content.Context
import android.graphics.Color as AColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView

// ---------------------------------------------------------------------------
// Leitura de legendas SRT / WebVTT (tambem o VTT gerado do ASS pelo servidor).
// - aceita "mm:ss.mmm" e "h:mm:ss,mmm"
// - guarda so <i> e <b>; tira <font>, <c.x>, <v Nome>, {\...} e entidades HTML
// - {\an7..9} ou "line:0" no VTT = legenda no topo
// ---------------------------------------------------------------------------

private val CUE_TIME = Regex(
    """((?:\d{1,2}:)?\d{1,2}:\d{2}[,.]\d{1,3})\s*-->\s*((?:\d{1,2}:)?\d{1,2}:\d{2}[,.]\d{1,3})(.*)""",
)
private val ASS_TOP = Regex("""\{[^}]*\\an[789][^}]*\}""")
private val BRACE_TAG = Regex("""\{[^}]*\}""")
private val HTML_TAG = Regex("""<(/?)([a-zA-Z]+)[^>]*>""")
private val VTT_TOP_LINE = Regex("""line:\s*(?:0|1|2|-?[0-9]%|1[0-9]%)(?:[,\s]|$)""")

internal fun parseCueTimestamp(ts: String): Long {
    val clean = ts.trim().replace(',', '.')
    val main = clean.substringBefore('.')
    val frac = clean.substringAfter('.', "0").padEnd(3, '0').take(3).toLongOrNull() ?: 0L
    val parts = main.split(':').map { it.toLongOrNull() ?: 0L }
    val (h, m, s) = when (parts.size) {
        3 -> Triple(parts[0], parts[1], parts[2])
        2 -> Triple(0L, parts[0], parts[1])
        else -> return 0L
    }
    return (h * 3_600_000L + m * 60_000L + s * 1_000L + frac).coerceAtLeast(0L)
}

private fun cleanCueText(raw: String): String {
    var t = raw.replace(BRACE_TAG, "")
    t = HTML_TAG.replace(t) { m ->
        val name = m.groupValues[2].lowercase()
        if (name == "i" || name == "b") "<${m.groupValues[1]}$name>" else ""
    }
    t = t.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&nbsp;", " ").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("\\N", "\n")
    return t.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
}

internal fun parseSubtitleText(content: String): List<SubtitleCue> {
    val lines = content.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val cues = mutableListOf<SubtitleCue>()
    var i = 0
    while (i < lines.size) {
        val m = CUE_TIME.find(lines[i])
        if (m == null) { i++; continue }
        val start = parseCueTimestamp(m.groupValues[1])
        val end = parseCueTimestamp(m.groupValues[2])
        var top = VTT_TOP_LINE.containsMatchIn(m.groupValues[3])
        val buf = mutableListOf<String>()
        i++
        while (i < lines.size && lines[i].isNotBlank() && CUE_TIME.find(lines[i]) == null) {
            val l = lines[i]
            // numero do proximo bloco SRT colado sem linha em branco
            val nextIsTime = i + 1 < lines.size && CUE_TIME.find(lines[i + 1]) != null
            if (!(nextIsTime && l.trim().all { it.isDigit() })) buf.add(l)
            i++
        }
        val raw = buf.joinToString("\n")
        if (ASS_TOP.containsMatchIn(raw)) top = true
        val text = cleanCueText(raw)
        if (text.isNotEmpty() && end > start) cues.add(SubtitleCue(start, end, text, top))
    }
    return cues.sortedBy { it.startMs }
}

/** Converte <i>/<b> em estilo; o resto vira texto. */
internal fun cueToAnnotated(text: String): AnnotatedString = buildAnnotatedString {
    var italic = 0
    var bold = 0
    var idx = 0
    val tag = Regex("""</?[ib]>""")
    for (m in tag.findAll(text)) {
        if (m.range.first > idx) {
            val seg = text.substring(idx, m.range.first)
            pushStyle(
                SpanStyle(
                    fontStyle = if (italic > 0) FontStyle.Italic else FontStyle.Normal,
                    fontWeight = if (bold > 0) FontWeight.Bold else null,
                ),
            )
            append(seg)
            pop()
        }
        when (m.value) {
            "<i>" -> italic++
            "</i>" -> italic = (italic - 1).coerceAtLeast(0)
            "<b>" -> bold++
            "</b>" -> bold = (bold - 1).coerceAtLeast(0)
        }
        idx = m.range.last + 1
    }
    if (idx < text.length) {
        pushStyle(
            SpanStyle(
                fontStyle = if (italic > 0) FontStyle.Italic else FontStyle.Normal,
                fontWeight = if (bold > 0) FontWeight.Bold else null,
            ),
        )
        append(text.substring(idx))
        pop()
    }
}

// ---------------------------------------------------------------------------
// Aparencia da legenda (vale para legenda do video e online), salva no aparelho.
// ---------------------------------------------------------------------------

internal enum class SubSize(val label: String, val sp: Float) {
    SMALL("Pequena", 15f), MEDIUM("Média", 18f), LARGE("Grande", 22f), XL("Enorme", 27f)
}

internal enum class SubColor(val label: String, val argb: Int) {
    WHITE("Branca", 0xFFFFFFFF.toInt()), YELLOW("Amarela", 0xFFFFE14D.toInt()), GREEN("Verde", 0xFF7CFC9A.toInt())
}

internal enum class SubBackground(val label: String) {
    OUTLINE("Contorno"), SHADOW("Sombra"), BOX("Caixa escura"), NONE("Nenhum")
}

internal data class SubtitleStyle(
    val size: SubSize = SubSize.MEDIUM,
    val color: SubColor = SubColor.WHITE,
    val background: SubBackground = SubBackground.OUTLINE,
) {
    val composeColor: Color get() = Color(color.argb)
}

internal object SubtitleStylePrefs {
    private const val PREFS = "streamflix_subtitle_style"

    fun load(context: Context): SubtitleStyle {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return SubtitleStyle(
            size = SubSize.entries.firstOrNull { it.name == p.getString("size", null) } ?: SubSize.MEDIUM,
            color = SubColor.entries.firstOrNull { it.name == p.getString("color", null) } ?: SubColor.WHITE,
            background = SubBackground.entries.firstOrNull { it.name == p.getString("bg", null) } ?: SubBackground.OUTLINE,
        )
    }

    fun save(context: Context, style: SubtitleStyle) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("size", style.size.name)
            .putString("color", style.color.name)
            .putString("bg", style.background.name)
            .apply()
    }
}

internal fun <T : Enum<T>> nextOf(value: T, all: List<T>): T = all[(all.indexOf(value) + 1) % all.size]

/** Aplica o estilo nas legendas do proprio video (SubtitleView do ExoPlayer). */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun applySubtitleStyle(view: SubtitleView, style: SubtitleStyle) {
    view.setApplyEmbeddedStyles(false)
    view.setApplyEmbeddedFontSizes(false)
    val (bg, edgeType) = when (style.background) {
        SubBackground.OUTLINE -> AColor.TRANSPARENT to CaptionStyleCompat.EDGE_TYPE_OUTLINE
        SubBackground.SHADOW -> AColor.TRANSPARENT to CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
        SubBackground.BOX -> 0xB3000000.toInt() to CaptionStyleCompat.EDGE_TYPE_NONE
        SubBackground.NONE -> AColor.TRANSPARENT to CaptionStyleCompat.EDGE_TYPE_NONE
    }
    view.setStyle(CaptionStyleCompat(style.color.argb, bg, AColor.TRANSPARENT, edgeType, AColor.BLACK, null))
    view.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, style.size.sp)
}
