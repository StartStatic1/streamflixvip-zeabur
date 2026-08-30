package com.streamflixvip.app.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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

/** Cores do sheet — recomendado roxo; demais neutros; badges com contraste. */
private val PurpleRec = Color(0xFF7C5CFF)
private val CyanAccent = Color(0xFF2EC4B6)
private val GoldVip = Color(0xFFE8A317)
private val BlueHd = Color(0xFF3B82F6)
private val OrangeSd = Color(0xFFF59E0B)

internal fun isAddonSourceLabel(label: String?): Boolean {
    val l = label.orEmpty()
    // IPTV nativo StreamFlix.* (Svent, maxcine, etc.) NAO conta como add-on
    val host = l.split("·", "•", "|").firstOrNull()?.trim().orEmpty()
    if (host.equals("StreamFlix.Svent", ignoreCase = true)) return false
    if (host.equals("StreamFlix.maxcine", ignoreCase = true)) return false
    if (host.equals("StreamFlix.dflix", ignoreCase = true)) return false
    if (host.startsWith("StreamFlix.", ignoreCase = true)) return true // Fenix etc no painel
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

/** So o nome do servidor (sem 720p / Dublado no titulo). */
private fun hostTitleFromLabel(label: String?): String {
    val raw = label?.trim().orEmpty()
    if (raw.isEmpty()) return "Servidor"
    val host = raw.split("·", "•").firstOrNull()?.trim().orEmpty()
    return host.ifBlank { raw }.take(28)
}

private fun qualityFromLabel(label: String?): String? {
    if (label == null) return null
    val u = label.uppercase()
    return listOf("4K", "2160P", "1080P", "720P", "HD", "SD").firstOrNull { u.contains(it) }
        ?.let { if (it == "2160P") "4K" else it }
}

private fun audioFromLabel(label: String?): String? {
    val l = label.orEmpty()
    return when {
        l.contains("Dublado", ignoreCase = true) -> "Dublado"
        l.contains("Legendado", ignoreCase = true) -> "Legendado"
        else -> null
    }
}

@Composable
fun ServerSheetTitle(title: String, subtitle: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)) {
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
                .height(14.dp)
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

/**
 * Card de servidor no sheet.
 * Sem texto "IPTV" / "Add-on" — so RECOMENDADO, PREMIUM e audio se houver.
 */
@Composable
private fun SeloChip(text: String, fg: Color, bg: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg,
    ) {
        Text(
            text.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
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
) {
    val badge = qualityFromLabel(source.source_label)
    val audio = audioFromLabel(source.source_label)
    val accent = when {
        isLockedForFree -> GoldVip
        isRecommended -> PurpleRec
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
    }
    // Badges com contraste forte (720p nao usa ciano do card antigo)
    val badgeBg = when (badge?.uppercase()) {
        "4K" -> GoldVip.copy(alpha = 0.28f)
        "1080P" -> BlueHd.copy(alpha = 0.28f)
        "720P" -> OrangeSd.copy(alpha = 0.26f)
        "HD" -> BlueHd.copy(alpha = 0.20f)
        "SD" -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val badgeFg = when (badge?.uppercase()) {
        "4K" -> GoldVip
        "1080P" -> Color(0xFF60A5FA)
        "720P" -> OrangeSd
        "HD" -> Color(0xFF60A5FA)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = if (isLockedForFree) onLockedClick else onClick,
        shape = RoundedCornerShape(16.dp),
        color = when {
            isLockedForFree -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
            isRecommended -> PurpleRec.copy(alpha = 0.28f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
        border = when {
            isLockedForFree -> BorderStroke(1.dp, GoldVip.copy(alpha = 0.40f))
            isRecommended -> BorderStroke(1.5.dp, PurpleRec.copy(alpha = 0.55f))
            else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
        },
        tonalElevation = if (isRecommended) 4.dp else 0.dp,
        shadowElevation = if (isRecommended) 3.dp else 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(accent.copy(alpha = 0.35f), accent.copy(alpha = 0.12f)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isLockedForFree -> Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = GoldVip,
                        modifier = Modifier.size(20.dp),
                    )
                    isRecommended -> Icon(
                        Icons.Outlined.Star,
                        contentDescription = null,
                        tint = PurpleRec,
                        modifier = Modifier.size(20.dp),
                    )
                    else -> Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    hostTitleFromLabel(source.source_label),
                    fontSize = 14.sp,
                    fontWeight = if (isLockedForFree) FontWeight.Normal else FontWeight.SemiBold,
                    color = if (isLockedForFree) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                // Selos compactos (qualidade + audio + status)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLockedForFree) {
                        SeloChip("PREMIUM", GoldVip, GoldVip.copy(alpha = 0.2f))
                    } else if (isRecommended) {
                        SeloChip("RECOMENDADO", PurpleRec, PurpleRec.copy(alpha = 0.2f))
                    }
                    if (!isLockedForFree && badge != null) {
                        if (isRecommended) Spacer(Modifier.width(6.dp))
                        SeloChip(badge, badgeFg, badgeBg)
                    }
                    if (!isLockedForFree && audio != null) {
                        Spacer(Modifier.width(6.dp))
                        val audioColor = if (audio == "Dublado") Color(0xFF34D399) else Color(0xFF60A5FA)
                        SeloChip(audio, audioColor, audioColor.copy(alpha = 0.18f))
                    }
                }
            }
        }
    }
}
