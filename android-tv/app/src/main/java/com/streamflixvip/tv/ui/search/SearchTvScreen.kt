package com.streamflixvip.tv.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.streamflixvip.tv.network.NetworkModule
import com.streamflixvip.tv.network.TmdbItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TMDB_POSTER_W342 = "https://image.tmdb.org/t/p/w342"

val GENRES = mapOf(
    0 to "Todos",
    28 to "Ação",
    12 to "Aventura",
    16 to "Animação",
    35 to "Comédia",
    80 to "Crime",
    99 to "Documentário",
    18 to "Drama",
    10751 to "Família",
    14 to "Fantasia",
    36 to "História",
    27 to "Terror",
    10402 to "Música",
    9648 to "Mistério",
    10749 to "Romance",
    878 to "Ficção Científica",
    53 to "Thriller",
    10752 to "Guerra",
)

val YEARS = listOf(0, 2026, 2025, 2024, 2023, 2022, 2021, 2020, 2019, 2018, 2017, 2016, 2015)
val TYPES = listOf("Todos", "Filmes", "Séries", "Animes")

@Composable
fun SearchTvScreen(
    onItemClick: (tmdbId: Int, mediaType: String) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("Todos") }
    var selectedGenre by remember { mutableIntStateOf(0) }
    var selectedYear by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<List<TmdbItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val searchBarFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        searchBarFocus.requestFocus()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A10))) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchTopBar(
                query = query,
                onQueryChange = { query = it },
                onBack = onBack,
                focusRequester = searchBarFocus,
            )

            FilterBar(
                selectedType = selectedType,
                onTypeSelected = { selectedType = it },
                selectedGenre = selectedGenre,
                onGenreSelected = { selectedGenre = it },
                selectedYear = selectedYear,
                onYearSelected = { selectedYear = it },
            )

            Spacer(modifier = Modifier.height(8.dp))

            SearchButton(
                onClick = {
                    coroutineScope.launch {
                        isLoading = true
                        results = performSearch(query, selectedType, selectedGenre, selectedYear)
                        isLoading = false
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            SearchResultsArea(
                isLoading = isLoading,
                results = results,
                query = query,
                onItemClick = onItemClick
            )
        }
    }
}

@Composable
private fun SearchButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        var isSearchBtnFocused by remember { mutableStateOf(false) }
        Card(
            onClick = onClick,
            modifier = Modifier
                .height(42.dp)
                .scale(if (isSearchBtnFocused) 1.05f else 1f)
                .onFocusChanged { isSearchBtnFocused = it.isFocused },
            colors = CardDefaults.colors(
                containerColor = Color(0xFFD4AF37),
                focusedContainerColor = Color(0xFFFFD700)
            ),
            shape = CardDefaults.shape(RoundedCornerShape(21.dp)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Text("Pesquisar", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun SearchResultsArea(
    isLoading: Boolean,
    results: List<TmdbItem>,
    query: String,
    onItemClick: (Int, String) -> Unit
) {
    if (isLoading) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFFD4AF37))
        }
    } else if (results.isEmpty() && query.isNotBlank()) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Text(
                "Nenhum resultado encontrado",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 16.sp,
            )
        }
    } else if (results.isNotEmpty()) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(results) { item ->
                SearchResultCard(item) { onItemClick(item.id, item.resolvedMediaType) }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Busca e Filtros", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Use a barra de pesquisa e os filtros acima.\nMarque Tipo, Gênero e Ano para refinar resultados.",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}

@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    focusRequester: FocusRequester,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(Color(0xFF12121A))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
            }

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = {
                    Text("Pesquisar filmes, séries...", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp)
                },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = Color(0xFFD4AF37))
                },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Limpar", tint = Color.White.copy(alpha = 0.6f))
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFD4AF37),
                    unfocusedBorderColor = Color(0xFF333344),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFFD4AF37),
                    focusedContainerColor = Color(0xFF1E1E2E),
                    unfocusedContainerColor = Color(0xFF1E1E2E),
                ),
                shape = RoundedCornerShape(24.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp),
            )
        }
    }
}

@Composable
private fun FilterBar(
    selectedType: String,
    onTypeSelected: (String) -> Unit,
    selectedGenre: Int,
    onGenreSelected: (Int) -> Unit,
    selectedYear: Int,
    onYearSelected: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Tipo",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFD4AF37),
            modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 48.dp),
        ) {
            items(TYPES) { type ->
                FilterChip(
                    text = type,
                    selected = type == selectedType,
                    onClick = { onTypeSelected(type) },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "Gênero",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFD4AF37),
            modifier = Modifier.padding(horizontal = 48.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 48.dp),
        ) {
            items(GENRES.entries.toList()) { (id, name) ->
                FilterChip(
                    text = name,
                    selected = id == selectedGenre,
                    onClick = { onGenreSelected(id) },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "Ano",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFD4AF37),
            modifier = Modifier.padding(horizontal = 48.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
        ) {
            items(YEARS) { year ->
                FilterChip(
                    text = if (year == 0) "Todos" else year.toString(),
                    selected = year == selectedYear,
                    onClick = { onYearSelected(year) },
                )
            }
        }
    }
}

@Composable
private fun FilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1f, label = "filter_scale")

    Card(
        onClick = onClick,
        modifier = Modifier
            .height(36.dp)
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = if (selected) Color(0xFFD4AF37) else Color(0xFF1E1E2E),
            focusedContainerColor = Color(0xFFFFD700),
        ),
        shape = CardDefaults.shape(RoundedCornerShape(18.dp)),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = if (selected) Color.Black else Color.White,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun SearchResultCard(item: TmdbItem, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.02f else 1f, label = "result_scale")

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = if (isFocused) Color(0xFF2E2E3E) else Color(0xFF15151C),
            focusedContainerColor = Color(0xFF2E2E3E),
        ),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = item.poster_path?.let { "$TMDB_POSTER_W342$it" },
                contentDescription = item.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(68.dp).height(100.dp).clip(RoundedCornerShape(8.dp)),
            )
            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(item.displayTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.displayYear?.let { MetaPillSmall(it) }
                    item.displayRating?.let { RatingPillSmall(it) }
                    MetaPillSmall(item.displayMediaLabel)
                }
                Spacer(modifier = Modifier.height(4.dp))
                item.overview?.let {
                    Text(it, fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f), maxLines = 2)
                }
            }

            Icon(
                Icons.Filled.PlayCircleFilled,
                contentDescription = null,
                tint = if (isFocused) Color(0xFFD4AF37) else Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun MetaPillSmall(text: String) {
    Box(modifier = Modifier.background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, fontSize = 11.sp, color = Color.White)
    }
}

@Composable
private fun RatingPillSmall(rating: String) {
    Box(modifier = Modifier.background(Color(0xFFFFC107), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = Color.Black, modifier = Modifier.size(11.dp))
            Text(" $rating", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

private suspend fun performSearch(
    query: String,
    type: String,
    genre: Int,
    year: Int,
): List<TmdbItem> {
    val results = mutableListOf<TmdbItem>()
    val searchPath = when (type) {
        "Filmes" -> "/search/movie"
        "Séries" -> "/search/tv"
        "Animes" -> "/search/tv"
        else -> "/search/multi"
    }

    runCatching {
        NetworkModule.tmdbApi.request(
            path = searchPath,
            query = query.takeIf { it.isNotBlank() },
            withGenres = if (genre > 0) genre.toString() else null,
            primaryReleaseYear = if (type == "Filmes" && year > 0) year else null,
            firstAirDateYear = if ((type == "Séries" || type == "Animes") && year > 0) year else null,
            withOriginalLanguage = if (type == "Animes") "ja" else null,
        )
    }.onSuccess { response ->
        results.addAll(response.results.orEmpty())
    }

    if (query.isBlank() && genre > 0) {
        val discoverPath = when (type) {
            "Filmes" -> "/discover/movie"
            "Séries" -> "/discover/tv"
            "Animes" -> "/discover/tv"
            else -> "/discover/movie"
        }
        runCatching {
            NetworkModule.tmdbApi.request(
                path = discoverPath,
                withGenres = genre.toString(),
                primaryReleaseYear = if (type == "Filmes" && year > 0) year else null,
                firstAirDateYear = if ((type == "Séries" || type == "Animes") && year > 0) year else null,
                withOriginalLanguage = if (type == "Animes") "ja" else null,
            )
        }.onSuccess { response ->
            results.addAll(response.results.orEmpty())
        }
    }

    return results
}
