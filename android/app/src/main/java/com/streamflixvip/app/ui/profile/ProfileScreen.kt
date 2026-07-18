package com.streamflixvip.app.ui.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.streamflixvip.app.ui.auth.AuthViewModel
import com.streamflixvip.app.ui.vip.VipSection
import com.streamflixvip.app.ui.vip.VipViewModel

/**
 * Aba Perfil premium do StreamFlixVIP.
 *
 * Seções:
 * 1. Header com avatar, nome/email e badge de membro
 * 2. Seção VIP (resgate de código + status)
 * 3. Contato Oficial (email + Telegram)
 * 4. Preferências (tema, notificações) — persistidas em SharedPreferences
 * 5. Informações do App (versão, sobre)
 * 6. Termos de Uso e Política de Privacidade com conteúdo real
 * 7. Sair da conta
 */

// Chaves de SharedPreferences para preferências
private const val PREFS_NAME = "streamflixvip_prefs"
private const val KEY_DARK_MODE = "dark_mode_enabled"
private const val KEY_NOTIFICATIONS = "notifications_enabled"

private fun Context.getPrefs() = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

@Composable
fun ProfileScreen(
    authViewModel: AuthViewModel,
    userId: String?,
    userEmail: String?,
    onSignedOut: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getPrefs() }
    val Gold = Color(0xFFD4AF37)
    val DarkBg = Color(0xFF0A0A10)
    val DarkSurface = Color(0xFF15151C)
    val DarkSurfaceLight = Color(0xFF232330)

    // Estado para diálogo de termos/privacidade
    var dialogContent by remember { mutableStateOf<DialogContent?>(null) }

    // Preferências com persistência em SharedPreferences
    var darkModeEnabled by remember { mutableStateOf(prefs.getBoolean(KEY_DARK_MODE, true)) }
    var notificationsEnabled by remember { mutableStateOf(prefs.getBoolean(KEY_NOTIFICATIONS, true)) }

    // Salvar preferências quando mudarem
    LaunchedEffect(darkModeEnabled) {
        prefs.edit().putBoolean(KEY_DARK_MODE, darkModeEnabled).apply()
    }
    LaunchedEffect(notificationsEnabled) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS, notificationsEnabled).apply()
    }

    // Animação de entrada do header
    val headerAnim by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 600),
        label = "header_anim"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
    ) {
        // === HEADER COM GRADIENTE ===
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Gold.copy(alpha = 0.15f),
                            DarkBg
                        ),
                        startY = 0f,
                        endY = 300f
                    )
                )
                .alpha(headerAnim),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Avatar com coroa
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Gold.copy(alpha = 0.3f), DarkSurfaceLight),
                                    start = Offset(0f, 0f),
                                    end = Offset(80f, 80f)
                                )
                            )
                            .border(2.dp, Gold.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    // Badge de coroa para VIP
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-2).dp)
                            .size(28.dp)
                            .background(Gold, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = DarkBg,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Email/identificação
                Text(
                    text = userEmail?.substringBefore("@") ?: "Usuário",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = userEmail ?: "—",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )

                // Badge de membro
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Gold.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Membro StreamFlixVIP",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Gold
                        )
                    }
                }
            }
        }

        // === CONTEÚDO PRINCIPAL ===
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            // === SEÇÃO VIP ===
            if (userId != null) {
                val vipViewModel: VipViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            return VipViewModel(userId) as T
                        }
                    },
                )
                SectionTitle(title = "Assinatura VIP")
                VipSection(viewModel = vipViewModel)
                Spacer(Modifier.height(28.dp))
            }

            // === CONTATO OFICIAL ===
            SectionTitle(title = "Contato Oficial")

            // Email
            ProfileInfoCard(
                icon = Icons.Default.Email,
                title = "E-mail de Suporte",
                subtitle = "streamflixvip@outlook.com",
                iconTint = Color(0xFF4FC3F7),
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:streamflixvip@outlook.com")
                        putExtra(Intent.EXTRA_SUBJECT, "Suporte StreamFlixVIP")
                    }
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        // Fallback: copiar email
                    }
                }
            )

            Spacer(Modifier.height(8.dp))

            // Telegram
            ProfileInfoCard(
                icon = Icons.Default.Forum,
                title = "Telegram Oficial",
                subtitle = "@streamflixofc",
                iconTint = Color(0xFF29B6F6),
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/streamflixofc"))
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        // Fallback
                    }
                }
            )

            Spacer(Modifier.height(28.dp))

            // === PREFERÊNCIAS ===
            SectionTitle(title = "Preferências")

            // Dark Mode — Icons.Default.Bedtime (substitui DarkMode que não existe)
            ProfileToggleCard(
                icon = Icons.Default.Bedtime,
                title = "Modo Escuro",
                subtitle = "Tema escuro do aplicativo",
                iconTint = Color(0xFF7E57C2),
                checked = darkModeEnabled,
                onCheckedChange = { darkModeEnabled = it }
            )

            Spacer(Modifier.height(8.dp))

            // Notificações — persistido em SharedPreferences
            ProfileToggleCard(
                icon = Icons.Default.Notifications,
                title = "Notificações",
                subtitle = "Receba novidades e atualizações",
                iconTint = Color(0xFFFF7043),
                checked = notificationsEnabled,
                onCheckedChange = { notificationsEnabled = it }
            )

            Spacer(Modifier.height(28.dp))

            // === INFORMAÇÕES DO APP ===
            SectionTitle(title = "Sobre")

            ProfileInfoCard(
                icon = Icons.Default.Info,
                title = "Versão",
                subtitle = "1.0.0",
                iconTint = Color(0xFF78909C),
                onClick = {}
            )

            Spacer(Modifier.height(8.dp))

            // Termos de Uso — abre diálogo com conteúdo real
            ProfileInfoCard(
                icon = Icons.Default.Description,
                title = "Termos de Uso",
                subtitle = "Leia nossos termos de serviço",
                iconTint = Color(0xFF78909C),
                onClick = {
                    dialogContent = DialogContent(
                        title = "Termos de Uso",
                        text = buildTermsText()
                    )
                }
            )

            Spacer(Modifier.height(8.dp))

            // Política de Privacidade — abre diálogo com conteúdo real
            // Ícones.Default.PrivacyTip NÃO existe no extended → usar Icons.Default.Shield
            ProfileInfoCard(
                icon = Icons.Default.Shield,
                title = "Política de Privacidade",
                subtitle = "Como protegemos seus dados",
                iconTint = Color(0xFF78909C),
                onClick = {
                    dialogContent = DialogContent(
                        title = "Política de Privacidade",
                        text = buildPrivacyText()
                    )
                }
            )

            Spacer(Modifier.height(28.dp))

            // === BOTÃO SAIR ===
            Button(
                onClick = {
                    authViewModel.signOut()
                    onSignedOut()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2A2A3A),
                    contentColor = Color(0xFFEF5350)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Sair da Conta",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(32.dp))

            // Footer
            Text(
                text = "StreamFlixVIP © 2025",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.3f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    // Diálogo para Termos e Privacidade
    dialogContent?.let { content ->
        ContentDialog(
            title = content.title,
            text = content.text,
            onDismiss = { dialogContent = null }
        )
    }
}

// Dados do diálogo
private data class DialogContent(val title: String, val text: String)

// Texto dos Termos de Uso
private fun buildTermsText(): String {
    return """Termos de Uso do StreamFlixVIP

1. Aceitação dos Termos
Ao utilizar o aplicativo StreamFlixVIP, você concorda com estes Termos de Uso. Caso não concorde, por favor, não utilize o aplicativo.

2. Descrição do Serviço
O StreamFlixVIP é um aplicativo de streaming de conteúdo audiovisual. O acesso ao conteúdo premium pode requerer a ativação de um código VIP.

3. Conta do Usuário
Cada usuário é responsável por manter a confidencialidade de sua conta e por todas as atividades que ocorram sob ela. É proibido compartilhar sua conta com terceiros não autorizados.

4. Códigos VIP
Os códigos VIP fornecidos têm prazo de validade e são válidos para um único uso, salvo indicação em contrário. A revenda ou redistribuição de códigos é estritamente proibida.

5. Conduta do Usuário
É proibido:
- Utilizar o aplicativo para fins ilegais ou não autorizados;
- Tentar contornar mecanismos de segurança;
- Copiar, redistribuir ou gravar conteúdo protegido.

6. Suspensão e Encerramento
Reservamo-nos o direito de suspender ou encerrar contas que violem estes termos, sem aviso prévio.

7. Isenção de Responsabilidade
O serviço é fornecido "como está", sem garantias expressas ou implícitas. Não nos responsabilizamos por interrupções temporárias do serviço.

8. Alterações nos Termos
Podemos atualizar estes Termos de Uso periodicamente. O uso continuado do aplicativo após alterações constitui aceitação dos novos termos.

9. Contato
Para dúvidas ou suporte, entre em contato pelo e-mail streamflixvip@outlook.com ou pelo Telegram oficial @streamflixofc.

Última atualização: Julho de 2025"""
}

// Texto da Política de Privacidade
private fun buildPrivacyText(): String {
    return """Política de Privacidade do StreamFlixVIP

1. Coleta de Informações
Coletamos apenas as informações necessárias para o funcionamento do serviço:
- Endereço de e-mail (para criação e autenticação de conta);
- Dados de uso do aplicativo (para melhorar a experiência).

2. Uso das Informações
Seus dados são utilizados exclusivamente para:
- Autenticar sua conta e fornecer acesso ao conteúdo;
- Enviar atualizações relevantes sobre o serviço;
- Melhorar a funcionalidade e performance do aplicativo.

3. Compartilhamento de Dados
Não vendemos, alugamos ou compartilhamos seus dados pessoais com terceiros para fins de marketing. Dados podem ser compartilhados apenas quando exigido por lei.

4. Segurança
Implementamos medidas de segurança adequadas para proteger suas informações contra acesso não autorizado, alteração ou destruição.

5. Armazenamento
Seus dados são armazenados de forma segura e são retidos apenas pelo tempo necessário para fornecer o serviço.

6. Seus Direitos
Você pode solicitar a exclusão de sua conta e dados a qualquer momento através do e-mail streamflixvip@outlook.com.

7. Cookies e Tecnologias Similares
O aplicativo pode utilizar identificadores locais para manter sua sessão ativa e preferências de uso.

8. Alterações nesta Política
Podemos atualizar esta Política de Privacidade periodicamente. Notificaremos sobre mudanças significativas através do aplicativo.

9. Contato
Para questões sobre privacidade, entre em contato: streamflixvip@outlook.com

Última atualização: Julho de 2025"""
}

// ========== COMPONENTES AUXILIARES ==========

@Composable
private fun SectionTitle(title: String) {
    Spacer(Modifier.height(4.dp))
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White.copy(alpha = 0.8f),
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
private fun ProfileInfoCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color,
    onClick: () -> Unit,
) {
    val DarkSurfaceLight = Color(0xFF232330)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = DarkSurfaceLight
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.45f)
                )
            }

            // Icons.Default.ChevronRight NÃO existe no extended → usar Icons.Default.NavigateNext
            Icon(
                imageVector = Icons.Default.NavigateNext,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ProfileToggleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val DarkSurfaceLight = Color(0xFF232330)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = DarkSurfaceLight
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.45f)
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF0A0A10),
                    checkedTrackColor = Color(0xFFD4AF37),
                    uncheckedThumbColor = Color(0xFF404050),
                    uncheckedTrackColor = Color(0xFF2A2A38)
                )
            )
        }
    }
}

// Diálogo para exibir conteúdo de Termos e Privacidade
@Composable
private fun ContentDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1A1A28)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD4AF37)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = text,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    lineHeight = 18.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD4AF37),
                        contentColor = Color(0xFF0A0A10)
                    )
                ) {
                    Text(
                        text = "Fechar",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
