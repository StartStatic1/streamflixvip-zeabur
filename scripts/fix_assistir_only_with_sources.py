#!/usr/bin/env python3
"""Assistir so aparece com fontes; loading da sessao mais cinema."""
from pathlib import Path

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt")
t = p.read_text()

# 1) Assistir apenas quando ha fontes reais
OLD_HERO = '''    // Botao visivel cedo: com fontes prontas OU ainda carregando (nao trava 5-9s sem CTA)
    val heroWatchEnabled = state.mediaType == "movie" &&
        !state.movieIsLocked(isVip) &&
        (state.movieSources.isNotEmpty() || state.isLoadingMovieSources)'''

NEW_HERO = '''    // Assistir so quando existe fonte — evita clique em titulo fora da grade
    // (loading embaixo; se vazio, card Pedir filme)
    val heroWatchEnabled = state.mediaType == "movie" &&
        !state.movieIsLocked(isVip) &&
        state.movieSources.isNotEmpty()'''

if OLD_HERO in t:
    t = t.replace(OLD_HERO, NEW_HERO, 1)
    print("heroWatchEnabled OK")
elif "state.movieSources.isNotEmpty()" in t and "isLoadingMovieSources)" not in t.split("heroWatchEnabled")[1][:200]:
    print("hero already sources-only?")
else:
    # fallback looser
    OLD2 = '''    val heroWatchEnabled = state.mediaType == "movie" &&
        !state.movieIsLocked(isVip) &&
        (state.movieSources.isNotEmpty() || state.isLoadingMovieSources)'''
    if OLD2 in t:
        t = t.replace(OLD2, NEW_HERO.replace("    // Assistir so quando existe fonte — evita clique em titulo fora da grade\n    // (loading embaixo; se vazio, card Pedir filme)\n", ""), 1)
        print("heroWatchEnabled OK loose")
    else:
        raise SystemExit("heroWatchEnabled pattern not found")

# 2) onWatchMovieNow: nao abre sheet vazio enquanto carrega
OLD_WATCH = '''                onWatchMovieNow = {
                    when {
                        s.movieSources.isEmpty() && s.isLoadingMovieSources -> {
                            // Ainda buscando — abre sheet; lista preenche quando chegar
                            showMovieServerPicker = true
                        }
                        s.movieSources.size == 1 -> pendingWatch = PendingSource(s.movieSources.first(), 0, 0)
                        s.movieSources.size > 1 -> showMovieServerPicker = true
                        else -> Unit
                    }
                },'''

NEW_WATCH = '''                onWatchMovieNow = {
                    when {
                        s.movieSources.size == 1 -> pendingWatch = PendingSource(s.movieSources.first(), 0, 0)
                        s.movieSources.size > 1 -> showMovieServerPicker = true
                        else -> Unit
                    }
                },'''

if OLD_WATCH in t:
    t = t.replace(OLD_WATCH, NEW_WATCH, 1)
    print("onWatchMovieNow OK")
else:
    print("WARN onWatchMovieNow")

# 3) Cinema loading premium
OLD_CINEMA = '''@Composable
private fun CinemaServersLoading() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🎬", fontSize = 36.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            "Preparando a sessão…",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Buscando os melhores servidores para este título",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Spacer(Modifier.height(20.dp))
        // Barrinha estilo “claquete / progressão de sessão”
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            var progress by remember { mutableStateOf(0f) }
            LaunchedEffect(Unit) {
                while (true) {
                    progress = 0f
                    val steps = 24
                    repeat(steps) {
                        progress = (it + 1) / steps.toFloat()
                        kotlinx.coroutines.delay(90)
                    }
                    kotlinx.coroutines.delay(200)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0.08f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(
                                Color(0xFF00E5FF),
                                Color(0xFF7C5CFF),
                            ),
                        ),
                    ),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Luz, câmera… servidores",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            fontWeight = FontWeight.Medium,
        )
    }
}'''

NEW_CINEMA = '''@Composable
private fun CinemaServersLoading() {
    val cyan = Color(0xFF00E5FF)
    val purple = Color(0xFF7C5CFF)
    var progress by remember { mutableStateOf(0.12f) }
    LaunchedEffect(Unit) {
        while (true) {
            progress = 0.12f
            val steps = 28
            repeat(steps) {
                progress = 0.12f + (it + 1) / steps.toFloat() * 0.88f
                kotlinx.coroutines.delay(85)
            }
            kotlinx.coroutines.delay(180)
        }
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF0A0E16),
        border = BorderStroke(
            1.dp,
            androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(cyan.copy(alpha = 0.55f), purple.copy(alpha = 0.55f))),
        ),
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Icone estilo play / sessao (sem emoji basico)
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(cyan.copy(alpha = 0.35f), purple.copy(alpha = 0.22f)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF121826)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "▶",
                        fontSize = 26.sp,
                        color = cyan,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "PREPARANDO A SESSÃO",
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.4.sp,
                color = Color(0xFFF2F5FA),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Localizando os melhores servidores para este título",
                fontSize = 12.sp,
                color = Color(0xFF9AA3B5),
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(22.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF1A2230)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0.1f, 1f))
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(cyan, purple, cyan),
                            ),
                        ),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "LUZ  ·  CÂMERA  ·  SERVIDORES",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
                color = cyan.copy(alpha = 0.85f),
            )
        }
    }
}'''

if OLD_CINEMA in t:
    t = t.replace(OLD_CINEMA, NEW_CINEMA, 1)
    print("CinemaServersLoading OK")
elif "PREPARANDO A SESSÃO" in t:
    print("cinema already premium?")
else:
    raise SystemExit("CinemaServersLoading block not found")

# BorderStroke import
if "import androidx.compose.foundation.BorderStroke" not in t:
    if "import androidx.compose.foundation.background" in t:
        t = t.replace(
            "import androidx.compose.foundation.background",
            "import androidx.compose.foundation.BorderStroke\nimport androidx.compose.foundation.background",
            1,
        )
        print("BorderStroke import")
    elif "import androidx.compose.foundation.layout" in t:
        t = t.replace(
            "import androidx.compose.foundation.layout",
            "import androidx.compose.foundation.BorderStroke\nimport androidx.compose.foundation.layout",
            1,
        )

p.write_text(t)
assert "state.movieSources.isNotEmpty()" in t
assert "PREPARANDO A SESSÃO" in t
print("done")
