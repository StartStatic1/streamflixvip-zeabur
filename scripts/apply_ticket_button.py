#!/usr/bin/env python3
"""Botao Ingresso R$2,50 na ficha (VipLockCard) + PixRequest type/tmdbId."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]

def patch_vip_api():
    p = ROOT / "android/app/src/main/java/com/streamflixvip/app/network/VipApi.kt"
    t = p.read_text(encoding="utf-8")
    old = '''data class PixRequest(\n    val userId: String,\n    val amount: Double,\n    val planLabel: String,\n    val durationHours: Int\n)'''
    new = '''data class PixRequest(\n    val userId: String,\n    val amount: Double,\n    val planLabel: String,\n    val durationHours: Int,\n    val type: String? = null,\n    val tmdbId: String? = null,\n    val mediaType: String? = null,\n)'''
    if "val type: String?" in t and "tmdbId" in t:
        print("VipApi already has type/tmdbId")
        return
    if old not in t:
        print("WARN PixRequest shape not found")
        return
    p.write_text(t.replace(old, new, 1), encoding="utf-8")
    print("ok VipApi PixRequest")

def patch_pix_sheet():
    p = ROOT / "android/app/src/main/java/com/streamflixvip/app/ui/vip/PixPaymentSheet.kt"
    t = p.read_text(encoding="utf-8")
    if "tmdbId: String? = null" in t and "type: String? = null" in t:
        print("PixPaymentSheet already ticket-aware")
        return
    old_sig = '''fun PixPaymentSheet(\n    userId: String,\n    amount: Double,\n    planLabel: String,\n    durationHours: Int,\n    onDismiss: () -> Unit,\n)'''
    new_sig = '''fun PixPaymentSheet(\n    userId: String,\n    amount: Double,\n    planLabel: String,\n    durationHours: Int,\n    onDismiss: () -> Unit,\n    type: String? = null,\n    tmdbId: String? = null,\n    mediaType: String? = null,\n)'''
    if old_sig not in t:
        print("WARN PixPaymentSheet sig not found")
        return
    t = t.replace(old_sig, new_sig, 1)
    old_req = '''                    PixRequest(\n                        userId = userId,\n                        amount = amount,\n                        planLabel = planLabel,\n                        durationHours = durationHours,\n                    )'''
    new_req = '''                    PixRequest(\n                        userId = userId,\n                        amount = amount,\n                        planLabel = planLabel,\n                        durationHours = durationHours,\n                        type = type,\n                        tmdbId = tmdbId,\n                        mediaType = mediaType,\n                    )'''
    if old_req in t:
        t = t.replace(old_req, new_req, 1)
        print("ok PixPaymentSheet request")
    else:
        print("WARN PixRequest call site not found")
    p.write_text(t, encoding="utf-8")

def patch_detail():
    p = ROOT / "android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt"
    t = p.read_text(encoding="utf-8")
    if "onTicketClick" in t and "Ingresso" in t:
        print("DetailScreen already has Ingresso")
        return

    # import PixPaymentSheet
    if "PixPaymentSheet" not in t:
        needle = "import com.streamflixvip.app.network.TmdbImages"
        if needle in t:
            t = t.replace(
                needle,
                needle + "\nimport com.streamflixvip.app.ui.vip.PixPaymentSheet",
                1,
            )
            print("ok import PixPaymentSheet")

    # DetailScreen params: userId
    old_sig = '''fun DetailScreen(\n    viewModel: DetailViewModel,\n    resumeSeconds: Int = 0,\n    initialSeason: Int = -1,\n    initialEpisode: Int = -1,\n    onPlaySource: (source: VipSource, season: Int, episode: Int, title: String, posterPath: String?) -> Unit,\n    onBack: () -> Unit,\n    onUpgradeClick: () -> Unit,\n    onOpenTitle: (tmdbId: Int, mediaType: String) -> Unit,\n) {'''
    new_sig = '''fun DetailScreen(\n    viewModel: DetailViewModel,\n    resumeSeconds: Int = 0,\n    initialSeason: Int = -1,\n    initialEpisode: Int = -1,\n    onPlaySource: (source: VipSource, season: Int, episode: Int, title: String, posterPath: String?) -> Unit,\n    onBack: () -> Unit,\n    onUpgradeClick: () -> Unit,\n    onOpenTitle: (tmdbId: Int, mediaType: String) -> Unit,\n    userId: String? = null,\n) {'''
    if old_sig in t:
        t = t.replace(old_sig, new_sig, 1)
        print("ok DetailScreen userId param")
    elif "userId: String? = null" in t:
        print("DetailScreen userId already")
    else:
        print("WARN DetailScreen sig")

    # state + sheet after isVip collect
    if "showTicketPay" not in t:
        anchor = "    val isVip by com.streamflixvip.app.data.VipStatusHolder.isVip.collectAsState()"
        inject = '''    val isVip by com.streamflixvip.app.data.VipStatusHolder.isVip.collectAsState()
    var showTicketPay by remember { mutableStateOf(false) }
    if (showTicketPay && !userId.isNullOrBlank()) {
        PixPaymentSheet(
            userId = userId,
            amount = 2.50,
            planLabel = "Ingresso 24h",
            durationHours = 24,
            onDismiss = { showTicketPay = false },
            type = "ticket",
            tmdbId = viewModel.tmdbIdForTicket(),
            mediaType = viewModel.mediaTypeForTicket(),
        )
    }'''
        if anchor in t:
            t = t.replace(anchor, inject, 1)
            print("ok showTicketPay sheet")
        else:
            print("WARN isVip anchor")

    # VipLockCard call
    old_call = "VipLockCard(onUpgradeClick = onUpgradeClick)"
    new_call = "VipLockCard(onUpgradeClick = onUpgradeClick, onTicketClick = { if (!userId.isNullOrBlank()) showTicketPay = true })"
    if old_call in t:
        t = t.replace(old_call, new_call)
        print("ok VipLockCard calls")

    # VipLockCard composable
    old_card = '''@Composable
private fun VipLockCard(onUpgradeClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("\U0001f512", fontSize = 28.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Conte\u00fado exclusivo VIP",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Assine o VIP para desbloquear este t\u00edtulo e assistir sem espera.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = onUpgradeClick, modifier = Modifier.fillMaxWidth()) {
                Text("Seja VIP agora")
            }
        }
    }
}'''
    # use actual file content - may have special chars
    if "onTicketClick" not in t:
        # simpler replace of button section
        old_btn = '''            Spacer(Modifier.height(14.dp))
            Button(onClick = onUpgradeClick, modifier = Modifier.fillMaxWidth()) {
                Text("Seja VIP agora")
            }
        }
    }
}

/** Extrai um selo'''
        new_btn = '''            Spacer(Modifier.height(14.dp))
            Button(onClick = onUpgradeClick, modifier = Modifier.fillMaxWidth()) {
                Text("Seja VIP agora")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onTicketClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Ingresso R$ 2,50 \u00b7 24h s\u00f3 este t\u00edtulo")
            }
        }
    }
}

/** Extrai um selo'''
        if old_btn in t:
            t = t.replace(old_btn, new_btn, 1)
            t = t.replace(
                "private fun VipLockCard(onUpgradeClick: () -> Unit)",
                "private fun VipLockCard(onUpgradeClick: () -> Unit, onTicketClick: () -> Unit = {})",
                1,
            )
            print("ok VipLockCard buttons")
        else:
            print("WARN VipLockCard button block")

    p.write_text(t, encoding="utf-8")

def patch_viewmodel():
    p = ROOT / "android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailViewModel.kt"
    t = p.read_text(encoding="utf-8")
    if "tmdbIdForTicket" in t:
        print("ViewModel already")
        return
    # add helpers at end of class - find last closing of class is hard; append methods before last lines
    # inject after class constructor fields
    if "fun tmdbIdForTicket" in t:
        return
    # Add after class opening params block
    needle = "private val userId: String? = null,"
    # Find mediaType field
    if "private val mediaType" in t and "fun tmdbIdForTicket" not in t:
        # append near end before last }
        # simple: after first function
        insert = '''
    fun tmdbIdForTicket(): String = tmdbId.toString()
    fun mediaTypeForTicket(): String = mediaType
'''
        # find "init {" and insert before it
        if "\n    init {" in t:
            t = t.replace("\n    init {", insert + "\n    init {", 1)
            p.write_text(t, encoding="utf-8")
            print("ok ViewModel ticket helpers")
        else:
            print("WARN ViewModel init")

def patch_main():
    p = ROOT / "android/app/src/main/java/com/streamflixvip/app/MainActivity.kt"
    t = p.read_text(encoding="utf-8")
    if "userId = userId," in t and "DetailScreen(" in t:
        # check if already passes userId to DetailScreen
        if "DetailScreen(" in t and "userId = userId" in t[t.find("DetailScreen("):t.find("DetailScreen(")+800]:
            print("MainActivity DetailScreen userId maybe ok")
        # force pass
        old = '''                DetailScreen(\n                    viewModel = viewModel,\n                    resumeSeconds = resumeSeconds,\n                    initialSeason = initialSeason,\n                    initialEpisode = initialEpisode,\n                    onPlaySource = { source, season, episode, title, posterPath ->'''
        new = '''                DetailScreen(\n                    viewModel = viewModel,\n                    resumeSeconds = resumeSeconds,\n                    initialSeason = initialSeason,\n                    initialEpisode = initialEpisode,\n                    userId = userId,\n                    onPlaySource = { source, season, episode, title, posterPath ->'''
        if old in t and "userId = userId," not in t[t.find("DetailScreen("):t.find("DetailScreen(")+400]:
            t = t.replace(old, new, 1)
            p.write_text(t, encoding="utf-8")
            print("ok MainActivity userId")
        elif "userId = userId" in t[t.find("DetailScreen("):t.find("DetailScreen(")+500]:
            print("MainActivity already passes userId")
        else:
            # try looser
            if "userId = userId," not in t[t.find("DetailScreen"):t.find("DetailScreen")+600]:
                t2 = t.replace(
                    "DetailScreen(\n                    viewModel = viewModel,",
                    "DetailScreen(\n                    viewModel = viewModel,\n                    userId = userId,",
                    1,
                )
                if t2 != t:
                    p.write_text(t2, encoding="utf-8")
                    print("ok MainActivity userId loose")
                else:
                    print("WARN MainActivity")
            else:
                print("MainActivity ok")
    else:
        print("WARN MainActivity structure")

if __name__ == "__main__":
    patch_vip_api()
    patch_pix_sheet()
    patch_viewmodel()
    patch_detail()
    patch_main()
    print("fim apply_ticket_button")
