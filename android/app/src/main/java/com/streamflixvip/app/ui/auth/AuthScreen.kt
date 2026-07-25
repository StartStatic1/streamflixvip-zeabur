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
private val DarkBg = Color(0xFF0A0A10)
private const val TMDB_POSTER_BASE = "https://image.tmdb.org/t/p/w342"

/**
 * Tela de login por e-mail + código de 6 dígitos — mesmo fluxo de duas
 * etapas que o site já usa (signInWithOtp -> verifyOtp), só que em UI
 * nativa Compose em vez de modal HTML.
 *
 * Duas correções nesta versão:
 * 1. O Column antigo usava fillMaxSize() + Arrangement.Center SEM
 *    scroll — quando o teclado abre, o conteúdo centralizado não tinha
 *    pra onde "sobrar espaço", ficando espremido/cortado. Agora rola
 *    (verticalScroll) e respeita o teclado (imePadding), então digitar
 *    o e-mail ou o código nunca fica travado atrás do teclado.
 * 2. Fundo estático deu lugar a um "rolo de filme" de pôsteres reais
 *    (TMDB populares) rolando devagar atrás do formulário, com um
 *    overlay escuro por cima pra manter o foco no login.
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

        // Overlay escuro por cima do rolo de pôsteres — forte o
        // suficiente pra não competir com o formulário, mas ainda deixa
        // o movimento visível no fundo (era esse o pedido: "sem tirar
        // foco da tela login").
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            DarkBg.copy(alpha = 0.88f),
                            DarkBg.copy(alpha = 0.94f),
                            DarkBg.copy(alpha = 0.88f),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding() // empurra o conteúdo pra cima do teclado em vez de deixá-lo cobrir os campos
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "StreamFlixVIP",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Gold,
            )
            Spacer(Modifier.height(8.dp))

            when (state.step) {
                AuthStep.EnterEmail -> EmailStep(state, viewModel)
                AuthStep.EnterCode -> CodeStep(state, viewModel)
                AuthStep.LoggedIn -> Unit // tratado acima
            }

            state.infoMessage?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = Gold, fontSize = 13.sp)
            }
            state.errorMessage?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            // Espaço extra no fim: garante que o botão nunca fica colado
            // na borda inferior do teclado quando a rolagem chega ao fim.
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EmailStep(state: AuthUiState, viewModel: AuthViewModel) {
    Text("Entre com seu e-mail para continuar", fontSize = 14.sp, color = Color.White)
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = state.email,
        onValueChange = viewModel::onEmailChange,
        label = { Text("E-mail") },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = viewModel::sendCode,
        enabled = !state.isLoading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
        } else {
            Text("Continuar")
        }
    }
}

@Composable
private fun CodeStep(state: AuthUiState, viewModel: AuthViewModel) {
    Text("Digite o código de 6 dígitos enviado para ${state.email}", fontSize = 14.sp, color = Color.White)
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = state.code,
        onValueChange = { if (it.length <= 6) viewModel.onCodeChange(it) },
        label = { Text("Código") },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = viewModel::confirmCode,
        enabled = !state.isLoading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
        } else {
            Text("Entrar")
        }
    }
}

/**
 * Fundo de "rolo de filme": várias fileiras de pôsteres reais (TMDB
 * populares) rolando devagar, cada fileira numa velocidade/direção
 * levemente diferente pra parecer orgânico em vez de um carrossel único
 * repetitivo. Preenche a tela inteira sem buracos duplicando a lista de
 * pôsteres o quanto for preciso.
 */
@Composable
private fun MovingPosterBackground() {
    var posterPaths by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        val repository = CatalogRepository()
        val movies = repository.getPopularMovies()
        val series = repository.getPopularSeries()
        posterPaths = (movies + series).mapNotNull { it.poster_path }.distinct()
    }

    if (posterPaths.isEmpty()) return // enquanto carrega, só o fundo escuro sólido aparece — nunca um flash de espaço vazio/quebrado

    // Reparte a lista em 4 fileiras horizontais, cada uma repetida
    // várias vezes seguidas pra nunca deixar buraco na tela mesmo em
    // telas bem largas — e cada fileira roda numa velocidade diferente.
    val rows = remember(posterPaths) {
        posterPaths.chunked((posterPaths.size / 4).coerceAtLeast(1)).take(4)
    }

    Column(Modifier.fillMaxSize()) {
        rows.forEachIndexed { index, rowPosters ->
            if (rowPosters.isEmpty()) return@forEachIndexed
            PosterRow(
                posters = rowPosters,
                durationMillis = 18_000 + index * 4_000, // fileiras diferentes = velocidades diferentes, evita sincronismo repetitivo
                reverse = index % 2 == 1, // alterna direção pra dar mais sensação de "rolo de filme" do que esteira única
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
    // Repete a lista várias vezes — a translação abaixo desloca só a
    // largura de UMA repetição (não da fileira toda), então enquanto uma
    // cópia sai pela esquerda a próxima já está entrando pela direita,
    // sem deixar buraco vazio no meio da animação.
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
            .padding(vertical = 3.dp)
            .graphicsLayerTranslate(offset / 6f), // só 1/6 da largura total = 1 repetição, não a fileira inteira
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeatedPosters.forEach { path ->
            AsyncImage(
                model = "$TMDB_POSTER_BASE$path",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(70.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp)),
            )
        }
    }
}

/** Translada a fileira inteira horizontalmente como fração da própria largura, criando o efeito de rolagem contínua. */
private fun Modifier.graphicsLayerTranslate(fraction: Float): Modifier =
    this.then(
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                placeable.place(x = (fraction * placeable.width).toInt(), y = 0)
            }
        },
    )
