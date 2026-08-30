#!/usr/bin/env python3
from pathlib import Path

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt")
text = p.read_text()
start = text.find("private fun HeroCinemaLoading()")
if start < 0:
    raise SystemExit("HeroCinemaLoading nao encontrado")
end = text.find("private fun CinemaServersLoading()", start)
if end < 0:
    raise SystemExit("CinemaServersLoading nao encontrado")

new = '''private fun HeroCinemaLoading() {
    val cyan = Color(0xFF00E5FF)
    val purple = Color(0xFF8B5CFF)
    val gold = Color(0xFFFFD54F)
    var progress by remember { mutableStateOf(0.16f) }
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
            progress = 0.16f
            repeat(28) {
                progress = 0.16f + (it + 1) / 28f * 0.78f
                kotlinx.coroutines.delay(85)
            }
            kotlinx.coroutines.delay(140)
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
                "PREPARANDO A SESSÃO",
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.4.sp,
                color = Color(0xFFF5F7FB),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Buscando as melhores fontes…",
                fontSize = 11.sp,
                color = Color(0xFF9AA3B5),
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF1A2230)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0.12f, 1f))
                        .height(4.dp)
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

p.write_text(text[:start] + new + text[end:])
print("hero ok", p.stat().st_size)
