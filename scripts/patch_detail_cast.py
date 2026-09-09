from pathlib import Path

p = Path('android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt')
if not p.exists():
    raise SystemExit('DetailScreen.kt nao achado')
t = p.read_text()

def once(old, new, label):
    global t
    if new.strip() in t:
        print('ok', label)
        return
    if old not in t:
        print('aviso: faltou bloco', label)
        return
    t = t.replace(old, new, 1)
    print('aplicou', label)

once(
'''        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                details.overview?.let { overview ->
''',
'''        item {
            DetailGenreAndCast(
                cast = details.credits?.cast,
                crew = details.credits?.crew,
                onPersonClick = onPersonClick,
            )
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                details.overview?.let { overview ->
''',
'elenco')

t = t.replace(
'''            DetailGenreAndCast(
                genres = details.genres,
                cast = details.credits?.cast,
            )''',
'''            DetailGenreAndCast(
                cast = details.credits?.cast,
                crew = details.credits?.crew,
                onPersonClick = onPersonClick,
            )'''
)

once(
'''    onUpgradeClick: () -> Unit,
    onOpenTitle: (tmdbId: Int, mediaType: String) -> Unit,
) {''',
'''    onUpgradeClick: () -> Unit,
    onOpenTitle: (tmdbId: Int, mediaType: String) -> Unit,
    onPersonClick: (Int) -> Unit = {},
) {''',
'param DetailScreen')

once(
'''    onPostComment: (text: String, onResult: (Boolean) -> Unit) -> Unit,
    onToggleFavorite: () -> Unit,
    skipHeroLoading: Boolean = false,
) {''',
'''    onPostComment: (text: String, onResult: (Boolean) -> Unit) -> Unit,
    onToggleFavorite: () -> Unit,
    onPersonClick: (Int) -> Unit = {},
    skipHeroLoading: Boolean = false,
) {''',
'param DetailContent')

once(
'''    year: String?,
    runtimeLabel: String?,
    isFavorite: Boolean,''',
'''    year: String?,
    runtimeLabel: String?,
    genreNames: List<String> = emptyList(),
    isFavorite: Boolean,''',
'param header')

once(
'''                year = (details.release_date ?: details.first_air_date)?.take(4),
                runtimeLabel = details.displayRuntime,
                isFavorite = state.isFavorite,''',
'''                year = (details.release_date ?: details.first_air_date)?.take(4),
                runtimeLabel = details.displayRuntime,
                genreNames = details.genres.orEmpty().mapNotNull { it.name.takeIf { n -> n.isNotBlank() } }.take(4),
                isFavorite = state.isFavorite,''',
'passa generos')

star_line = '                rating?.let { MetaChip("\u2b50 ${\"%.1f\".format(it)}") }'
# match real source star chip without relying on escaped star
import re
pat = r'([ \t]*year\?\.let \{ MetaChip\(it\) \}\n[ \t]*runtimeLabel\?\.let \{ MetaChip\(it\) \}\n[ \t]*rating\?\.let \{ MetaChip\(".*"\) \}\n[ \t]*\})'
mchip = re.search(pat, t)
if mchip and 'genreNames.isNotEmpty()' not in t:
    chips = mchip.group(0) + '''
            if (genreNames.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    genreNames.forEach { name ->
                        Text(
                            text = name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StreamFlixColors.Amber,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(StreamFlixColors.SurfaceHigh)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }'''
    t = t[:mchip.start()] + chips + t[mchip.end():]
    print('aplicou chips no hero')
elif 'genreNames.isNotEmpty()' in t:
    print('ok chips no hero')
else:
    print('aviso: chips no hero nao aplicados')

old_pass = '''                onToggleFavorite = viewModel::toggleFavorite,
            )

            if (showMovieServerPicker) {'''
new_pass = '''                onToggleFavorite = viewModel::toggleFavorite,
                onPersonClick = onPersonClick,
            )

            if (showMovieServerPicker) {'''
if old_pass in t:
    t = t.replace(old_pass, new_pass, 1)
    print('aplicou passa clique')

if 'import com.streamflixvip.app.ui.theme.StreamFlixColors' not in t:
    t = t.replace(
        'import com.streamflixvip.app.ads.AdsHelper\n',
        'import com.streamflixvip.app.ads.AdsHelper\nimport com.streamflixvip.app.ui.theme.StreamFlixColors\n',
    )

p.write_text(t)
print('pronto', p.stat().st_size)

m = Path('android/app/src/main/java/com/streamflixvip/app/MainActivity.kt')
if m.exists():
    mt = m.read_text()
    if 'import com.streamflixvip.app.ui.person.PersonScreen' not in mt:
        mt = mt.replace(
            'import com.streamflixvip.app.ui.detail.DetailViewModel\n',
            'import com.streamflixvip.app.ui.detail.DetailViewModel\nimport com.streamflixvip.app.ui.person.PersonScreen\nimport com.streamflixvip.app.ui.person.PersonViewModel\n',
        )
    needle = (
        '                    onOpenTitle = { openTmdbId, openMediaType ->\n'
        '                        navController.navigate("detail/$openTmdbId/$openMediaType")\n'
        '                    },\n'
        '                )\n'
        '            }\n\n'
        '            composable(\n'
        '                route = "player/'
    )
    insert = (
        '                    onOpenTitle = { openTmdbId, openMediaType ->\n'
        '                        navController.navigate("detail/$openTmdbId/$openMediaType")\n'
        '                    },\n'
        '                    onPersonClick = { personId ->\n'
        '                        navController.navigate("person/$personId")\n'
        '                    },\n'
        '                )\n'
        '            }\n\n'
        '            composable(\n'
        '                route = "person/{personId}",\n'
        '                arguments = listOf(\n'
        '                    navArgument("personId") { type = NavType.IntType },\n'
        '                ),\n'
        '            ) { entry ->\n'
        '                val personId = entry.arguments?.getInt("personId") ?: return@composable\n'
        '                val personVm: PersonViewModel = viewModel(\n'
        '                    factory = viewModelFactory { PersonViewModel(personId) },\n'
        '                )\n'
        '                PersonScreen(\n'
        '                    viewModel = personVm,\n'
        '                    onBack = { navController.popBackStack() },\n'
        '                    onOpenTitle = { openTmdbId, openMediaType ->\n'
        '                        navController.navigate("detail/$openTmdbId/$openMediaType")\n'
        '                    },\n'
        '                )\n'
        '            }\n\n'
        '            composable(\n'
        '                route = "player/'
    )
    if needle in mt:
        mt = mt.replace(needle, insert, 1)
        print('aplicou rota person')
    elif 'route = "person/{personId}"' in mt:
        print('ok rota person')
    else:
        print('aviso: rota person nao aplicada')
    m.write_text(mt)
