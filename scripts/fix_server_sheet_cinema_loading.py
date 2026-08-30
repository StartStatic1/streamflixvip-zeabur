#!/usr/bin/env python3
"""Sheet Escolha o servidor: loading cinema enquanto fontes carregam."""
from pathlib import Path

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt")
t = p.read_text()

OLD = '''            if (showMovieServerPicker) {
                val sheetState = rememberModalBottomSheetState()
                var showPremiumSheet by remember { mutableStateOf(false) }
                ModalBottomSheet(onDismissRequest = { showMovieServerPicker = false }, sheetState = sheetState) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 48.dp),
                    ) {
                        item {
                            Text(
                                "Escolha o servidor",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        }
                        itemsIndexed(s.movieSources) { index, source ->
                            val isAddon = isAddonSourceLabel(source.source_label)
                            val lockedForFree = !isVip && (index >= FREE_SERVER_SLOTS || isAddon)
                            SourceRow(
                                source = source,
                                isRecommended = index == 0 && !isAddon,
                                isLockedForFree = lockedForFree,
                                onClick = {
                                    showMovieServerPicker = false
                                    pendingWatch = PendingSource(source, 0, 0)
                                },
                                onLockedClick = { showPremiumSheet = true },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
                if (showPremiumSheet) {
                    PremiumServerSheet(
                        onDismiss = { showPremiumSheet = false },
                        onUpgradeClick = onUpgradeClick,
                    )
                }
            }'''

NEW = '''            if (showMovieServerPicker) {
                val sheetState = rememberModalBottomSheetState()
                var showPremiumSheet by remember { mutableStateOf(false) }
                // Auto: 1 fonte pronta enquanto sheet aberto → segue pro play
                LaunchedEffect(s.movieSources, s.isLoadingMovieSources, showMovieServerPicker) {
                    if (!showMovieServerPicker) return@LaunchedEffect
                    if (s.isLoadingMovieSources) return@LaunchedEffect
                    if (s.movieSources.size == 1) {
                        showMovieServerPicker = false
                        pendingWatch = PendingSource(s.movieSources.first(), 0, 0)
                    }
                }
                ModalBottomSheet(onDismissRequest = { showMovieServerPicker = false }, sheetState = sheetState) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 48.dp),
                    ) {
                        item {
                            Text(
                                "Escolha o servidor",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        }
                        if (s.movieSources.isEmpty() && s.isLoadingMovieSources) {
                            item {
                                CinemaServersLoading()
                            }
                        } else if (s.movieSources.isEmpty() && !s.isLoadingMovieSources) {
                            item {
                                Text(
                                    "Nenhum servidor disponível agora. Tente de novo em instantes.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 20.dp),
                                )
                            }
                        } else {
                            itemsIndexed(s.movieSources) { index, source ->
                                val isAddon = isAddonSourceLabel(source.source_label)
                                val lockedForFree = !isVip && (index >= FREE_SERVER_SLOTS || isAddon)
                                SourceRow(
                                    source = source,
                                    isRecommended = index == 0 && !isAddon,
                                    isLockedForFree = lockedForFree,
                                    onClick = {
                                        showMovieServerPicker = false
                                        pendingWatch = PendingSource(source, 0, 0)
                                    },
                                    onLockedClick = { showPremiumSheet = true },
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
                if (showPremiumSheet) {
                    PremiumServerSheet(
                        onDismiss = { showPremiumSheet = false },
                        onUpgradeClick = onUpgradeClick,
                    )
                }
            }'''

if OLD not in t:
    if "CinemaServersLoading" in t:
        print("already has cinema loading")
        raise SystemExit(0)
    raise SystemExit("movie sheet block not found")

t = t.replace(OLD, NEW, 1)

# Add composable near SourceRow or end of file before last braces
if "fun CinemaServersLoading" not in t:
    # place before private fun SourceRow or at end after MovieRequestCard
    marker = "@Composable\nprivate fun MovieRequestCard()"
    cinema = '''@Composable
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
}

'''
    if marker in t:
        t = t.replace(marker, cinema + marker, 1)
        print("CinemaServersLoading added")
    else:
        # append before last closing of file is hard; try SourceRow
        m2 = "@Composable\nprivate fun SourceRow("
        if m2 in t:
            t = t.replace(m2, cinema + m2, 1)
            print("CinemaServersLoading before SourceRow")
        else:
            raise SystemExit("no place for CinemaServersLoading")

# Ensure Color import if using Color(0xFF...)
if "import androidx.compose.ui.graphics.Color" not in t:
    if "import androidx.compose.ui.graphics.graphicsLayer" in t:
        t = t.replace(
            "import androidx.compose.ui.graphics.graphicsLayer",
            "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.graphicsLayer",
            1,
        )
    elif "import androidx.compose.ui.Modifier" in t:
        t = t.replace(
            "import androidx.compose.ui.Modifier",
            "import androidx.compose.ui.Modifier\nimport androidx.compose.ui.graphics.Color",
            1,
        )

p.write_text(t)
print("done", "CinemaServersLoading" in t)
