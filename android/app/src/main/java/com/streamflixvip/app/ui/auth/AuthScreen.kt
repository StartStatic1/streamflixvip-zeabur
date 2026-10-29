package com.streamflixvip.app.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streamflixvip.app.data.CatalogRepository
import com.streamflixvip.app.network.TmdbImages
import com.streamflixvip.app.ui.brand.BrandMark
import com.streamflixvip.app.ui.theme.AccentStore
import kotlinx.coroutines.delay

private val DarkBg = Color(0xFF08090D)

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onLoggedIn: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val accent = AccentStore.current

    // efeito, não durante a composição (antes chamava onLoggedIn() no meio do desenho)
    LaunchedEffect(state.step) { if (state.step is AuthStep.LoggedIn) onLoggedIn() }
    if (state.step is AuthStep.LoggedIn) return

    Box(Modifier.fillMaxSize().background(DarkBg)) {
        MovingPosterBackground()
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to DarkBg.copy(alpha = 0.70f),
                    0.5f to DarkBg.copy(alpha = 0.88f),
                    1f to DarkBg.copy(alpha = 0.97f),
                ),
            ),
        )
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(accent.accent.copy(alpha = 0.18f), Color.Transparent), radius = 900f)))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandMark(64.dp, accent.accent, accent.accent2)
            Spacer(Modifier.height(12.dp))
            Text("STREAMFLIX", fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp, color = Color.White)
            Text("VIP", fontSize = 11.sp, letterSpacing = 7.sp, color = accent.accent, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(
                "Filmes, séries e TV ao vivo em um só lugar",
                fontSize = 14.sp, color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Benefit(Icons.Filled.Movie, "Filmes")
                Benefit(Icons.Filled.Tv, "Séries")
                Benefit(Icons.Filled.LiveTv, "Ao vivo")
            }
            Spacer(Modifier.height(22.dp))

            Box(
                Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF12141B).copy(alpha = 0.86f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(24.dp))
                    .padding(22.dp),
            ) {
                Column {
                    AnimatedContent(
                        targetState = state.step,
                        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                        label = "authStep",
                    ) { step ->
                        Column {
                            when (step) {
                                AuthStep.EnterEmail -> EmailStep(state, viewModel)
                                AuthStep.EnterCode -> CodeStep(state, viewModel)
                                AuthStep.LoggedIn -> Unit
                            }
                        }
                    }
                    state.infoMessage?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                    state.errorMessage?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Sem senha: enviamos um código para o seu e-mail.",
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f), textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Benefit(icon: ImageVector, label: String) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
    }
}

@Composable
private fun PrimaryButton(text: String, loading: Boolean, onClick: () -> Unit) {
    val accent = AccentStore.current
    Button(
        onClick = onClick,
        enabled = !loading,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = accent.accent, contentColor = accent.onAccent),
    ) {
        if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = accent.onAccent)
        else Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun EmailStep(state: AuthUiState, viewModel: AuthViewModel) {
    val accent = AccentStore.current
    Text("Entrar", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
    Spacer(Modifier.height(4.dp))
    Text("Digite seu e-mail. Enviamos um código de acesso.", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = state.email,
        onValueChange = viewModel::onEmailChange,
        placeholder = { Text("seu@email.com") },
        leadingIcon = { Icon(Icons.Filled.Email, null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { viewModel.sendCode() }),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent.accent,
            unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
            focusedLeadingIconColor = accent.accent,
            cursorColor = accent.accent,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedContainerColor = Color.White.copy(alpha = 0.04f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
        ),
        shape = RoundedCornerShape(14.dp),
    )
    Spacer(Modifier.height(16.dp))
    PrimaryButton("Enviar código", state.isLoading) { viewModel.sendCode() }
}

@Composable
private fun CodeStep(state: AuthUiState, viewModel: AuthViewModel) {
    val accent = AccentStore.current
    var resendIn by remember { mutableIntStateOf(30) }
    LaunchedEffect(resendIn) { if (resendIn > 0) { delay(1000); resendIn -= 1 } }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // 6 dígitos: entra sozinho
    LaunchedEffect(state.code) { if (state.code.length == 6 && !state.isLoading) viewModel.confirmCode() }

    Text("Digite o código", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
    Spacer(Modifier.height(4.dp))
    Text("Enviamos 6 números para ${state.email}", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
    Spacer(Modifier.height(18.dp))
    BasicTextField(
        value = state.code,
        onValueChange = { v -> viewModel.onCodeChange(v.filter { it.isDigit() }.take(6)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { viewModel.confirmCode() }),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        decorationBox = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0 until 6) {
                    val ch = state.code.getOrNull(i)?.toString() ?: ""
                    val active = i == state.code.length
                    Box(
                        Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(
                                if (active) 2.dp else 1.dp,
                                if (active) accent.accent else Color.White.copy(alpha = 0.16f),
                                RoundedCornerShape(12.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(ch, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        },
    )
    Spacer(Modifier.height(18.dp))
    PrimaryButton("Entrar", state.isLoading) { viewModel.confirmCode() }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { viewModel.backToEmail() }, enabled = !state.isLoading) {
            Text("Trocar e-mail", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        }
        TextButton(
            onClick = { resendIn = 30; viewModel.sendCode() },
            enabled = !state.isLoading && resendIn == 0,
        ) {
            Text(if (resendIn > 0) "Reenviar em ${resendIn}s" else "Reenviar código", color = if (resendIn > 0) Color.White.copy(alpha = 0.4f) else accent.accent, fontSize = 13.sp)
        }
    }
}

@Composable
private fun MovingPosterBackground() {
    var posterPaths by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        runCatching {
            val repository = CatalogRepository()
            val movies = repository.getPopularMovies()
            val series = repository.getPopularSeries()
            posterPaths = (movies + series).mapNotNull { it.poster_path }.distinct()
        }
    }

    if (posterPaths.isEmpty()) return

    val rows = remember(posterPaths) {
        posterPaths.chunked((posterPaths.size / 4).coerceAtLeast(1)).take(4)
    }

    Column(Modifier.fillMaxSize()) {
        rows.forEachIndexed { index, rowPosters ->
            if (rowPosters.isEmpty()) return@forEachIndexed
            PosterRow(
                posters = rowPosters,
                durationMillis = 20_000 + index * 5_000,
                reverse = index % 2 == 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PosterRow(
    posters: List<String>,
    durationMillis: Int,
    reverse: Boolean,
    modifier: Modifier = Modifier,
) {
    val repeatedPosters = remember(posters) { List(6) { posters }.flatten() }

    val transition = rememberInfiniteTransition(label = "poster_row")
    val offset by transition.animateFloat(
        initialValue = if (reverse) -1f else 0f,
        targetValue = if (reverse) 0f else -1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "poster_row_offset",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .graphicsLayerTranslate(offset / 6f),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeatedPosters.forEach { path ->
            AsyncImage(
                model = TmdbImages.poster(path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(72.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp)),
            )
        }
    }
}

private fun Modifier.graphicsLayerTranslate(fraction: Float): Modifier =
    this.then(
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                placeable.place(x = (fraction * placeable.width).toInt(), y = 0)
            }
        },
    )
