#!/usr/bin/env python3
from pathlib import Path
p = Path('android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt')
t = p.read_text()

old_if = '''            if (showServersLoading) {
                Spacer(Modifier.height(16.dp))
                HeroCinemaLoading()
            } else if (showWatchNowButton) {'''
new_if = '''            var sessionReady by remember { mutableStateOf(false) }
            LaunchedEffect(title, showServersLoading) {
                if (showServersLoading) sessionReady = false
            }
            if (showServersLoading || (showWatchNowButton && !sessionReady)) {
                Spacer(Modifier.height(16.dp))
                HeroCinemaLoading(
                    finishing = showWatchNowButton && !showServersLoading,
                    onReady = { sessionReady = true },
                )
            } else if (showWatchNowButton) {'''
if old_if not in t:
    if 'finishing = showWatchNowButton' in t:
        print('call site ja ok')
    else:
        raise SystemExit('if showServersLoading nao encontrado')
else:
    t = t.replace(old_if, new_if, 1)
    print('call site ok')

start = t.find('@Composable\nprivate fun HeroCinemaLoading()')
if start < 0:
    start = t.find('private fun HeroCinemaLoading(')
if start < 0:
    raise SystemExit('HeroCinemaLoading nao encontrado')
# include previous @Composable if present
prev = t.rfind('@Composable', 0, start)
if prev >= 0 and start - prev < 40:
    start = prev
end = t.find('@Composable\nprivate fun CinemaServersLoading()', start)
if end < 0:
    end = t.find('private fun CinemaServersLoading()', start)
if end < 0:
    raise SystemExit('CinemaServersLoading nao encontrado')

new_fn = '''@Composable
private fun HeroCinemaLoading(
    finishing: Boolean = false,
    onReady: () -> Unit = {},
) {
    val cyan = Color(0xFF00E5FF)
    val purple = Color(0xFF8B5CFF)
    val gold = Color(0xFFFFD54F)
    var progress by remember { mutableStateOf(0.10f) }
    var phraseIdx by remember { mutableStateOf(0) }
    val phrases = listOf(
        "Abrindo a sala…",
        "Ajustando o projetor…",
        "Buscando as melhores fontes…",
        "Sessão quase pronta…",
    )
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
    LaunchedEffect(finishing) {
        if (finishing) {
            phraseIdx = phrases.lastIndex
            val from = progress.coerceAtLeast(0.35f)
            val steps = 10
            repeat(steps) { i ->
                progress = from + (1f - from) * ((i + 1) / steps.toFloat())
                kotlinx.coroutines.delay(28)
            }
            progress = 1f
            kotlinx.coroutines.delay(90)
            onReady()
            return@LaunchedEffect
        }
        while (true) {
            if (progress < 0.78f) {
                progress = (progress + 0.018f).coerceAtMost(0.78f)
            }
            phraseIdx = (phraseIdx + 1) % (phrases.size - 1)
            kotlinx.coroutines.delay(520)
        }
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF070B12),
        shadowElevation = 6.dp,
        border = BorderStroke(
            1.dp,
            androidx.compose.ui.graphics.Brush.horizontalGradient(
                listOf(
                    cyan.copy(alpha = 0.35f + glow * 0.4f),
                    purple.copy(alpha = 0.35f + glow * 0.3f),
                    gold.copy(alpha = 0.22f + glow * 0.22f),
                ),
            ),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color(0xFF101826), Color(0xFF070B12)),
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(cyan.copy(alpha = 0.38f), purple.copy(alpha = 0.24f)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("▶", fontSize = 15.sp, color = cyan, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (finishing) "SESSÃO PRONTA" else "PREPARANDO A SESSÃO",
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.4.sp,
                color = Color(0xFFF5F7FB),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                phrases[phraseIdx.coerceIn(0, phrases.lastIndex)],
                fontSize = 11.sp,
                color = Color(0xFF9AA3B5),
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF1A2230)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0.08f, 1f))
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(cyan, purple, gold.copy(alpha = 0.9f)),
                            ),
                        ),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "LUZ · CÂMERA · AÇÃO",
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.6.sp,
                color = cyan.copy(alpha = 0.85f),
            )
        }
    }
}

'''
t = t[:start] + new_fn + t[end:]
p.write_text(t)
print('hero fn ok', p.stat().st_size)
