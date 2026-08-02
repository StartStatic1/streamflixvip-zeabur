package com.streamflixvip.app.ui.auth

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streamflixvip.app.data.CatalogRepository

private val Gold = Color(0xFFD4AF37)
private val DarkBg = Color(0xFF05050A)
private const val TMDB_POSTER_BASE = "https://image.tmdb.org/t/p/w342"

/**
 * Login por e-mail + código — formulário em card flutuante sobre o rolo de posters.
 */
@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onLoggedIn: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    if (state.step is AuthStep.LoggedIn) {
        onLoggedIn()
        return
    }

    Box(Modifier.fillMaxSize().background(DarkBg)) {
        MovingPosterBackground()

        // Overlay mais suave — posters ficam mais visíveis (efeito "no ar")
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            DarkBg.copy(alpha = 0.78f),
                            DarkBg.copy(alpha = 0.88f),
                            DarkBg.copy(alpha = 0.78f),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Card flutuante do formulário
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF0F0F16).copy(alpha = 0.92f),
                shadowElevation = 16.dp,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text(
                        "StreamFlixVIP",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Gold,
                    )
                    Spacer(Modifier.height(6.dp))

                    when (state.step) {
                        AuthStep.EnterEmail -> EmailStep(state, viewModel)
                        AuthStep.EnterCode -> CodeStep(state, viewModel)
                        AuthStep.LoggedIn -> Unit
                    }

                    state.infoMessage?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = Gold, fontSize = 13.sp)
                    }
                    state.errorMessage?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EmailStep(state: AuthUiState, viewModel: AuthViewModel) {
    Text("Entre com seu e-mail para continuar", fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f))
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = state.email,
        onValueChange = viewModel::onEmailChange,
        label = { Text("E-mail") },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Gold,
            unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
            focusedLabelColor = Gold,
            cursorColor = Gold,
        ),
        shape = RoundedCornerShape(14.dp),
    )
    Spacer(Modifier.height(18.dp))
    Button(
        onClick = viewModel::sendCode,
        enabled = !state.isLoading,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Gold,
            contentColor = Color.Black,
        ),
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp, color = Color.Black)
        } else {
            Text("Continuar", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun CodeStep(state: AuthUiState, viewModel: AuthViewModel) {
    Text(
        "Digite o código de 6 dígitos enviado para ${state.email}",
        fontSize = 14.sp,
        color = Color.White.copy(alpha = 0.85f),
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = state.code,
        onValueChange = { if (it.length <= 6) viewModel.onCodeChange(it) },
        label = { Text("Código") },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Gold,
            unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
            focusedLabelColor = Gold,
            cursorColor = Gold,
        ),
        shape = RoundedCornerShape(14.dp),
    )
    Spacer(Modifier.height(18.dp))
    Button(
        onClick = viewModel::confirmCode,
        enabled = !state.isLoading,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Gold,
            contentColor = Color.Black,
        ),
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp, color = Color.Black)
        } else {
            Text("Entrar", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun MovingPosterBackground() {
    var posterPaths by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        val repository = CatalogRepository()
        val movies = repository.getPopularMovies()
        val series = repository.getPopularSeries()
        posterPaths = (movies + series).mapNotNull { it.poster_path }.distinct()
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
                model = "$TMDB_POSTER_BASE$path",
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
