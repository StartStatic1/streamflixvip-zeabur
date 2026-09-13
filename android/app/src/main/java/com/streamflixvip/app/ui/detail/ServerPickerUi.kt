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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Star
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
private val BlueHd = Color(0xFF3B82F6)
private val OrangeSd = Color(0xFFF59E0B)
private val GreenDub = Color(0xFF34D399)
private val BlueLeg = Color(0xFF60A5FA)

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

private fun titleCaseWord(word: String): String {
    if (word.isBlank()) return word
    val known = mapOf(
        "fenix" to "Fenix",
        "frost" to "Frost",
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
    )
    val key = word.lowercase()
    known[key]?.let { return it }
    return word.lowercase().replaceFirstChar { it.titlecase() }
}

/** Nome apresentavel: sem StreamFlix., title case. Qualidade sai do titulo. */
internal fun hostTitleFromLabel(label: String?): String {
    val raw = label?.trim().orEmpty()
    if (raw.isEmpty()) return "Servidor"
    var host = raw.split("·", "•").firstOrNull()?.trim().orEmpty()
    if (host.isBlank()) host = raw
    host = host.replace(Regex("(?i)^streamflix[._\\-\\s]+"), "")
    host = host.replace(Regex("(?i)^addon[._\\-\\s]+"), "")
    host = host.trim('.', '-', '_', ' ')
    if (host.isBlank()) return "Servidor"
    val pretty = host.split(Regex("[._\\-\\s]+"))
        .filter { it.isNotBlank() }
        .joinToString(" ") { titleCaseWord(it) }
    return pretty.ifBlank { "Servidor" }.take(28)
}

/** Qualidade so no rotulo da fonte — URL gera falso 4K. */
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
private fun SeloChip(text: String, fg: Color, bg: Color) {
    Surface(shape = RoundedCornerShape(5.dp), color = bg) {
        Text(
            text.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp,
            color = fg,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
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
    val hasMeta = badge != null || audio != null || isRecommended || isLockedForFree
    val accent = when {
        isLockedForFree -> GoldVip
        isRecommended -> Amber
        else -> Amber.copy(alpha = 0.85f)
    }
    val badgeBg = when (badge) {
        "4K" -> GoldVip.copy(alpha = 0.22f)
        "1080p" -> BlueHd.copy(alpha = 0.22f)
        "720p" -> OrangeSd.copy(alpha = 0.20f)
        "SD" -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val badgeFg = when (badge) {
        "4K" -> GoldVip
        "1080p" -> Color(0xFF93C5FD)
        "720p" -> OrangeSd
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = if (isLockedForFree) onLockedClick else onClick,
        shape = RoundedCornerShape(14.dp),
        color = when {
            isLockedForFree -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
            isRecommended -> Amber.copy(alpha = 0.10f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        },
        border = when {
            isLockedForFree -> BorderStroke(1.dp, GoldVip.copy(alpha = 0.38f))
            isRecommended -> BorderStroke(1.dp, Amber.copy(alpha = 0.45f))
            else -> BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
        },
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 14.dp,
                vertical = if (hasMeta) 12.dp else 13.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.08f)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isLockedForFree -> Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = GoldVip,
                        modifier = Modifier.size(16.dp),
                    )
                    isRecommended -> Icon(
                        Icons.Outlined.Star,
                        contentDescription = null,
                        tint = Amber,
                        modifier = Modifier.size(16.dp),
                    )
                    else -> Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.15.sp,
                    color = if (isLockedForFree) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (hasMeta) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        if (isLockedForFree) SeloChip("PREMIUM", GoldVip, GoldVip.copy(alpha = 0.18f))
                        else if (isRecommended) SeloChip("TOP", Amber, Amber.copy(alpha = 0.16f))
                        if (!isLockedForFree && badge != null) SeloChip(badge, badgeFg, badgeBg)
                        if (!isLockedForFree && audio != null) {
                            val c = if (audio == "Dublado") GreenDub else BlueLeg
                            SeloChip(audio, c, c.copy(alpha = 0.16f))
                        }
                    }
                }
            }
        }
    }
}
