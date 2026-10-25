package com.streamflixvip.tv.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.itemsIndexed
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.streamflixvip.tv.data.LocalLibraryStore
import com.streamflixvip.tv.ui.components.LocalToaster
import com.streamflixvip.tv.ui.components.PosterCard
import com.streamflixvip.tv.ui.shell.FocusSignal
import com.streamflixvip.tv.ui.theme.LocalPalette
import kotlinx.coroutines.delay

/** Minha lista (favoritos do aparelho). Segurar OK remove. */
@Composable
fun MyListTvScreen(
    focusSignal: FocusSignal,
    onItemClick: (tmdbId: Int, mediaType: String) -> Unit,
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val libraryStore = remember { LocalLibraryStore(context) }
    var favorites by remember { mutableStateOf(libraryStore.getFavorites()) }
    val firstFocus = remember { FocusRequester() }
    var handledToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(focusSignal.token, favorites.isEmpty()) {
        if (handledToken == focusSignal.token || favorites.isEmpty()) return@LaunchedEffect
        delay(60)
        if (runCatching { firstFocus.requestFocus() }.isSuccess) handledToken = focusSignal.token
    }

    Column(Modifier.fillMaxSize().padding(start = 36.dp, top = 28.dp, end = 28.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Minha lista", color = p.text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(14.dp))
            Text(
                if (favorites.isEmpty()) "" else "${favorites.size} título${if (favorites.size == 1) "" else "s"} · segure OK para remover",
                color = p.textMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 5.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        if (favorites.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.BookmarkBorder, null, tint = p.accent, modifier = Modifier.size(54.dp))
                    Spacer(Modifier.height(14.dp))
                    Text("Sua lista está vazia", color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Nos detalhes de um título, toque em “Minha lista”.\nNa vitrine, segure OK em qualquer pôster.",
                        color = p.textMuted,
                        fontSize = 14.sp,
                    )
                }
            }
        } else {
            TvLazyVerticalGrid(
                columns = TvGridCells.Fixed(7),
                contentPadding = PaddingValues(top = 6.dp, bottom = 60.dp, end = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(favorites, key = { _, it -> "${it.tmdbId}_${it.mediaType}" }) { index, fav ->
                    Column {
                        PosterCard(
                            title = fav.title,
                            posterPath = fav.posterPath,
                            width = 200.dp,
                            modifier = Modifier.fillMaxWidth().let { if (index == 0) it.focusRequester(firstFocus) else it },
                            badge = if (fav.mediaType == "tv") "SÉRIE" else null,
                            onLongClick = {
                                libraryStore.removeFavorite(fav.tmdbId, fav.mediaType)
                                favorites = libraryStore.getFavorites()
                                toaster.show("“${fav.title}” saiu da Minha lista")
                                if (favorites.isNotEmpty()) focusSignal.request()
                            },
                            onClick = { onItemClick(fav.tmdbId, fav.mediaType) },
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(fav.title, color = p.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
