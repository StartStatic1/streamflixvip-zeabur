#!/usr/bin/env python3
"""Liga elenco na ficha + onPersonClick (compativel com ingresso/ticket)."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
p = ROOT / "android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt"
if not p.exists():
    raise SystemExit("DetailScreen.kt nao achado")
t = p.read_text(encoding="utf-8")

def once(old, new, label):
    global t
    if new.strip() in t:
        print("ok", label)
        return True
    if old not in t:
        print("aviso: faltou bloco", label)
        return False
    t = t.replace(old, new, 1)
    print("aplicou", label)
    return True

# --- elenco antes da sinopse ---
once(
    """        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                details.overview?.let { overview ->
""",
    """        item {
            DetailGenreAndCast(
                cast = details.credits?.cast,
                crew = details.credits?.crew,
                onPersonClick = onPersonClick,
            )
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                details.overview?.let { overview ->
""",
    "elenco",
)

# legado
t = t.replace(
    """            DetailGenreAndCast(
                genres = details.genres,
                cast = details.credits?.cast,
            )""",
    """            DetailGenreAndCast(
                cast = details.credits?.cast,
                crew = details.credits?.crew,
                onPersonClick = onPersonClick,
            )""",
)

# --- param DetailScreen (com userId do ingresso) ---
if "onPersonClick: (Int) -> Unit" not in t[t.find("fun DetailScreen"):t.find("fun DetailScreen") + 900]:
    once(
        """    onOpenTitle: (tmdbId: Int, mediaType: String) -> Unit,
    userId: String? = null,
) {""",
        """    onOpenTitle: (tmdbId: Int, mediaType: String) -> Unit,
    userId: String? = null,
    onPersonClick: (Int) -> Unit = {},
) {""",
        "param DetailScreen+userId",
    ) or once(
        """    onOpenTitle: (tmdbId: Int, mediaType: String) -> Unit,
) {""",
        """    onOpenTitle: (tmdbId: Int, mediaType: String) -> Unit,
    onPersonClick: (Int) -> Unit = {},
) {""",
        "param DetailScreen",
    )
else:
    print("ok param DetailScreen")

# --- param DetailContent (com onTicketClick) ---
idx_dc = t.find("private fun DetailContent")
chunk = t[idx_dc : idx_dc + 1200] if idx_dc >= 0 else ""
if "onPersonClick: (Int) -> Unit" not in chunk:
    once(
        """    onToggleFavorite: () -> Unit,
    onTicketClick: () -> Unit = {},
    skipHeroLoading: Boolean = false,
) {""",
        """    onToggleFavorite: () -> Unit,
    onTicketClick: () -> Unit = {},
    onPersonClick: (Int) -> Unit = {},
    skipHeroLoading: Boolean = false,
) {""",
        "param DetailContent+ticket",
    ) or once(
        """    onToggleFavorite: () -> Unit,
    skipHeroLoading: Boolean = false,
) {""",
        """    onToggleFavorite: () -> Unit,
    onPersonClick: (Int) -> Unit = {},
    skipHeroLoading: Boolean = false,
) {""",
        "param DetailContent",
    )
else:
    print("ok param DetailContent")

# --- passar onPersonClick na chamada DetailContent ---
if "onPersonClick = onPersonClick" not in t[t.find("DetailContent(") : t.find("DetailContent(") + 1200]:
    once(
        """                onTicketClick = {
                    if (!userId.isNullOrBlank()) showTicketPay = true
                },
            )""",
        """                onTicketClick = {
                    if (!userId.isNullOrBlank()) showTicketPay = true
                },
                onPersonClick = onPersonClick,
            )""",
        "call DetailContent+ticket",
    ) or once(
        """                onToggleFavorite = viewModel::toggleFavorite,
            )""",
        """                onToggleFavorite = viewModel::toggleFavorite,
                onPersonClick = onPersonClick,
            )""",
        "call DetailContent",
    )
else:
    print("ok call DetailContent")

p.write_text(t, encoding="utf-8")
print("DetailScreen escrito")

# --- MainActivity: onPersonClick + rota person ---
m = ROOT / "android/app/src/main/java/com/streamflixvip/app/MainActivity.kt"
if m.exists():
    mt = m.read_text(encoding="utf-8")
    if "PersonScreen" not in mt:
        mt = mt.replace(
            "import com.streamflixvip.app.ui.detail.DetailViewModel\n",
            "import com.streamflixvip.app.ui.detail.DetailViewModel\n"
            "import com.streamflixvip.app.ui.person.PersonScreen\n"
            "import com.streamflixvip.app.ui.person.PersonViewModel\n",
        )
        print("import Person*")

    # callback no DetailScreen
    if "onPersonClick = { personId" not in mt:
        old = (
            "                    onOpenTitle = { openTmdbId, openMediaType ->\n"
            "                        navController.navigate(\"detail/$openTmdbId/$openMediaType\")\n"
            "                    },\n"
            "                )\n"
        )
        new = (
            "                    onOpenTitle = { openTmdbId, openMediaType ->\n"
            "                        navController.navigate(\"detail/$openTmdbId/$openMediaType\")\n"
            "                    },\n"
            "                    onPersonClick = { personId ->\n"
            "                        navController.navigate(\"person/$personId\")\n"
            "                    },\n"
            "                )\n"
        )
        if old in mt:
            mt = mt.replace(old, new, 1)
            print("aplicou onPersonClick no DetailScreen call")
        else:
            print("aviso: call DetailScreen person")

    if 'route = "person/{personId}"' not in mt:
        needle = (
            "            composable(\n"
            "                route = \"player/"
        )
        insert = (
            "            composable(\n"
            "                route = \"person/{personId}\",\n"
            "                arguments = listOf(\n"
            "                    navArgument(\"personId\") { type = NavType.IntType },\n"
            "                ),\n"
            "            ) { entry ->\n"
            "                val personId = entry.arguments?.getInt(\"personId\") ?: return@composable\n"
            "                val personVm: PersonViewModel = viewModel(\n"
            "                    factory = viewModelFactory { PersonViewModel(personId) },\n"
            "                )\n"
            "                PersonScreen(\n"
            "                    viewModel = personVm,\n"
            "                    onBack = { navController.popBackStack() },\n"
            "                    onOpenTitle = { openTmdbId, openMediaType ->\n"
            "                        navController.navigate(\"detail/$openTmdbId/$openMediaType\")\n"
            "                    },\n"
            "                )\n"
            "            }\n\n"
            "            composable(\n"
            "                route = \"player/"
        )
        if needle in mt:
            mt = mt.replace(needle, insert, 1)
            print("aplicou rota person")
        else:
            print("aviso: rota person")
    else:
        print("ok rota person")

    m.write_text(mt, encoding="utf-8")

# PersonScreen existe?
ps = list((ROOT / "android/app/src/main/java/com/streamflixvip/app/ui").rglob("Person*.kt"))
print("Person files:", [str(x.relative_to(ROOT)) for x in ps])
if not ps:
    print("AVISO: PersonScreen.kt nao existe — onPersonClick vira no-op no default")

print("done patch_detail_cast")
