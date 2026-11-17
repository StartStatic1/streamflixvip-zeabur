package com.streamflixvip.app.ui.mood

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import coil.compose.AsyncImage
import com.streamflixvip.app.BuildConfig
import com.streamflixvip.app.data.AuthTokenHelper
import com.streamflixvip.app.data.FavoritesRepository
import com.streamflixvip.app.data.VipStatusHolder
import com.streamflixvip.app.network.NetworkModule
import com.streamflixvip.app.network.TmdbImages
import com.streamflixvip.app.network.TmdbItem
import com.streamflixvip.app.profiles.ProfileLocalData
import com.streamflixvip.app.profiles.ProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import org.json.JSONArray

/**
 * Humor → gêneros da TMDB (os mesmos ids que o app já usa em Explorar/Gêneros).
 * movieGenres/tvGenres: "a|b" = qualquer um, "a,b" = todos. null = não sorteia nesse tipo.
 */
enum class Mood(val emoji: String, val label: String, val movieGenres: String?, val tvGenres: String?, val kidsSafe: Boolean) {
    RIR("😂", "Quero rir", "35", "35", true),
    SUSPENSE("😱", "Suspense / Terror", "53|27", "9648|80", false),
    ACAO("💥", "Ação", "28", "10759", false),
    ROMANCE("❤️", "Romance", "10749", null, false),
    FAMILIA("👨‍👩‍👧", "Pra família", "10751|16", "10751|10762", true),
    DRAMA("😢", "Chorar / Drama", "18", "18", false),
    SURPRESA("🎲", "Surpreenda-me", null, null, true),
}

/** Gêneros que nunca entram no perfil infantil. */
private const val KIDS_BLOCKED = "27,53,80,10752,10749,18"

data class MoodPick(val item: TmdbItem, val mediaType: String)

/** Sorteia um título que o app consegue tocar agora, de preferência um que o perfil ainda não viu. */
object MoodRepository {
    private val tmdb = NetworkModule.tmdbApi
    private val mediaSources = NetworkModule.mediaSourcesApi
    private var watchedCache: Pair<String, Set<String>>? = null
    private val shownThisSession = mutableSetOf<String>()

    fun moodsFor(kids: Boolean): List<Mood> = Mood.entries.filter { !kids || it.kidsSafe }

    /** "movie:123" já vistos (conta principal: watch_history + continuar; perfil do aparelho: dados locais). */
    private suspend fun watched(): Set<String> = withContext(Dispatchers.IO) {
        val scope = ProfileLocalData.localScope()
        val key = scope ?: ("main:" + (AuthTokenHelper.currentUserId() ?: ""))
        watchedCache?.let { if (it.first == key) return@withContext it.second }
        val out = mutableSetOf<String>()
        if (scope != null) {
            ProfileLocalData.progress(scope).forEach { out += "${it.media_type}:${it.tmdb_id}" }
        } else {
            val uid = AuthTokenHelper.currentUserId()
            val token = AuthTokenHelper.validAccessToken()
            if (uid != null && token != null) {
                runCatching {
                    val url = BuildConfig.SUPABASE_URL.trimEnd('/') +
                        "/rest/v1/watch_history?user_id=eq.$uid&select=tmdb_id,media_type&limit=3000"
                    val req = Request.Builder().url(url)
                        .header("apikey", NetworkModule.supabaseAnonKey)
                        .header("Authorization", "Bearer $token").build()
                    NetworkModule.okHttpClient.newCall(req).execute().use { r ->
                        if (r.isSuccessful) {
                            val a = JSONArray(r.body?.string().orEmpty())
                            for (i in 0 until a.length()) a.optJSONObject(i)?.let {
                                out += "${it.optString("media_type")}:${it.optInt("tmdb_id")}"
                            }
                        }
                    }
                }
                runCatching {
                    NetworkModule.watchProgressApi.getContinueWatching(
                        apiKey = NetworkModule.supabaseAnonKey,
                        bearerToken = "Bearer $token",
                        userIdFilter = com.streamflixvip.app.network.PostgrestFilter.eq(uid),
                    ).forEach { out += "${it.media_type}:${it.tmdb_id}" }
                }
            }
        }
        watchedCache = key to out
        out
    }

    private suspend fun candidates(mood: Mood, kids: Boolean): List<MoodPick> {
        val real = if (mood == Mood.SURPRESA) moodsFor(kids).filter { it != Mood.SURPRESA }.random() else mood
        val types = listOfNotNull(real.movieGenres?.let { "movie" to it }, real.tvGenres?.let { "tv" to it }).shuffled()
        val out = mutableListOf<MoodPick>()
        for ((type, genres) in types) {
            val kidsGenres = if (type == "movie") "10751" else "10762"
            val params = mutableMapOf(
                "sort_by" to "popularity.desc",
                "include_adult" to "false",
                "vote_count.gte" to if (type == "movie") "200" else "60",
                "page" to (1..(if (kids) 4 else 8)).random().toString(),
                // infantil: o gênero do humor E família/infantil ao mesmo tempo (vírgula = todos)
                "with_genres" to if (kids && real != Mood.FAMILIA) "$genres,${if (type == "movie") "10751" else "10762"}" else genres,
            )
            if (kids) {
                params["without_genres"] = KIDS_BLOCKED
                if (real == Mood.FAMILIA) params["with_genres"] = kidsGenres
            }
            val res = runCatching { tmdb.discover("/discover/$type", params).results.orEmpty() }.getOrDefault(emptyList())
            res.filter { !it.poster_path.isNullOrBlank() && !it.overview.isNullOrBlank() }
                .forEach { out += MoodPick(it.copy(media_type = type), type) }
        }
        return out
    }

    /** Tem servidor para tocar? (mesma consulta que a tela do título faz). */
    private suspend fun playable(p: MoodPick): Boolean = withTimeoutOrNull(20_000) {
        repeat(3) {
            val res = runCatching {
                if (p.mediaType == "movie") mediaSources.getMovieSources(p.item.id, fast = 1)
                else mediaSources.getEpisodeSources(p.item.id, season = 1, episode = 1, fast = 1)
            }.getOrNull() ?: return@withTimeoutOrNull false
            if (res.code == "VIP_REQUIRED" && !VipStatusHolder.isVipNow()) return@withTimeoutOrNull false
            if (res.sources.isNotEmpty()) return@withTimeoutOrNull true
            if (!res.partial) return@withTimeoutOrNull false
            delay((res.pollAfterMs ?: 1500L).coerceIn(500L, 3000L))
        }
        false
    } ?: false

    /** Sem VIP o app só toca a Área Free: sorteia entre ela, pelos gêneros de cada filme. */
    private val genreCache = mutableMapOf<Int, Pair<Set<Int>, String?>>()
    private suspend fun freeCandidates(mood: Mood, kids: Boolean): List<MoodPick> {
        val free = runCatching { NetworkModule.freeCatalogApi.getFreeCatalog(100).items }.getOrDefault(emptyList())
        val real = if (mood == Mood.SURPRESA) null else mood
        val wanted = real?.movieGenres?.split('|', ',')?.mapNotNull { it.toIntOrNull() }?.toSet()
        val blocked = KIDS_BLOCKED.split(',').map { it.toInt() }.toSet()
        return free.filter { it.resolvedMediaType == "movie" && !it.poster_path.isNullOrBlank() }.mapNotNull { f ->
            val (genres, overview) = genreCache[f.id] ?: runCatching {
                val d = tmdb.request(path = "/movie/${f.id}")
                ((d.genres ?: emptyList()).map { g -> g.id }.toSet() to d.overview).also { genreCache[f.id] = it }
            }.getOrNull() ?: return@mapNotNull null
            if (kids && (genres.any { it in blocked } || (10751 !in genres && 16 !in genres))) return@mapNotNull null
            if (wanted != null && genres.none { it in wanted }) return@mapNotNull null
            MoodPick(f.copy(media_type = "movie", overview = f.overview?.takeIf { it.isNotBlank() } ?: overview), "movie")
        }
    }

    suspend fun pick(mood: Mood, kids: Boolean): MoodPick? {
        val seen = watched()
        if (!VipStatusHolder.isVipNow()) {
            val all = freeCandidates(mood, kids)
            var pool = all.filter { "${it.mediaType}:${it.item.id}" !in shownThisSession }
            if (pool.isEmpty() && all.isNotEmpty()) { all.forEach { shownThisSession -= "${it.mediaType}:${it.item.id}" }; pool = all }
            val (fresh, old) = pool.partition { "${it.mediaType}:${it.item.id}" !in seen }
            for (p in fresh.shuffled() + old.shuffled()) {
                shownThisSession += "${p.mediaType}:${p.item.id}"
                if (playable(p)) return p
            }
            return null
        }
        repeat(3) {
            val all = candidates(mood, kids).distinctBy { "${it.mediaType}:${it.item.id}" }
                .filter { "${it.mediaType}:${it.item.id}" !in shownThisSession }
            if (all.isEmpty()) return@repeat
            val (fresh, old) = all.partition { "${it.mediaType}:${it.item.id}" !in seen }
            val ordered = fresh.shuffled() + old.shuffled()
            for (p in ordered.take(4)) {
                val k = "${p.mediaType}:${p.item.id}"
                shownThisSession += k
                if (playable(p)) return p
            }
        }
        return null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodPickerSheet(onDismiss: () -> Unit, onWatch: (tmdbId: Int, mediaType: String) -> Unit) {
    val kids = remember { ProfileStore.isKids }
    val scope = rememberCoroutineScope()
    val favorites = remember { FavoritesRepository() }
    var mood by remember { mutableStateOf<Mood?>(null) }
    var pick by remember { mutableStateOf<MoodPick?>(null) }
    var loading by remember { mutableStateOf(false) }
    var notFound by remember { mutableStateOf(false) }
    var inList by remember { mutableStateOf(false) }
    var round by remember { mutableIntStateOf(0) }

    LaunchedEffect(mood, round) {
        val m = mood ?: return@LaunchedEffect
        loading = true; notFound = false; pick = null; inList = false
        val p = MoodRepository.pick(m, kids)
        pick = p
        notFound = p == null
        loading = false
        if (p != null) inList = runCatching { favorites.isFavorite(tmdbId = p.item.id, mediaType = p.mediaType) }.getOrDefault(false)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF10141D),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
        ) {
            Text("Não sei o que assistir", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                if (mood == null) "Qual é o clima de hoje?" else "${mood!!.emoji} ${mood!!.label}",
                fontSize = 13.sp, color = Color.White.copy(alpha = 0.65f),
            )
            Spacer(Modifier.height(14.dp))

            if (mood == null) {
                MoodRepository.moodsFor(kids).chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { m ->
                            Row(
                                Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(alpha = 0.07f))
                                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                                    .clickable { mood = m }
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(m.emoji, fontSize = 22.sp)
                                Spacer(Modifier.width(10.dp))
                                Text(m.label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                }
                return@Column
            }

            when {
                loading -> Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Text("Sorteando um título que está disponível…", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                    }
                }
                notFound -> Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (VipStatusHolder.isVipNow()) "Não achei um título disponível agora."
                        else "Nenhum título grátis com esse humor agora. Com o VIP o sorteio usa o catálogo todo.",
                        color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    WhiteButton("Tentar de novo", Icons.Filled.Refresh) { round++ }
                }
                pick != null -> {
                    val it = pick!!.item
                    Row {
                        AsyncImage(
                            model = TmdbImages.poster(it.poster_path, "w342"),
                            contentDescription = it.displayTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.width(120.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f)),
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(it.displayTitle, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            val meta = listOfNotNull(
                                if (pick!!.mediaType == "tv") "Série" else "Filme",
                                it.displayYear,
                                it.displayRating?.let { r -> "★ $r" },
                            ).joinToString(" · ")
                            Text(meta, color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(it.overview.orEmpty(), color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, maxLines = 7, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    WhiteButton("Assistir", Icons.Filled.PlayArrow, Modifier.fillMaxWidth()) {
                        onWatch(it.id, pick!!.mediaType)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlineAction("Sortear outro", Icons.Filled.Casino, Modifier.weight(1f)) { round++ }
                        OutlineAction(if (inList) "Na Minha Lista" else "Minha Lista", if (inList) Icons.Filled.Check else Icons.Filled.Add, Modifier.weight(1f)) {
                            val p = pick ?: return@OutlineAction
                            scope.launch {
                                inList = if (inList) {
                                    !favorites.removeFavorite(tmdbId = p.item.id, mediaType = p.mediaType)
                                } else {
                                    favorites.addFavorite(tmdbId = p.item.id, mediaType = p.mediaType, title = p.item.displayTitle,
                                        posterPath = p.item.poster_path, originalLanguage = p.item.original_language)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { mood = null; pick = null }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Trocar o humor", color = Color.White.copy(alpha = 0.75f))
            }
        }
    }
}

@Composable
private fun WhiteButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
    ) {
        Icon(icon, null, tint = Color.Black, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun OutlineAction(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
    }
}

/** Entrada na Início: faixa "Não sei o que assistir". */
@Composable
fun MoodEntryCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF1E2A44), Color(0xFF2B1B3D))))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🎲", fontSize = 26.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Não sei o que assistir", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("Escolha o humor e a gente sorteia um título", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }
        Icon(Icons.Filled.Casino, null, tint = Color.White)
    }
}
