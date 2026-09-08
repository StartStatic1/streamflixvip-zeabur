from pathlib import Path

p = Path('android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt')
if not p.exists():
    raise SystemExit('DetailScreen.kt nao achado')
t = p.read_text()
if 'DetailGenreAndCast(' in t:
    print('elenco ja ligado')
    raise SystemExit(0)

old = '''        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                details.overview?.let { overview ->
'''
new = '''        item {
            DetailGenreAndCast(
                genres = details.genres,
                cast = details.credits?.cast,
            )
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                details.overview?.let { overview ->
'''
if old not in t:
    raise SystemExit('bloco overview nao achado')
p.write_text(t.replace(old, new, 1))
print('elenco ligado', p.stat().st_size)
