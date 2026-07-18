package com.streamflixvip.app.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.onFocusChanged
import coil.compose.AsyncImage
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
 * - Fundo com backdrop cinematográfico real do TMDB (imagem fixa com blur)
 * - Overlay escuro gradiente sobre a imagem
 * - Logo StreamFlixVIP estilizado
 * - Campo de email: OutlinedTextField Material3 (foca, cola, teclado nativo)
 * - OTP: 6 caixinhas individuais com teclado numérico + suporte a colar
 * - Animações de entrada
 */

// Backdrop widescreen real do TMDB para o fundo
private const val TMDB_BACKDROP_BASE = "https://image.tmdb.org/t/p/w1280"
private val BACKGROUND_BACKDROP = "/neeNHeXjMF5fXoCJRsOmkNGC7q.jpg" // Oppenheimer

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

    // Animação de entrada
    var animStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animStarted = true
    }

    val backgroundAlpha by animateFloatAsState(
        targetValue = if (animStarted) 1f else 0f,
        animationSpec = tween(durationMillis = 1200),
        label = "bg_alpha"
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (animStarted) 1f else 0f,
        animationSpec = tween(durationMillis = 800, delayMillis = 400),
        label = "content_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // === FUNDO CINEMATOGRÁFICO FIXO ===
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(backgroundAlpha)
        ) {
            // Imagem backdrop real
            AsyncImage(
                model = TMDB_BACKDROP_BASE + BACKGROUND_BACKDROP,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(8.dp)
            )

            // Overlay escuro gradiente por cima da imagem
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0A0A10).copy(alpha = 0.85f),
                                Color(0xFF0A0A10).copy(alpha = 0.75f),
                                Color(0xFF0A0A10).copy(alpha = 0.95f)
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
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
                    CodeStep(state, viewModel, contentAlpha, onBack = viewModel::goBackToEmail)
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
    val DarkSurface = Color(0xFF15151C)

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

        // Campo de email — OutlinedTextField padrão Material3
        // Funciona: foca ao tocar, cola texto, teclado aparece, cursor visível
        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            placeholder = {
                Text(
                    text = "seu@email.com",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 15.sp
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Gold,
                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Gold,
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = DarkSurface,
                focusedLabelColor = Gold,
                unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
            ),
            textStyle = androidx.compose.ui.text.TextStyle(
                color = Color.White,
                fontSize = 15.sp
            )
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
private fun CodeStep(state: AuthUiState, viewModel: AuthViewModel, alpha: Float, onBack: () -> Unit) {
    val Gold = Color(0xFFD4AF37)
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
    ) {
        // Botão voltar
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Voltar",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        // Título
        Text(
            text = "Verifique seu e-mail",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Enviamos um código de 6 dígitos para",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = state.email,
            fontSize = 13.sp,
            color = Gold,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(28.dp))

        // Campo de código OTP
        OtpInput(
            value = state.code,
            onValueChange = viewModel::onCodeChange,
        )

        Spacer(Modifier.height(24.dp))

        // Botão verificar
        Button(
            onClick = viewModel::confirmCode,
            enabled = !state.isLoading && state.code.length >= 6,
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
                    text = "Verificar código",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Reenviar código
        TextButton(
            onClick = viewModel::sendCode,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (state.isLoading && state.code.isEmpty())
                    "Reenviando..."
                else
                    "Não recebeu o código? Reenviar",
                color = Gold,
                fontSize = 13.sp
            )
        }
    }
}

// ========== OTP INPUT (6 CAIXINHAS) ==========

@Composable
private fun OtpInput(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val Gold = Color(0xFFD4AF37)
    val DarkSurface = Color(0xFF1A1A28)
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    // Posição do cursor
    val cursorPos = minOf(value.length, 6)

    // Campo de input real e visível - abrange toda a área das caixinhas
    BasicTextField(
        value = value.take(6),
        onValueChange = { input ->
            // Filtra só dígitos, máximo 6
            val digits = input.filter { it.isDigit() }
            if (digits.length <= 6) {
                onValueChange(digits)
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword
        ),
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            color = Color.Transparent, // texto invisível (usamos caixinhas custom)
            fontSize = 1.sp,
            lineHeight = 1.sp,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused },
        decorationBox = { innerTextField ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Caixinhas visuais
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    repeat(6) { index ->
                        val digit = if (index < value.length) value[index] else ' '
                        val isActive = isFocused && index == cursorPos

                        Box(
                            modifier = Modifier
                                .width(48.dp)
                                .height(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isActive) DarkSurface
                                    else Color(0xFF12121A)
                                )
                                .border(
                                    width = if (isActive) 1.5.dp else 1.dp,
                                    color = if (isActive) Gold else Color.White.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { focusRequester.requestFocus() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (index < value.length) {
                                Text(
                                    text = digit.toString(),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            } else if (isActive) {
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(28.dp)
                                        .background(Gold)
                                )
                            }
                        }
                    }
                }
            }
        }
    )

    // Foca automaticamente quando entra na tela de código
    LaunchedEffect(Unit) {
        delay(500)
        focusRequester.requestFocus()
    }
}
