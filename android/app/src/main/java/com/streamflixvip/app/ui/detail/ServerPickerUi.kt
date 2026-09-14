package com.streamflixvip.app.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamflixvip.app.network.VipSource

private val Amber = Color(0xFFFFB547)
private val GoldVip = Color(0xFFE8A317)
private val BlueHd = Color(0xFF3B82F6)
private val PurpleLeg = Color(0xFF7C6CF0)
private val GreenOk = Color(0xFF34D399)

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
        host.startsWith("Comet", ignoreCase = true)
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
    )
    val key = word.lowercase()
    known[key]?.let { return it }
    return word.lowercase().replaceFirstChar { it.titlecase() }
}

/** Nome no card: tira StreamFlix / Stremflix / Addon e deixa title case. */
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
    val t = source.source_label.orEmpty().lowercase()
    return when {
        Regex("dublad|\\bdub\\b|dual\\s*audio").containsMatchIn(t) -> "Dublado"
        Regex("legendad|\\bleg\\b|subtitle").containsMatchIn(t) -> "Legendado"
        else -> null
    }
}

private fun statusLine(isRecommended: Boolean, badge: String?, audio: String?): String {
    val bits = mutableListOf<String>()
    if (isRecommended) bits.add("Recomendado")
    if (badge != null && !isRecommended) bits.add(badge)
    else if (badge != null && isRecommended) bits.add(badge)
    if (audio != null) bits.add(audio.lowercase())
    return bits.joinToString(" · ")
}

@Composable
fun ServerSheetTitle(title: String, subtitle: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp)) {
        Text(
            title,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun InlineSelo(text: String, fg: Color, bg: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = bg) {
        Text(
            text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.2.sp,
            color = fg,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
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
) {
    val badge = qualityFromSource(source)
    val audio = audioFromSource(source)
    val title = hostTitleFromLabel(source.source_label)
    val status = statusLine(isRecommended && !isLockedForFree, badge, audio)
    val accent = when {
        isLockedForFree -> GoldVip
        isRecommended -> Amber
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val badgeFg = when (badge) {
        "4K" -> Color(0xFF93C5FD)
        "1080p" -> Color(0xFF93C5FD)
        "720p" -> Amber
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val badgeBg = when (badge) {
        "4K", "1080p" -> BlueHd.copy(alpha = 0.28f)
        "720p" -> Amber.copy(alpha = 0.18f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val dot = when {
        isLockedForFree -> GoldVip
        isRecommended -> GreenOk
        badge == "4K" || badge == "1080p" -> GreenOk.copy(alpha = 0.7f)
        badge == "720p" -> Amber
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }

    Surface(
        onClick = if (isLockedForFree) onLockedClick else onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(
            alpha = if (isRecommended && !isLockedForFree) 0.34f else 0.28f,
        ),
        border = when {
            isLockedForFree -> BorderStroke(1.dp, GoldVip.copy(alpha = 0.38f))
            isRecommended -> BorderStroke(1.2.dp, Amber.copy(alpha = 0.55f))
            else -> BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
        },
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isRecommended && !isLockedForFree) {
                            Color(0xFF1A2744)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isLockedForFree -> Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = GoldVip,
                        modifier = Modifier.size(18.dp),
                    )
                    else -> Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = if (isRecommended) Color(0xFF93C5FD) else accent.copy(alpha = 0.85f),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isLockedForFree) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (!isLockedForFree && badge != null) {
                        InlineSelo(badge, badgeFg, badgeBg)
                    }
                    if (!isLockedForFree && audio != null && badge == null) {
                        InlineSelo(
                            audio.lowercase(),
                            Color(0xFFD6D0FF),
                            PurpleLeg.copy(alpha = 0.35f),
                        )
                    }
                    if (isLockedForFree) {
                        InlineSelo("VIP", GoldVip, GoldVip.copy(alpha = 0.18f))
                    }
                }
                if (status.isNotBlank() || isLockedForFree) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(RoundedCornerShape(50))
                                .background(dot),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isLockedForFree) "Disponível no Premium" else status,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
