package com.streamflixvip.app.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Tela de login por e-mail + código de 6 dígitos — redesign premium.
 *
 * Características:
 * - Background com "encartes flutuantes" (títulos de filmes rodando)
 * - Logo StreamFlixVIP em branco com brilho dourado (não todo amarelo)
 * - Frase de efeito
 * - Campos estilizados com bordas suaves
 * - OTP com 6 caixinhas individuais (pin input)
 * - Animações de entrada
 */

// Títulos fictícios para os encartes flutuantes ao fundo
private val floatingTitles = listOf(
    "Oppenheimer", "Barbie", "Dune: Part Two", "The Batman",
    "Interstellar", "Breaking Bad", "Stranger Things", "The Witcher",
    "House of the Dragon", "Wednesday", "Squid Game", "Arcane",
    "The Last of Us", "Euphoria", "Peaky Blinders", "Dark",
    "Money Heist", "Narcos", "Ozark", "Better Call Saul"
)

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

    val Gold = Color(0xFFD4AF37)
    val DarkBg = Color(0xFF0A0A10)
    val DarkSurface = Color(0xFF15151C)

    // Animação de entrada
    val contentAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 800, delayMillis = 300),
        label = "content_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // === BACKGROUND COM ENCARTE FLUTUANTES ===
        FloatingPosterBackground(modifier = Modifier.alpha(0.12f))

        // === GRADIENTE SUTIL NO FUNDO ===
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Gold.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = Offset(size.width / 2f, size.height * 0.35f),
                    radius = size.width * 0.8f
                )
            )
        }

        // === CONTEÚDO PRINCIPAL ===
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // LOGO
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(contentAlpha)) {

                // Ícone de play acima do logo
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            color = Gold.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Gold,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // "StreamFlix" em branco
                Text(
                    text = "StreamFlix",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                // "VIP" em dourado
                Text(
                    text = "VIP",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Gold
                )

                Spacer(Modifier.height(8.dp))

                // Frase de efeito
                Text(
                    text = "Seu cinema premium na palma da mão",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center
                )

                // Linha decorativa
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(2.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Gold.copy(alpha = 0.5f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            Spacer(Modifier.height(36.dp))

            // FORMULÁRIO DE LOGIN
            when (state.step) {
                AuthStep.EnterEmail -> {
                    EmailStep(state, viewModel, contentAlpha)
                }
                AuthStep.EnterCode -> {
                    CodeStep(state, viewModel, contentAlpha)
                }
                AuthStep.LoggedIn -> Unit
            }

            // Mensagens
            AnimatedVisibility(
                visible = state.infoMessage != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut()
            ) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = state.infoMessage ?: "",
                    color = Color(0xFF4CAF50),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            AnimatedVisibility(
                visible = state.errorMessage != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut()
            ) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = state.errorMessage ?: "",
                    color = Color(0xFFFF3D3D),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ========== EMAIL STEP ==========

@Composable
private fun EmailStep(state: AuthUiState, viewModel: AuthViewModel, alpha: Float) {
    val Gold = Color(0xFFD4AF37)
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(600)
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
    ) {
        // Título do passo
        Text(
            text = "Bem-vindo de volta",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Entre com seu e-mail para continuar",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.5f)
        )

        Spacer(Modifier.height(24.dp))

        // Campo de email estilizado
        StyledTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            placeholder = "seu@email.com",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            isFocused = isFocused,
            onFocusChange = { isFocused = it },
            focusRequester = focusRequester,
            leadingIcon = Icons.Default.Email
        )

        Spacer(Modifier.height(28.dp))

        // Botão continuar
        Button(
            onClick = viewModel::sendCode,
            enabled = !state.isLoading && state.email.contains("@"),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Gold,
                contentColor = Color(0xFF0A0A10),
                disabledContainerColor = Gold.copy(alpha = 0.3f)
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 4.dp
            )
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF0A0A10)
                )
            } else {
                Text(
                    text = "Continuar",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Link de ajuda
        Text(
            text = "Não tem conta? Basta inserir seu e-mail e criar automaticamente.",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.35f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ========== CODE STEP ==========

@Composable
private fun CodeStep(state: AuthUiState, viewModel: AuthViewModel, alpha: Float) {
    val Gold = Color(0xFFD4AF37)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
    ) {
        // Título do passo
        Text(
            text = "Verificação",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Digite o código de 6 dígitos enviado para",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = state.email,
            fontSize = 13.sp,
            color = Gold,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(28.dp))

        // PIN Input com 6 caixinhas
        PinInputField(
            code = state.code,
            onCodeChange = { if (it.length <= 6) viewModel.onCodeChange(it) },
            isLoading = state.isLoading
        )

        Spacer(Modifier.height(28.dp))

        // Botão entrar
        Button(
            onClick = viewModel::confirmCode,
            enabled = !state.isLoading && state.code.length == 6,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Gold,
                contentColor = Color(0xFF0A0A10),
                disabledContainerColor = Gold.copy(alpha = 0.3f)
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 4.dp
            )
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF0A0A10)
                )
            } else {
                Text(
                    text = "Entrar",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Botão reenviar código
        TextButton(
            onClick = viewModel::sendCode,
            enabled = !state.isLoading
        ) {
            Text(
                text = "Reenviar código",
                color = Gold,
                fontSize = 13.sp
            )
        }
    }
}

// ========== CAMPO DE TEXTO ESTILIZADO ==========

@Composable
private fun StyledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardOptions: KeyboardOptions,
    isFocused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    leadingIcon: ImageVector,
) {
    val Gold = Color(0xFFD4AF37)
    val borderColor = if (isFocused) Gold else Color.White.copy(alpha = 0.15f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1A28))
            .border(
                width = if (isFocused) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            keyboardOptions = keyboardOptions,
            singleLine = true,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { onFocusChange(it.isFocused) },
            textStyle = androidx.compose.ui.text.TextStyle(
                color = Color.White,
                fontSize = 15.sp
            ),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = if (isFocused) Gold else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = Color.White.copy(alpha = 0.35f),
                            fontSize = 15.sp
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

// ========== PIN INPUT (6 CAIXINHAS) ==========

@Composable
private fun PinInputField(
    code: String,
    onCodeChange: (String) -> Unit,
    isLoading: Boolean,
) {
    val Gold = Color(0xFFD4AF37)
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(400)
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Campo invisível mas grande o suficiente para capturar digitação e colar
            BasicTextField(
                value = code,
                onValueChange = { input ->
                    // Filtra apenas dígitos e limita a 6
                    val filtered = input.filter { it.isDigit() }.take(6)
                    onCodeChange(filtered)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier
                    .width(300.dp)
                    .height(60.dp)
                    .alpha(0f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { isFocused = it.isFocused },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 24.sp)
            )

            // 6 caixinhas visuais — clicáveis para focar
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.clickable {
                    focusRequester.requestFocus()
                }
            ) {
                repeat(6) { index ->
                    val digit = if (index < code.length) code[index] else null
                    val isActive = index == code.length && isFocused

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isActive) Color(0xFF1A1A28) else Color(0xFF12121A)
                            )
                            .border(
                                width = if (isActive) 1.5.dp else 1.dp,
                                color = if (isActive) Gold else Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (digit != null) {
                            Text(
                                text = digit.toString(),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        } else if (isActive) {
                            // Cursor piscante
                            val cursorVisible by rememberInfiniteTransition(label = "cursor").animateFloat(
                                initialValue = 1f,
                                targetValue = 0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(500),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "cursor_blink"
                            )
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(24.dp)
                                    .background(Gold.copy(alpha = cursorVisible))
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Instrução de colar
        if (code.isEmpty()) {
            Text(
                text = "Toque nas caixinhas e digite ou cole o código",
                color = Gold.copy(alpha = 0.5f),
                fontSize = 11.sp
            )
        }
    }
}

// ========== BACKGROUND FLUTUANTE (PÔSTERES/ENCARTES) ==========

// Encartes com títulos e cores representativas para simular pôsteres
private val floatingPosters = listOf(
    "Oppenheimer" to Color(0xFF1A237E),
    "Barbie" to Color(0xFFE91E63),
    "Dune: Part Two" to Color(0xFF795548),
    "The Batman" to Color(0xFF212121),
    "Interstellar" to Color(0xFF0D47A1),
    "Breaking Bad" to Color(0xFF2E7D32),
    "Stranger Things" to Color(0xFFB71C1C),
    "The Witcher" to Color(0xFF37474F),
    "House of the Dragon" to Color(0xFF4E342E),
    "Wednesday" to Color(0xFF1B1B2F),
    "Squid Game" to Color(0xFF1A237E),
    "Arcane" to Color(0xFF311B92),
    "The Last of Us" to Color(0xFF33691E),
    "Euphoria" to Color(0xFF6A1B9A),
    "Peaky Blinders" to Color(0xFF263238),
    "Dark" to Color(0xFF1B1B2F),
    "Money Heist" to Color(0xFFF44336),
    "Narcos" to Color(0xFF424242),
    "Ozark" to Color(0xFF004D40),
    "Better Call Saul" to Color(0xFF1A237E),
)

@Composable
private fun FloatingPosterBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "bg_floating")

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 40.dp)
    ) {
        // 4 linhas de pôsteres flutuantes
        repeat(4) { rowIndex ->
            val scrollAnim by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 25000 + rowIndex * 4000,
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Restart
                ),
                label = "scroll_${rowIndex}"
            )

            val direction = if (rowIndex % 2 == 0) 1f else -1f
            val scrollMultiplier = (scrollAnim * 2f - 1f) * direction
            val offsetX = (scrollMultiplier * 600).dp

            val rowY = (rowIndex * 25).toFloat() // posição vertical percentual

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(
                        x = offsetX,
                        y = (rowY / 100f * 800).dp // posiciona verticalmente
                    ),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Repete 3x para scroll contínuo
                for (i in 0 until 3) {
                    floatingPosters.forEach { (title, accentColor) ->
                        // Card estilo pôster mini
                        Box(
                            modifier = Modifier
                                .width(70.dp)
                                .height(100.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            accentColor,
                                            Color(0xFF0A0A10)
                                        )
                                    )
                                )
                                .alpha(0.12f + (rowIndex % 2) * 0.03f), // opacidade sutil
                        ) {
                            // Título do filme embaixo
                            Text(
                                text = title,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(4.dp)
                            )
                            // Linha decorativa no topo (simula imagem)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(accentColor.copy(alpha = 0.5f))
                            )
                        }
                    }
                }
            }
        }
    }
}
