#!/usr/bin/env python3
"""No lugar do Assistir: card cinema premium enquanto busca fontes."""
from pathlib import Path

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt")
t = p.read_text()

# 1) Flag no content
OLD = '''    // Assistir so quando existe fonte — evita clique em titulo fora da grade
    // (loading embaixo; se vazio, card Pedir filme)
    val heroWatchEnabled = state.mediaType == "movie" &&
        !state.movieIsLocked(isVip) &&
        state.movieSources.isNotEmpty()

    // Controla se o modal de trailer inline está aberto.
    var showTrailerModal by remember { mutableStateOf(false) }

    LazyColumn(modifier.fillMaxSize()) {
        item {
            DetailHeader(
                title = title,
                tagline = details.tagline,
                backdropUrl = backdropUrl,
                posterUrl = posterUrl,
                rating = details.vote_average,
                year = (details.release_date ?: details.first_air_date)?.take(4),
                runtimeLabel = details.displayRuntime,
                isFavorite = state.isFavorite,
                onToggleFavorite = onToggleFavorite,
                showWatchNowButton = heroWatchEnabled,
                onWatchNowClick = onWatchMovieNow,
                onBack = onBack,
                trailerKey = state.trailerKey,'''

NEW = '''    // Assistir so com fontes; enquanto busca, card cinema no lugar do botao
    val heroWatchEnabled = state.mediaType == "movie" &&
        !state.movieIsLocked(isVip) &&
        state.movieSources.isNotEmpty()
    val heroServersLoading = state.mediaType == "movie" &&
        !state.movieIsLocked(isVip) &&
        state.isLoadingMovieSources &&
        state.movieSources.isEmpty()

    // Controla se o modal de trailer inline está aberto.
    var showTrailerModal by remember { mutableStateOf(false) }

    LazyColumn(modifier.fillMaxSize()) {
        item {
            DetailHeader(
                title = title,
                tagline = details.tagline,
                backdropUrl = backdropUrl,
                posterUrl = posterUrl,
                rating = details.vote_average,
                year = (details.release_date ?: details.first_air_date)?.take(4),
                runtimeLabel = details.displayRuntime,
                isFavorite = state.isFavorite,
                onToggleFavorite = onToggleFavorite,
                showWatchNowButton = heroWatchEnabled,
                showServersLoading = heroServersLoading,
                onWatchNowClick = onWatchMovieNow,
                onBack = onBack,
                trailerKey = state.trailerKey,'''

if OLD not in t:
    raise SystemExit("hero block not found")
t = t.replace(OLD, NEW, 1)
print("hero flags OK")

# 2) Remove texto simples "Carregando servidores…" do lazy (o hero ja mostra)
OLD_TXT = '''            } else if (state.isLoadingMovieSources) {
                item {
                    // Indicador discreto — fontes chegam em background sem travar a tela
                    Text(
                        "Carregando servidores…",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            } else if (state.movieSources.isEmpty()) {'''

NEW_TXT = '''            } else if (state.isLoadingMovieSources) {
                // Loading cinema fica no hero (no lugar do Assistir)
            } else if (state.movieSources.isEmpty()) {'''

if OLD_TXT in t:
    t = t.replace(OLD_TXT, NEW_TXT, 1)
    print("removed plain loading text")
else:
    print("WARN plain loading text")

# 3) Signature DetailHeader
OLD_SIG = '''    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    showWatchNowButton: Boolean,
    onWatchNowClick: () -> Unit,
    onBack: () -> Unit,
    trailerKey: String?,
    onTrailerClick: () -> Unit,
    onShare: () -> Unit,
) {'''

NEW_SIG = '''    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    showWatchNowButton: Boolean,
    showServersLoading: Boolean = false,
    onWatchNowClick: () -> Unit,
    onBack: () -> Unit,
    trailerKey: String?,
    onTrailerClick: () -> Unit,
    onShare: () -> Unit,
) {'''

if OLD_SIG not in t:
    raise SystemExit("DetailHeader sig not found")
t = t.replace(OLD_SIG, NEW_SIG, 1)
print("sig OK")

# 4) UI no lugar do Assistir
OLD_BTN = '''            if (showWatchNowButton) {
                // Shimmer effect no botão para dar vida e chamar atenção
                val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
                val shimmerX by infiniteTransition.animateFloat(
                    initialValue = -200f,
                    targetValue = 800f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "shimmerX"
                )

                Button(
                    onClick = onWatchNowClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(54.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        // O brilho (shimmer) que passa pelo botão
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(60.dp)
                                .graphicsLayer { translationX = shimmerX }
                                .background(
                                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        listOf(
                                            androidx.compose.ui.graphics.Color.Transparent,
                                            androidx.compose.ui.graphics.Color.White.copy(alpha = 0.2f),
                                            androidx.compose.ui.graphics.Color.Transparent,
                                        ),
                                    ),
                                ),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.PlayCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Assistir Agora", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }

            // Barra de ações secundárias: favorito, trailer (se existir) e
            // compartilhar — alinhados horizontalmente abaixo do CTA principal,
            // fáceis de alcançar com o polegar e sem poluir o backdrop.
            Spacer(modifier.height(if (showWatchNowButton) 12.dp else 20.dp))'''

NEW_BTN = '''            if (showServersLoading) {
                HeroCinemaLoading()
            } else if (showWatchNowButton) {
                // Shimmer effect no botão para dar vida e chamar atenção
                val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
                val shimmerX by infiniteTransition.animateFloat(
                    initialValue = -200f,
                    targetValue = 800f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "shimmerX"
                )

                Button(
                    onClick = onWatchNowClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(54.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(60.dp)
                                .graphicsLayer { translationX = shimmerX }
                                .background(
                                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        listOf(
                                            androidx.compose.ui.graphics.Color.Transparent,
                                            androidx.compose.ui.graphics.Color.White.copy(alpha = 0.2f),
                                            androidx.compose.ui.graphics.Color.Transparent,
                                        ),
                                    ),
                                ),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.PlayCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Assistir Agora", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }

            // Barra de ações secundárias
            Spacer(modifier.height(if (showWatchNowButton || showServersLoading) 12.dp else 20.dp))'''

if OLD_BTN not in t:
    raise SystemExit("Assistir button block not found")
t = t.replace(OLD_BTN, NEW_BTN, 1)
print("hero button slot OK")

# 5) Composable HeroCinemaLoading (inserir antes de CinemaServersLoading)
HERO_FN = '''
@Composable
private fun HeroCinemaLoading() {
    val cyan = Color(0xFF00E5FF)
    val purple = Color(0xFF8B5CFF)
    val gold = Color(0xFFFFD54F)
    var progress by remember { mutableStateOf(0.14f) }
    val pulse = rememberInfiniteTransition(label = "heroPulse")
    val glow by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )
    LaunchedEffect(Unit) {
        while (true) {
            progress = 0.14f
            repeat(30) {
                progress = 0.14f + (it + 1) / 30f * 0.86f
                kotlinx.coroutines.delay(80)
            }
            kotlinx.coroutines.delay(160)
        }
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF070B12),
        shadowElevation = 12.dp,
        border = BorderStroke(
            1.5.dp,
            androidx.compose.ui.graphics.Brush.horizontalGradient(
                listOf(
                    cyan.copy(alpha = 0.35f + glow * 0.45f),
                    purple.copy(alpha = 0.4f + glow * 0.35f),
                    gold.copy(alpha = 0.25f + glow * 0.25f),
                ),
            ),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(
                            Color(0xFF121A28),
                            Color(0xFF070B12),
                            Color(0xFF0C1020),
                        ),
                    ),
                )
                .padding(horizontal = 18.dp, vertical = 18.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(cyan.copy(alpha = 0.4f), purple.copy(alpha = 0.28f)),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0E1420)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("▶", fontSize = 22.sp, color = cyan, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "PREPARANDO A SESSÃO",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.8.sp,
                    color = Color(0xFFF5F7FB),
                )
                Spacer(modifier.height(4.dp))
                Text(
                    "Localizando servidores premium…",
                    fontSize = 11.sp,
                    color = Color(0xFF9AA3B5),
                )
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF1A2230)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0.12f, 1f))
                            .height(7.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                androidx.compose.ui.graphics.Brush.horizontalGradient(
                                    listOf(cyan, purple, gold.copy(alpha = 0.9f)),
                                ),
                            ),
                    )
                }
                Spacer(modifier.height(10.dp))
                Text(
                    "LUZ  ·  CÂMERA  ·  AÇÃO",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = cyan.copy(alpha = 0.9f),
                )
            }
        }
    }
}

'''

if "private fun HeroCinemaLoading()" not in t:
    if "private fun CinemaServersLoading()" in t:
        t = t.replace("@Composable\nprivate fun CinemaServersLoading()", HERO_FN + "@Composable\nprivate fun CinemaServersLoading()", 1)
        print("HeroCinemaLoading inserted")
    else:
        raise SystemExit("CinemaServersLoading anchor missing")
else:
    print("HeroCinemaLoading exists")

# BorderStroke
if "import androidx.compose.foundation.BorderStroke" not in t:
    if "import androidx.compose.foundation.background" in t:
        t = t.replace(
            "import androidx.compose.foundation.background",
            "import androidx.compose.foundation.BorderStroke\nimport androidx.compose.foundation.background",
            1,
        )
    print("BorderStroke import")

# FastOutSlowInEasing
if "FastOutSlowInEasing" in t and "import androidx.compose.animation.core.FastOutSlowInEasing" not in t:
    if "import androidx.compose.animation.core.LinearEasing" in t:
        t = t.replace(
            "import androidx.compose.animation.core.LinearEasing",
            "import androidx.compose.animation.core.FastOutSlowInEasing\nimport androidx.compose.animation.core.LinearEasing",
            1,
        )
        print("FastOutSlowInEasing import")

p.write_text(t)
assert "showServersLoading" in t
assert "HeroCinemaLoading" in t
print("done")
