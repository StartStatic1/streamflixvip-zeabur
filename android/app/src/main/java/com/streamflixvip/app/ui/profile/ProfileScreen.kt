package com.streamflixvip.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamflixvip.app.data.IptvStore
import com.streamflixvip.app.data.PreferencesStore
import com.streamflixvip.app.network.AnnouncementItem
import com.streamflixvip.app.network.NetworkModule
import com.streamflixvip.app.ui.auth.AuthViewModel
import androidx.compose.material.icons.filled.SettingsInputComponent
import com.streamflixvip.app.ui.vip.VipSection
import com.streamflixvip.app.ui.vip.VipViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TELEGRAM_URL = "https://t.me/streamflixofc"
private const val SUPPORT_EMAIL = "streamflixvip@outlook.com"
private val Accent = Color(0xFF00E5FF)

@Composable
fun ProfileScreen(
    authViewModel: AuthViewModel,
    userId: String?,
    userEmail: String?,
    onSignedOut: () -> Unit,
    onMyListClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val preferencesStore = remember { PreferencesStore(context) }
    val iptvStore = remember { IptvStore(context) }

    var notificationsEnabled by remember { mutableStateOf(preferencesStore.notificationsEnabled) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showIptvDialog by remember { mutableStateOf(false) }
    var isIptvActive by remember { mutableStateOf(iptvStore.hasCredentials) }
    var announcements by remember { mutableStateOf<List<AnnouncementItem>>(emptyList()) }

    // Carrega avisos só se o usuário quer notificações
    LaunchedEffect(notificationsEnabled) {
        if (!notificationsEnabled) {
            announcements = emptyList()
            return@LaunchedEffect
        }
        announcements = withContext(Dispatchers.IO) {
            runCatching {
                NetworkModule.announcementsApi.getAnnouncements().announcements
            }.getOrElse { emptyList() }
        }
    }

    val displayName = userEmail?.substringBefore("@") ?: "—"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF15151C)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(48.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFF001820), modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(displayName, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(userEmail ?: "—", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Accent.copy(alpha = 0.12f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text("Membro StreamFlixVIP", fontSize = 12.sp, color = Accent, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(24.dp))

        if (onMyListClick != null) {
            ProfileInfoCard(
                icon = Icons.Filled.Favorite,
                title = "Minha Lista",
                subtitle = "Filmes e séries que você salvou",
                iconTint = Accent,
                onClick = onMyListClick,
            )
            Spacer(Modifier.height(24.dp))
        }

        // Avisos (só com notificações ligadas)
        if (notificationsEnabled && announcements.isNotEmpty()) {
            SectionTitle("Avisos")
            announcements.forEach { item ->
                AnnouncementCard(item)
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(16.dp))
        }

        if (userId != null) {
            SectionTitle("Assinatura VIP")
            val vipViewModel: VipViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        return VipViewModel(userId) as T
                    }
                },
            )
            VipSection(viewModel = vipViewModel)
            Spacer(Modifier.height(28.dp))
        }

        SectionTitle("Contato Oficial")
        ProfileInfoCard(
            icon = Icons.Filled.Email,
            title = "E-mail de Suporte",
            subtitle = SUPPORT_EMAIL,
            iconTint = Color(0xFF29B6F6),
            onClick = { uriHandler.openUri("mailto:$SUPPORT_EMAIL") },
        )
        Spacer(Modifier.height(8.dp))
        ProfileInfoCard(
            icon = Icons.Filled.Send,
            title = "Telegram Oficial",
            subtitle = "@streamflixofc",
            iconTint = Color(0xFF29B6F6),
            onClick = { uriHandler.openUri(TELEGRAM_URL) },
        )

        Spacer(Modifier.height(28.dp))

        SectionTitle("Preferências")

        ProfileInfoCard(
            icon = Icons.Filled.SettingsInputComponent,
            title = "Leitor IPTV Nativo",
            subtitle = if (isIptvActive) "Ativado (${iptvStore.xtreamUser})" else "Configurar Host, Login e Senha",
            iconTint = if (isIptvActive) Accent else Color(0xFF4CAF50),
            onClick = { showIptvDialog = true },
        )

        Spacer(Modifier.height(8.dp))

        ProfileToggleCard(
            icon = Icons.Filled.Notifications,
            title = "Notificações / Avisos",
            subtitle = if (notificationsEnabled) "Avisos ativos no Perfil" else "Desativado — avisos ocultos",
            iconTint = Color(0xFFFF7043),
            checked = notificationsEnabled,
            onCheckedChange = {
                notificationsEnabled = it
                preferencesStore.notificationsEnabled = it
            },
        )

        Spacer(Modifier.height(28.dp))

        SectionTitle("Sobre")
        ProfileInfoCard(
            icon = Icons.Filled.Info,
            title = "Versão",
            subtitle = "7.4.0",
            iconTint = Color(0xFF78909C),
            onClick = {},
            showChevron = false,
        )
        Spacer(Modifier.height(8.dp))
        ProfileInfoCard(
            icon = Icons.Filled.Description,
            title = "Termos de Uso",
            subtitle = "Leia nossos termos de serviço",
            iconTint = Color(0xFF78909C),
            onClick = { showTermsDialog = true },
        )
        Spacer(Modifier.height(8.dp))
        ProfileInfoCard(
            icon = Icons.Filled.PrivacyTip,
            title = "Política de Privacidade",
            subtitle = "Como protegemos seus dados",
            iconTint = Color(0xFF78909C),
            onClick = { showPrivacyDialog = true },
        )

        Spacer(Modifier.height(28.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    authViewModel.signOut()
                    onSignedOut()
                },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sair da Conta", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "StreamFlixVIP © 2026",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
    }

    if (showTermsDialog) {
        InfoDialog(
            title = "Termos de Uso",
            body = "O StreamFlixVIP é um app de catálogo e organização de conteúdo. " +
                "O acesso VIP é pessoal e intransferível. Reservamo-nos o direito de " +
                "suspender contas em caso de uso indevido ou compartilhamento não autorizado " +
                "de credenciais. Dúvidas sobre o serviço podem ser tiradas pelo Telegram ou e-mail de suporte.",
            onDismiss = { showTermsDialog = false },
        )
    }
    if (showPrivacyDialog) {
        InfoDialog(
            title = "Política de Privacidade",
            body = "Guardamos apenas o essencial pra você usar o app: seu e-mail de login, " +
                "seu histórico de \"continuar assistindo\" e sua lista de favoritos — tudo " +
                "vinculado só à sua conta. Não vendemos nem compartilhamos esses dados com terceiros.",
            onDismiss = { showPrivacyDialog = false },
        )
    }

    if (showIptvDialog) {
        IptvLoginDialog(
            iptvStore = iptvStore,
            onDismiss = { showIptvDialog = false },
            onSuccess = { isIptvActive = iptvStore.hasCredentials }
        )
    }
}

@Composable
private fun AnnouncementCard(item: AnnouncementItem) {
    val typeColor = when (item.type.lowercase()) {
        "movie", "filme" -> Accent
        "maintenance", "manutencao", "manutenção" -> Color(0xFFFFB74D)
        "promo", "promoção", "promocao" -> Color(0xFF81C784)
        else -> Color(0xFF90CAF9)
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                item.typeLabel.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = typeColor,
            )
            Spacer(Modifier.height(4.dp))
            Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                item.body,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun ProfileInfoCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color,
    onClick: () -> Unit,
    showChevron: Boolean = true,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (showChevron) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color(0xFF001820)),
            )
        }
    }
}

@Composable
private fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(body, fontSize = 14.sp) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Entendi", color = Accent)
            }
        },
    )
}
