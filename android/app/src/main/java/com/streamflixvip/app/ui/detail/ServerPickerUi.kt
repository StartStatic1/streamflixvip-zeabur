package com.streamflixvip.app.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamflixvip.app.network.VipSource

private val Amber = Color(0xFFFFB547)
private val GoldVip = Color(0xFFE8A317)
private val BlueBadge = Color(0xFF3B82F6)
private val PurpleLeg = Color(0xFF6E63E0)
private val CardNavy = Color(0xFF141A24)
private val CardNavyHi = Color(0xFF1C2433)

private val PlayAccents = listOf(
    Color(0xFFFFB547),
    Color(0xFF7C6CFF),
    Color(0xFF2DD4BF),
    Color(0xFFF59E0B),
    Color(0xFFFB7185),
    Color(0xFF38BDF8),
    Color(0xFFA78BFA),
    Color(0xFF34D399),
)

internal fun isAddonSourceLabel(label: String?): Boolean {
    val l = label.orEmpty()
    val host = l.split("·", "•", "|").firstOrNull()?.trim().orEmpty()
    if (host.equals("StreamFlix.Svent", ignoreCase = true)) return false
    if (host.equals("StreamFlix.maxcine", ignoreCase = true)) return false
    if (host.equals("StreamFlix.dflix", ignoreCase = true)) return false
    if (host.startsWith("StreamFlix.", ignoreCase = true)) return true
    return host.startsWith("Fenix", ignoreCase = true) ||
        host.startsWith("Frost", ignoreCase = true) ||
        host.startsWith("King", ignoreCase = true) ||
        host.startsWith("BsCine", ignoreCase = true) ||
        host.startsWith("PopPlay", ignoreCase = true) ||
        host.startsWith("Addon", ignoreCase = true) ||
        host.startsWith("Nuvio", ignoreCase = true) ||
        host.startsWith("IPTV Bridge", ignoreCase = true) ||
        host.startsWith("FrostStream", ignoreCase = true) ||
        host.startsWith("HdHub", ignoreCase = true) ||
        host.startsWith("Comet", ignoreCase = true) ||
        host.startsWith("Tordb", ignoreCase = true) ||
        host.startsWith("TorrentsDB", ignoreCase = true)
}

private fun isBrandWord(word: String): Boolean {
    val w = word.lowercase().replace(" ", "")
    return w == "streamflix" || w == "stremflix" || w == "streamflixvip" || w == "addon"
}

private fun titleCaseWord(word: String): String {
    if (word.isBlank()) return word
    val known = mapOf(
        "fenix" to "Fenix",
        "frost" to "Frost",
        "froststream" to "Frost",
        "vexio" to "Vexio",
        "vulke" to "Vulke",
        "wova" to "Wova",
        "tplay" to "Tplay",
        "flexone" to "Flexone",
        "expacix" to "Expacix",
        "hdhub" to "HdHub",
        "bscine" to "BsCine",
        "popplay" to "PopPlay",
        "comet" to "Comet",
        "nuvio" to "Nuvio",
        "pengu" to "Pengu",
        "gndk" to "Gndk",
        "cdnz" to "Cdnz",
        "diex" to "Diex",
        "cebix" to "Cebix",
        "webstream" to "Webstream",
        "tordb" to "Tordb",
    )
    known[word.lowercase()]?.let { return it }
    return word.lowercase().replaceFirstChar { it.titlecase() }
}

internal fun hostTitleFromLabel(label: String?): String {
    val raw = label?.trim().orEmpty()
    if (raw.isEmpty()) return "Servidor"
    var host = raw.split("·", "•").firstOrNull()?.trim().orEmpty()
    if (host.isBlank()) host = raw
    host = host.replace(Regex("(?i)^(stream\\s*flix|strem\\s*flix|streamflix|stremflix|addon)[._\\-\\s]*"), "")
    host = host.trim('.', '-', '_', ' ')
    if (host.isBlank()) return "Servidor"
    val pretty = host.split(Regex("[._\\-\\s]+"))
        .filter { it.isNotBlank() && !isBrandWord(it) }
        .joinToString(" ") { titleCaseWord(it) }
    return pretty.ifBlank { "Servidor" }.take(28)
}

internal fun qualityFromSource(source: VipSource): String? {
    source.meta?.quality?.takeIf { it.isNotBlank() }?.let { return it }
    val t = source.source_label.orEmpty().lowercase()
    return when {
        Regex("\\b(2160p?|4k|uhd)\\b").containsMatchIn(t) -> "4K"
        Regex("\\b1080p?\\b").containsMatchIn(t) -> "1080p"
        Regex("\\b720p?\\b").containsMatchIn(t) -> "720p"
        Regex("\\b(480p?|360p?)\\b").containsMatchIn(t) -> "SD"
        else -> null
    }
}

internal fun audioFromSource(source: VipSource): String? {
    source.meta?.audio?.takeIf { it.isNotBlank() }?.let { return it }
    val t = source.source_label.orEmpty().lowercase()
    return when {
        Regex("dublad|\\bdub\\b|dual\\s*audio").containsMatchIn(t) -> "Dublado"
        Regex("legendad|\\bleg\\b|subtitle").containsMatchIn(t) -> "Legendado"
        else -> null
    }
}

internal fun originFromSource(source: VipSource): String? =
    source.meta?.origin?.takeIf { it.isNotBlank() }

internal fun sizeFromSource(source: VipSource): String? =
    source.meta?.size?.takeIf { it.isNotBlank() }

/** Painel manda. Numero menor sobe. Qualidade so desempata. */
internal fun sourceDisplayRank(source: VipSource): Int {
    val p = (source.priority ?: 50).coerceIn(0, 999)
    val q = when (qualityFromSource(source)) {
        "4K" -> 0
        "1080p" -> 1
        "720p" -> 2
        "SD" -> 3
        else -> 8
    }
    return p * 10 + q
}

internal fun serversAvailableLabel(count: Int, loading: Boolean = false): String {
    return when {
        loading && count == 0 -> "Procurando servidores…"
        count <= 0 -> "Nenhum servidor neste título"
        count == 1 -> "1 servidor disponível"
        else -> "$count servidores disponíveis"
    }
}

@Composable
fun ServerSheetTitle(title: String, subtitle: String) {
    Column(modifier.padding(bottom = 12.dp)) {
        Text(
            title,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = (-0.3).sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
        )
    }
}

@Composable
fun ServerSectionLabel(text: String, accent: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = accent.copy(alpha = 0.95f),
        )
    }
}

@Composable
private fun MetaPill(text: String, fg: Color, bg: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = bg) {
        Text(
            text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun ServerSourceCard(
    source: VipSource,
    isRecommended: Boolean,
    isLockedForFree: Boolean,
    onClick: () -> Unit,
    onLockedClick: () -> Unit,
    index: Int = 0,
) {
    val badge = qualityFromSource(source)
    val audio = audioFromSource(source)
    val origin = originFromSource(source)
    val size = sizeFromSource(source)
    val title = hostTitleFromLabel(source.source_label)
    val accent = PlayAccents[index.coerceAtLeast(0) % PlayAccents.size]
    val pillText = when {
        isLockedForFree -> "VIP"
        badge != null -> badge
        audio != null -> audio
        origin != null -> origin
        size != null -> size
        else -> null
    }
    val pillFg = when {
        pillText == "VIP" -> GoldVip
        pillText in listOf("4K", "1080p") -> Color(0xFFDCEBFF)
        pillText == "720p" -> Amber
        pillText == "Dublado" -> Color(0xFFD1FAE5)
        pillText == "Legendado" -> Color(0xFFE8E4FF)
        pillText == "Original" -> Color(0xFFFFE8F0)
        origin != null && pillText == origin -> Color(0xFFE0FFFA)
        size != null && pillText == size -> Color(0xFFFFF4D6)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val pillBg = when {
        pillText == "VIP" -> GoldVip.copy(alpha = 0.18f)
        pillText in listOf("4K", "1080p") -> BlueBadge.copy(alpha = 0.34f)
        pillText == "720p" -> Amber.copy(alpha = 0.18f)
        pillText == "Dublado" -> Color(0xFF34D399).copy(alpha = 0.18f)
        pillText == "Legendado" -> PurpleLeg.copy(alpha = 0.28f)
        pillText == "Original" -> Color(0xFFFB7185).copy(alpha = 0.28f)
        origin != null && pillText == origin -> Color(0xFF2DD4BF).copy(alpha = 0.28f)
        size != null && pillText == size -> Color(0xFFFBBF24).copy(alpha = 0.28f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val highlight = isRecommended && !isLockedForFree
    val number = (index + 1).toString().padStart(2, '0')

    Surface(
        onClick = if (isLockedForFree) onLockedClick else onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        border = BorderStroke(
            width = if (highlight) 1.4.dp else 1.dp,
            color = when {
                isLockedForFree -> GoldVip.copy(alpha = 0.38f)
                highlight -> Amber.copy(alpha = 0.72f)
                else -> Color.White.copy(alpha = 0.06f)
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .background(
                    if (highlight) {
                        Brush.horizontalGradient(
                            listOf(
                                Amber.copy(alpha = 0.20f),
                                CardNavyHi.copy(alpha = 0.96f),
                                CardNavy,
                            ),
                        )
                    } else {
                        Brush.horizontalGradient(listOf(CardNavyHi, CardNavy))
                    },
                )
                .height(58.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    number,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (highlight) Amber else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.width(22.dp),
                )
                Box(
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .width(1.dp)
                        .height(18.dp)
                        .background(Color.White.copy(alpha = if (highlight) 0.18f else 0.08f)),
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(accent, accent.copy(alpha = 0.55f)),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLockedForFree) {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = GoldVip,
                            modifier = Modifier.size(15.dp),
                        )
                    } else {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isLockedForFree) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        } else {
                            Color.White
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (pillText != null) MetaPill(pillText, pillFg, pillBg)
                }
                if (highlight) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Amber,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(16.dp),
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
