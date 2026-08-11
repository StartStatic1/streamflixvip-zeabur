#!/usr/bin/env python3
"""Layout: categorias na esquerda, lista na direita; remove chips horizontais de categoria."""
from pathlib import Path
import re

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/livetv/LiveTvScreen.kt")
t = p.read_text()

if "fillMaxHeight" not in t:
    t = t.replace(
        "import androidx.compose.foundation.layout.fillMaxSize",
        "import androidx.compose.foundation.layout.fillMaxHeight\nimport androidx.compose.foundation.layout.fillMaxSize",
        1,
    )

if "SideBg" not in t:
    t = t.replace(
        "private val ScreenBg = Color(0xFF0A0A10)",
        "private val ScreenBg = Color(0xFF0A0A10)\nprivate val SideBg = Color(0xFF0E0E16)",
        1,
    )

old_cats = '''        // \u2500\u2500 Categorias (s\u00f3 na aba Canais) \u2500\u2500
        if (state.tab == LiveTvTab.CHANNELS && state.searchQuery.isEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.categories, key = { it.id }) { cat ->
                    val selectedCat = state.selectedCategoryId == cat.id
                    FilterChip(
                        selected = selectedCat,
                        onClick = { viewModel.selectCategory(cat.id) },
                        label = {
                            Text(
                                cat.name,
                                fontSize = 12.sp,
                                fontWeight = if (selectedCat) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Accent.copy(alpha = 0.22f),
                            selectedLabelColor = Accent,
                            containerColor = Color.White.copy(alpha = 0.05f),
                            labelColor = Color.White.copy(alpha = 0.7f),
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = Color.White.copy(alpha = 0.1f),
                            selectedBorderColor = Accent.copy(alpha = 0.45f),
                            enabled = true,
                            selected = selectedCat,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }

'''
# Prefer regex for unicode dashes
m = re.search(
    r"\n\s*// [^\n]*Categorias[^\n]*\n\s*if \(state\.tab == LiveTvTab\.CHANNELS && state\.searchQuery\.isEmpty\(\)\) \{\n\s*LazyRow\([\s\S]*?Spacer\(Modifier\.height\(6\.dp\)\)\n\s*\}\n",
    t,
)
if m:
    t = t[: m.start()] + "\n" + t[m.end() :]
    print("removed cats via regex")
elif old_cats in t:
    t = t.replace(old_cats, "", 1)
    print("removed horizontal cats")
else:
    print("WARN cats block")

old_list = '''            list.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (state.tab == LiveTvTab.FAVORITES) "Nenhum favorito ainda.\nToque no cora\u00e7\u00e3o no player."
                        else "Nenhum canal encontrado",
                        color = Color.White.copy(alpha = 0.45f),
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp,
                    )
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(list, key = { it.id }) { channel ->
                        val isSel = channel.id == state.selectedChannelId
                        val isFav = state.favoriteIds.contains(channel.id)
                        ChannelRow(
                            channel = channel,
                            selected = isSel,
                            isFavorite = isFav,
                            onClick = { viewModel.selectChannel(channel) },
                            onToggleFavorite = { viewModel.toggleFavorite(channel.id) },
                            onOpenFullscreen = { onChannelClick(channel) },
                        )
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }'''

# Use actual UTF-8 from file pattern via regex
m2 = re.search(
    r"list\.isEmpty\(\) -> \{[\s\S]*?else -> \{\n\s*LazyColumn\([\s\S]*?item \{ Spacer\(Modifier\.height\(72\.dp\)\) \}\n\s*\}\n\s*\}",
    t,
)
new_list = '''else -> {
                Row(Modifier.fillMaxSize()) {
                    if (state.tab == LiveTvTab.CHANNELS &&
                        state.searchQuery.isEmpty() &&
                        state.brandFilter == null
                    ) {
                        LazyColumn(
                            Modifier
                                .width(108.dp)
                                .fillMaxHeight()
                                .background(SideBg)
                                .padding(vertical = 4.dp),
                        ) {
                            items(state.categories, key = { it.id }) { cat ->
                                val sel = state.selectedCategoryId == cat.id
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectCategory(cat.id) }
                                        .background(if (sel) Accent.copy(alpha = 0.16f) else Color.Transparent)
                                        .padding(vertical = 11.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier
                                            .width(3.dp)
                                            .height(16.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(if (sel) Accent else Color.Transparent),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        cat.name,
                                        color = if (sel) Accent else Color.White.copy(alpha = 0.55f),
                                        fontSize = 12.sp,
                                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 14.sp,
                                    )
                                }
                            }
                            item { Spacer(Modifier.height(72.dp)) }
                        }
                    }
                    if (list.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            Text(
                                if (state.tab == LiveTvTab.FAVORITES)
                                    "Nenhum favorito ainda.\nToque no cora\u00e7\u00e3o no player."
                                else "Nenhum canal nesta categoria",
                                color = Color.White.copy(alpha = 0.45f),
                                textAlign = TextAlign.Center,
                                fontSize = 13.sp,
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        ) {
                            items(list, key = { it.id }) { channel ->
                                val isSel = channel.id == state.selectedChannelId
                                val isFav = state.favoriteIds.contains(channel.id)
                                ChannelRow(
                                    channel = channel,
                                    selected = isSel,
                                    isFavorite = isFav,
                                    onClick = { viewModel.selectChannel(channel) },
                                    onToggleFavorite = { viewModel.toggleFavorite(channel.id) },
                                    onOpenFullscreen = { onChannelClick(channel) },
                                )
                            }
                            item { Spacer(Modifier.height(72.dp)) }
                        }
                    }
                }
            }'''
# fix unicode in new_list for heart message
new_list = new_list.replace("cora\\u00e7\\u00e3o", "coração")

if m2:
    t = t[: m2.start()] + new_list + t[m2.end() :]
    print("list layout replaced")
else:
    print("WARN list block")

p.write_text(t)
print("DONE")
