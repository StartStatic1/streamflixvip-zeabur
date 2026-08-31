package com.streamflixvip.app.ui.reels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.streamflixvip.app.network.NetworkModule
import com.streamflixvip.app.network.ReelStory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF08080F))
            .padding(horizontal = 16.dp),
    ) {
        Text(
            "Historias",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        Text(
            "Minisseries verticais. Um toque e assiste.",
            color = Color(0xFF8B8BA8),
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        when (val s = ui) {
            is ReelsUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF00E5FF))
            }
            is ReelsUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.message, color = Color(0xFFFF8A80))
                    TextButton(onClick = { viewModel.refresh() }) {
                        Text("Tentar de novo", color = Color(0xFF00E5FF))
                    }
                }
            }
            is ReelsUiState.Ready -> {
                if (s.stories.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Nenhuma historia no painel ainda.", color = Color(0xFF8B8BA8))
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(s.stories, key = { it.id }) { story ->
                            StoryCard(story) { onStoryClick(story) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryCard(story: ReelStory, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF161622)),
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xCC08080F)),
                        ),
                    ),
            )
            val badge = story.genre?.takeIf { it.isNotBlank() }
            if (badge != null) {
                Text(
                    badge,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color(0x99000000), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Text(
            story.title ?: "Sem titulo",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        val sub = story.subtitle?.takeIf { it.isNotBlank() }
        if (sub != null) {
            Text(sub, color = Color(0xFF8B8BA8), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
