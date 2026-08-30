#!/usr/bin/env python3
"""Card cinema no lugar do Assistir enquanto busca fontes."""
from pathlib import Path

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt")
t = p.read_text()

if "fun HeroCinemaLoading()" in t and "showServersLoading" in t:
    print("already applied")
    raise SystemExit(0)

# 1) Flag de loading no hero
OLD_FLAG = '''    val heroWatchEnabled = state.mediaType == "movie" &&
        !state.movieIsLocked(isVip) &&
        state.movieSources.isNotEmpty()'''
NEW_FLAG = '''    val heroWatchEnabled = state.mediaType == "movie" &&
        !state.movieIsLocked(isVip) &&
        state.movieSources.isNotEmpty()
    val heroServersLoading = state.mediaType == "movie" &&
        !state.movieIsLocked(isVip) &&
        state.isLoadingMovieSources &&
        state.movieSources.isEmpty()'''
if OLD_FLAG not in t:
    raise SystemExit("heroWatchEnabled not found")
t = t.replace(OLD_FLAG, NEW_FLAG, 1)
print("flag OK")

# 2) Passa param no DetailHeader
OLD_CALL = '''                showWatchNowButton = heroWatchEnabled,
                onWatchNowClick = onWatchMovieNow,'''
NEW_CALL = '''                showWatchNowButton = heroWatchEnabled,
                showServersLoading = heroServersLoading,
                onWatchNowClick = onWatchMovieNow,'''
if OLD_CALL not in t:
    raise SystemExit("DetailHeader call not found")
t = t.replace(OLD_CALL, NEW_CALL, 1)
print("call OK")

# 3) Signature
OLD_SIG = '''    showWatchNowButton: Boolean,
    onWatchNowClick: () -> Unit,'''
NEW_SIG = '''    showWatchNowButton: Boolean,
    showServersLoading: Boolean = false,
    onWatchNowClick: () -> Unit,'''
if OLD_SIG not in t:
    raise SystemExit("DetailHeader sig not found")
t = t.replace(OLD_SIG, NEW_SIG, 1)
print("sig OK")

# 4) Slot do botao Assistir
OLD_IF = '''            if (showWatchNowButton) {
                Spacer(Modifier.height(16.dp))'''
NEW_IF = '''            if (showServersLoading) {
                Spacer(Modifier.height(16.dp))
                HeroCinemaLoading()
            } else if (showWatchNowButton) {
                Spacer(Modifier.height(16.dp))'''
if OLD_IF not in t:
    raise SystemExit("Assistir if not found")
t = t.replace(OLD_IF, NEW_IF, 1)
print("button slot OK")

OLD_SP = '''            Spacer(Modifier.height(if (showWatchNowButton) 12.dp else 20.dp))'''
NEW_SP = '''            Spacer(Modifier.height(if (showWatchNowButton || showServersLoading) 12.dp else 20.dp))'''
if OLD_SP not in t:
    raise SystemExit("spacer not found")
t = t.replace(OLD_SP, NEW_SP, 1)
print("spacer OK")

# 5) Remove texto simples embaixo
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
    print("plain text removed")
else:
    print("WARN plain loading text not found")

# 6) Composable
HERO_FN = '''
@Composable
private fun HeroCinemaLoading() {
    val cyan = Color(0xFF00E5FF)
    val purple = Color(0xFF8B5CFF)
    val gold = Color(0xFFFFD54F)
    var progress by remember { mutableStateOf(0.14f) }
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "heroPulse")
    val glow by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                durationMillis = 1100,
                easing = androidx.compose.animation.core.FastOutSlowInEasing,
            ),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
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
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color(0xFF121A28), Color(0xFF070B12), Color(0xFF0C1020)),
                    ),
                )
                .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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
                    Text("\u25b6", fontSize = 22.sp, color = cyan, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "PREPARANDO A SESS\u00c3O",
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.8.sp,
                color = Color(0xFFF5F7FB),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Localizando servidores premium\u2026",
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
            Spacer(Modifier.height(10.dp))
            Text(
                "LUZ  \u00b7  C\u00c2MERA  \u00b7  A\u00c7\u00c3O",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = cyan.copy(alpha = 0.9f),
            )
        }
    }
}

'''

if "private fun CinemaServersLoading()" not in t:
    raise SystemExit("CinemaServersLoading missing")
t = t.replace(
    "@Composable\nprivate fun CinemaServersLoading()",
    HERO_FN + "@Composable\nprivate fun CinemaServersLoading()",
    1,
)
print("HeroCinemaLoading inserted")

p.write_text(t)
assert "showServersLoading" in t
assert "fun HeroCinemaLoading()" in t
print("done")
