#!/usr/bin/env python3
"""Troca links Telegram por WhatsApp (suporte + comunidade) com textos no nicho cinema."""
from pathlib import Path

PROFILE = Path("android/app/src/main/java/com/streamflixvip/app/ui/profile/ProfileScreen.kt")
DETAIL = Path("android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt")

WA_SUPPORT = "https://wa.me/558498334731"
WA_COMMUNITY = "https://chat.whatsapp.com/FAxyer3o2pe8x3JXZJBDDV"

# --- ProfileScreen ---
t = PROFILE.read_text()

t = t.replace(
    'private const val TELEGRAM_URL = "https://t.me/streamflixofc"',
    'private const val WHATSAPP_SUPPORT_URL = "' + WA_SUPPORT + '"\n'
    'private const val WHATSAPP_COMMUNITY_URL = "' + WA_COMMUNITY + '"',
)

# Import Chat icon if missing (for WhatsApp-like feel)
if "Icons.Filled.Chat" not in t and "import androidx.compose.material.icons.filled.Send" in t:
    t = t.replace(
        "import androidx.compose.material.icons.filled.Send",
        "import androidx.compose.material.icons.filled.Chat\n"
        "import androidx.compose.material.icons.filled.Groups\n"
        "import androidx.compose.material.icons.filled.Send",
    )

OLD_CONTACT = '''        SectionTitle("Contato Oficial")
        ProfileInfoCard(
            icon = Icons.Filled.Email,
            title = "E-mail de Suporte",
            subtitle = SUPPORT_EMAIL,
            iconTint = Color(0xFF29B6F6),
            onClick = { uriHandler.openUri("mailto:$SUPPORT_EMAIL") },
        )
        Spacer(modifier = Modifier.height(8.dp))
        ProfileInfoCard(
            icon = Icons.Filled.Send,
            title = "Telegram Oficial",
            subtitle = "@streamflixofc",
            iconTint = Color(0xFF29B6F6),
            onClick = { uriHandler.openUri(TELEGRAM_URL) },
        )'''

NEW_CONTACT = '''        SectionTitle("Central de Atendimento")
        ProfileInfoCard(
            icon = Icons.Filled.Chat,
            title = "Suporte VIP · WhatsApp",
            subtitle = "Atendimento rápido · conta, pagamento e pedidos",
            iconTint = Color(0xFF25D366),
            onClick = { uriHandler.openUri(WHATSAPP_SUPPORT_URL) },
        )
        Spacer(modifier = Modifier.height(8.dp))
        ProfileInfoCard(
            icon = Icons.Filled.Groups,
            title = "Comunidade StreamFlix",
            subtitle = "Novidades, bastidores e pedidos de filmes",
            iconTint = Color(0xFF25D366),
            onClick = { uriHandler.openUri(WHATSAPP_COMMUNITY_URL) },
        )
        Spacer(modifier = Modifier.height(8.dp))
        ProfileInfoCard(
            icon = Icons.Filled.Email,
            title = "E-mail de Suporte",
            subtitle = SUPPORT_EMAIL,
            iconTint = Color(0xFF29B6F6),
            onClick = { uriHandler.openUri("mailto:$SUPPORT_EMAIL") },
        )'''

if OLD_CONTACT not in t:
    if "WHATSAPP_SUPPORT_URL" in t and "Comunidade StreamFlix" in t:
        print("profile already updated")
    else:
        raise SystemExit("Profile contact block not found")
else:
    t = t.replace(OLD_CONTACT, NEW_CONTACT, 1)
    print("profile contact OK")

t = t.replace(
    "Dúvidas sobre o serviço podem ser tiradas pelo Telegram ou e-mail de suporte.",
    "Dúvidas sobre o serviço podem ser tiradas pelo WhatsApp de suporte, na comunidade ou por e-mail.",
)

PROFILE.write_text(t)
print("ProfileScreen written")

# --- DetailScreen MovieRequestCard ---
if DETAIL.exists():
    d = DETAIL.read_text()
    OLD_CARD = '''@Composable
private fun MovieRequestCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val telegramUrl = "https://t.me/streamflixofc/7335"
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Este filme ainda não está no catálogo", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("Quer assistir? Peça no nosso canal e a equipe analisa a inclusão o mais rápido possível.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
            Spacer(Modifier.height(14.dp))
            Button(onClick = { try { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(telegramUrl))) } catch (_: Exception) { } }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Pedir este filme no Telegram", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}'''

    NEW_CARD = '''@Composable
private fun MovieRequestCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Comunidade WhatsApp — pedidos de catálogo e novidades
    val requestUrl = "''' + WA_COMMUNITY + '''"
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Ainda não está na grade", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Quer esse título no catálogo? Manda o pedido na comunidade — a equipe analisa e coloca na programação o mais rápido possível.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    try {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(requestUrl),
                            ),
                        )
                    } catch (_: Exception) { }
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
            ) {
                Text("Pedir este filme no WhatsApp", fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}'''

    if OLD_CARD not in d:
        if "Pedir este filme no WhatsApp" in d:
            print("detail already updated")
        else:
            # fallback: simple replaces
            d2 = d.replace("https://t.me/streamflixofc/7335", WA_COMMUNITY)
            d2 = d2.replace("Pedir este filme no Telegram", "Pedir este filme no WhatsApp")
            d2 = d2.replace(
                "Quer assistir? Peça no nosso canal e a equipe analisa a inclusão o mais rápido possível.",
                "Quer esse título no catálogo? Manda o pedido na comunidade — a equipe analisa e coloca na programação o mais rápido possível.",
            )
            d2 = d2.replace("Este filme ainda não está no catálogo", "Ainda não está na grade")
            if d2 != d:
                DETAIL.write_text(d2)
                print("detail fallback OK")
            else:
                raise SystemExit("MovieRequestCard not found")
    else:
        d = d.replace(OLD_CARD, NEW_CARD, 1)
        DETAIL.write_text(d)
        print("detail card OK")
else:
    print("DetailScreen missing")

print("done")
