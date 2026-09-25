#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]

p = ROOT / "android/app/src/main/java/com/streamflixvip/app/network/SupabaseApi.kt"
t = p.read_text(encoding="utf-8")
if "data class SourceMeta" not in t:
    old = """@JsonClass(generateAdapter = true)\ndata class VipSource(\n    val source_url: String,\n    val source_label: String?,\n    val priority: Int?,\n) {"""
    new = """@JsonClass(generateAdapter = true)\ndata class SourceMeta(\n    val quality: String? = null,\n    val audio: String? = null,\n    val origin: String? = null,\n    val size: String? = null,\n)\n\n@JsonClass(generateAdapter = true)\ndata class VipSource(\n    val source_url: String,\n    val source_label: String?,\n    val priority: Int?,\n    val meta: SourceMeta? = null,\n) {"""
    if old not in t:
        raise SystemExit("SupabaseApi: block not found")
    p.write_text(t.replace(old, new, 1), encoding="utf-8")
    print("SupabaseApi: ok")
else:
    print("SupabaseApi: already ok")

p = ROOT / "android/app/src/main/java/com/streamflixvip/app/ui/detail/ServerPickerUi.kt"
t = p.read_text(encoding="utf-8")
if "source.meta?.quality" not in t:
    t = t.replace(
        "internal fun qualityFromSource(source: VipSource): String? {\n    val t = source.source_label",
        "internal fun qualityFromSource(source: VipSource): String? {\n    source.meta?.quality?.takeIf { it.isNotBlank() }?.let { return it }\n    val t = source.source_label",
        1,
    )
    t = t.replace(
        "internal fun audioFromSource(source: VipSource): String? {\n    val t = source.source_label",
        "internal fun audioFromSource(source: VipSource): String? {\n    source.meta?.audio?.takeIf { it.isNotBlank() }?.let { return it }\n    val t = source.source_label",
        1,
    )
    if "fun originFromSource" not in t:
        t = t.replace(
            "/** Painel manda. Numero menor sobe. Qualidade so desempata. */",
            "internal fun originFromSource(source: VipSource): String? =\n"
            "    source.meta?.origin?.takeIf { it.isNotBlank() }\n\n"
            "internal fun sizeFromSource(source: VipSource): String? =\n"
            "    source.meta?.size?.takeIf { it.isNotBlank() }\n\n"
            "/** Painel manda. Numero menor sobe. Qualidade so desempata. */",
            1,
        )
    old_block = (
        '    val badge = qualityFromSource(source)\n'
        '    val audio = audioFromSource(source)\n'
        '    val title = hostTitleFromLabel(source.source_label)\n'
        '    val accent = PlayAccents[index.coerceAtLeast(0) % PlayAccents.size]\n'
        '    val pillText = when {\n'
        '        isLockedForFree -> "VIP"\n'
        '        badge != null -> badge\n'
        '        audio != null -> audio\n'
        '        else -> null\n'
        '    }\n'
        '    val pillFg = when (pillText) {\n'
        '        "VIP" -> GoldVip\n'
        '        "4K", "1080p" -> Color(0xFFDCEBFF)\n'
        '        "720p" -> Amber\n'
        '        "Dublado" -> Color(0xFFD1FAE5)\n'
        '        "Legendado" -> Color(0xFFE8E4FF)\n'
        '        else -> MaterialTheme.colorScheme.onSurfaceVariant\n'
        '    }\n'
        '    val pillBg = when (pillText) {\n'
        '        "VIP" -> GoldVip.copy(alpha = 0.18f)\n'
        '        "4K", "1080p" -> BlueBadge.copy(alpha = 0.34f)\n'
        '        "720p" -> Amber.copy(alpha = 0.18f)\n'
        '        "Dublado" -> Color(0xFF34D399).copy(alpha = 0.18f)\n'
        '        "Legendado" -> PurpleLeg.copy(alpha = 0.28f)\n'
        '        else -> MaterialTheme.colorScheme.surfaceVariant\n'
        '    }\n'
    )
    new_block = (
        '    val badge = qualityFromSource(source)\n'
        '    val audio = audioFromSource(source)\n'
        '    val origin = originFromSource(source)\n'
        '    val size = sizeFromSource(source)\n'
        '    val title = hostTitleFromLabel(source.source_label)\n'
        '    val accent = PlayAccents[index.coerceAtLeast(0) % PlayAccents.size]\n'
        '    val pillText = when {\n'
        '        isLockedForFree -> "VIP"\n'
        '        badge != null -> badge\n'
        '        audio != null -> audio\n'
        '        origin != null -> origin\n'
        '        size != null -> size\n'
        '        else -> null\n'
        '    }\n'
        '    val pillFg = when {\n'
        '        pillText == "VIP" -> GoldVip\n'
        '        pillText in listOf("4K", "1080p") -> Color(0xFFDCEBFF)\n'
        '        pillText == "720p" -> Amber\n'
        '        pillText == "Dublado" -> Color(0xFFD1FAE5)\n'
        '        pillText == "Legendado" -> Color(0xFFE8E4FF)\n'
        '        pillText == "Original" -> Color(0xFFFFE8F0)\n'
        '        origin != null && pillText == origin -> Color(0xFFE0FFFA)\n'
        '        size != null && pillText == size -> Color(0xFFFFF4D6)\n'
        '        else -> MaterialTheme.colorScheme.onSurfaceVariant\n'
        '    }\n'
        '    val pillBg = when {\n'
        '        pillText == "VIP" -> GoldVip.copy(alpha = 0.18f)\n'
        '        pillText in listOf("4K", "1080p") -> BlueBadge.copy(alpha = 0.34f)\n'
        '        pillText == "720p" -> Amber.copy(alpha = 0.18f)\n'
        '        pillText == "Dublado" -> Color(0xFF34D399).copy(alpha = 0.18f)\n'
        '        pillText == "Legendado" -> PurpleLeg.copy(alpha = 0.28f)\n'
        '        pillText == "Original" -> Color(0xFFFB7185).copy(alpha = 0.28f)\n'
        '        origin != null && pillText == origin -> Color(0xFF2DD4BF).copy(alpha = 0.28f)\n'
        '        size != null && pillText == size -> Color(0xFFFBBF24).copy(alpha = 0.28f)\n'
        '        else -> MaterialTheme.colorScheme.surfaceVariant\n'
        '    }\n'
    )
    if old_block not in t:
        raise SystemExit("ServerPickerUi: pill block not found")
    t = t.replace(old_block, new_block, 1)
    if "Tordb" not in t:
        t = t.replace(
            'host.startsWith("Comet", ignoreCase = true)',
            'host.startsWith("Comet", ignoreCase = true) ||\n'
            '        host.startsWith("Tordb", ignoreCase = true) ||\n'
            '        host.startsWith("TorrentsDB", ignoreCase = true)',
            1,
        )
    p.write_text(t, encoding="utf-8")
    print("ServerPickerUi: ok")
else:
    print("ServerPickerUi: already ok")
print("android chips applied")
