from pathlib import Path

p = Path('android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt')
if not p.exists():
    raise SystemExit('DetailScreen.kt nao achado')
t = p.read_text()

def once(old, new, label):
    global t
    if old not in t:
        if new.strip() in t or label in ('elenco',):
            print('ok', label)
            return
        raise SystemExit('faltou bloco: ' + label)
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

once(
'''                year?.let { MetaChip(it) }
                runtimeLabel?.let { MetaChip(it) }
                rating?.let { MetaChip("\u2b50 ${\"%.1f\".format(it)}") }
            }''',
'''                year?.let { MetaChip(it) }
                runtimeLabel?.let { MetaChip(it) }
                rating?.let { MetaChip("\u2b50 ${\"%.1f\".format(it)}") }
            }
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
            }''',
'chips no hero')

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
