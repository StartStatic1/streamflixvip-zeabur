package com.streamflixvip.app.ui.reels

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.streamflixvip.app.network.NetworkModule
import com.streamflixvip.app.network.ReelStory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private enum class ReelsFilter { Todas, Continuar, Favoritas }

private fun isInProgress(prefs: SharedPreferences, id: String): Boolean {
    if (prefs.getBoolean("done_$id", false)) return false
    return prefs.contains("idx_$id") || prefs.all.keys.any { it.startsWith("pos_${id}_") }
}

sealed interface ReelsUiState {
    data object Loading : ReelsUiState
    data class Error(val message: String) : ReelsUiState
    data class Ready(val stories: List<ReelStory>) : ReelsUiState
}

class ReelsViewModel : ViewModel() {
    private val _state = MutableStateFlow<ReelsUiState>(ReelsUiState.Loading)
    val state: StateFlow<ReelsUiState> = _state
    init { refresh() }
    fun refresh() {
        viewModelScope.launch {
            _state.value = ReelsUiState.Loading
            val result = runCatching { NetworkModule.reelsApi.listStories() }
            val body = result.getOrNull()
            if (result.isFailure) {
                _state.value = ReelsUiState.Error(result.exceptionOrNull()?.message ?: "Falha ao carregar")
                return@launch
            }
            if (!body?.error.isNullOrBlank()) {
                _state.value = ReelsUiState.Error(body?.error ?: "Erro")
                return@launch
            }
            _state.value = ReelsUiState.Ready(body?.stories ?: emptyList())
        }
    }
}

@Composable
fun ReelsScreen(
    viewModel: ReelsViewModel,
    onStoryClick: (ReelStory) -> Unit,
) {
    val ui by viewModel.state.collectAsState()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("sfv_reels", Context.MODE_PRIVATE) }
    var filter by remember { mutableStateOf(ReelsFilter.Todas) }
    var tick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07070C))
            .padding(horizontal = 12.dp),
    ) {
        Text("Historias", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReelsFilter.entries.forEach { item ->
                val on = filter == item
                Text(
                    item.name,
                    color = if (on) Color.Black else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (on) Color(0xFF00E5FF) else Color(0xFF1B1B28))
                        .clickable { filter = item }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        when (val s = ui) {
            is ReelsUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF00E5FF))
            }
            is ReelsUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.message, color = Color(0xFFFF8A80))
                    TextButton(onClick = { viewModel.refresh() }) { Text("Tentar de novo", color = Color(0xFF00E5FF)) }
                }
            }
            is ReelsUiState.Ready -> {
                @Suppress("UNUSED_EXPRESSION")
                tick
                val shown = s.stories.filter { story ->
                    when (filter) {
                        ReelsFilter.Todas -> true
                        ReelsFilter.Favoritas -> prefs.getBoolean("like_${story.id}", false)
                        ReelsFilter.Continuar -> isInProgress(prefs, story.id)
                    }
                }
                if (shown.isEmpty()) {
                    val empty = when (filter) {
                        ReelsFilter.Todas -> "Nenhuma historia no painel ainda."
                        ReelsFilter.Favoritas -> "Nada favoritado. Toque no coracao no player."
                        ReelsFilter.Continuar -> "Nada em andamento."
                    }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(empty, color = Color(0xFF8B8BA8))
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(shown, key = { it.id }) { story ->
                            StoryCard(
                                story = story,
                                liked = prefs.getBoolean("like_${story.id}", false),
                                watching = isInProgress(prefs, story.id),
                                onClick = { onStoryClick(story) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryCard(story: ReelStory, liked: Boolean, watching: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.70f)
                .clip(shape)
                .border(1.2.dp, Color(0xFF00D4E8), shape)
                .background(Color(0xFF101018)),
        ) {
            val poster = story.poster_url.orEmpty()
            if (poster.startsWith("http")) {
                AsyncImage(
                    model = poster,
                    contentDescription = story.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (watching) {
                Text(
                    "Continuar",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(7.dp)
                        .background(Color(0xFFFF2D55), RoundedCornerShape(7.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
            if (liked) {
                Text("★", color = Color(0xFFFFD54F), fontSize = 13.sp, modifier = Modifier.align(Alignment.TopEnd).padding(7.dp))
            }
        }
        Text(
            story.title ?: "Sem titulo",
            color = Color(0xFFE8E8F0),
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )
    }
}
