from pathlib import Path
p = Path('android/app/src/main/java/com/streamflixvip/app/ui/reels/ReelsScreen.kt')
t = p.read_text()
if 'onLongClick' in t and 'q.isBlank' in t:
    print('reels ui ja patch')
else:
    if 'combinedClickable' not in t:
        t = t.replace(
            'import androidx.compose.foundation.clickable',
            'import androidx.compose.foundation.clickable\nimport androidx.compose.foundation.combinedClickable',
        )
        t = t.replace(
            'import androidx.compose.material3.TextButton',
            'import androidx.compose.material3.OutlinedTextField\nimport androidx.compose.material3.TextButton\nimport androidx.compose.material3.TextFieldDefaults',
        )
    old_col = '''        Text("Historias", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
        Row('''
    new_col = '''        Text("Historias", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
        var q by remember { mutableStateOf("") }
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            placeholder = { Text("Buscar titulo", color = Color(0xFF8B8BA8)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        Text("Segura o card para tirar de Continuar ou Favoritas.", color = Color(0xFF8B8BA8), fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
        Row('''
    if old_col in t:
        t = t.replace(old_col, new_col, 1)
    old_f = '''                val shown = s.stories.filter { story ->
                    when (filter) {
                        ReelsFilter.Todas -> true
                        ReelsFilter.Favoritas -> ReelLocalStore.isLiked(prefs, story.id)
                        ReelsFilter.Continuar -> ReelLocalStore.isInProgress(prefs, story.id)
                    }
                }'''
    new_f = '''                val shown = s.stories.filter { story ->
                    val okFilter = when (filter) {
                        ReelsFilter.Todas -> true
                        ReelsFilter.Favoritas -> ReelLocalStore.isLiked(prefs, story.id)
                        ReelsFilter.Continuar -> ReelLocalStore.isInProgress(prefs, story.id)
                    }
                    val okQ = q.isBlank() || (story.title ?: "").contains(q, ignoreCase = true)
                    okFilter && okQ
                }'''
    if old_f in t:
        t = t.replace(old_f, new_f, 1)
    old_card = '''                            StoryCard(
                                story = story,
                                liked = ReelLocalStore.isLiked(prefs, story.id),
                                watching = ReelLocalStore.isInProgress(prefs, story.id),
                                onClick = { onStoryClick(story) },
                            )'''
    new_card = '''                            StoryCard(
                                story = story,
                                liked = ReelLocalStore.isLiked(prefs, story.id),
                                watching = ReelLocalStore.isInProgress(prefs, story.id),
                                onClick = { onStoryClick(story) },
                                onLongClick = {
                                    when (filter) {
                                        ReelsFilter.Favoritas -> ReelLocalStore.setLiked(prefs, story.id, false)
                                        ReelsFilter.Continuar -> ReelLocalStore.clearProgress(prefs, story.id)
                                        ReelsFilter.Todas -> {
                                            if (ReelLocalStore.isLiked(prefs, story.id)) ReelLocalStore.setLiked(prefs, story.id, false)
                                            if (ReelLocalStore.isInProgress(prefs, story.id)) ReelLocalStore.clearProgress(prefs, story.id)
                                        }
                                    }
                                    tick++
                                },
                            )'''
    if old_card in t:
        t = t.replace(old_card, new_card, 1)
    t = t.replace(
        'private fun StoryCard(story: ReelStory, liked: Boolean, watching: Boolean, onClick: () -> Unit) {',
        'private fun StoryCard(story: ReelStory, liked: Boolean, watching: Boolean, onClick: () -> Unit, onLongClick: () -> Unit = {}) {',
        1,
    )
    t = t.replace(
        'Column(modifier = Modifier.clickable(onClick = onClick)) {',
        'Column(modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {',
        1,
    )
    p.write_text(t)
    print('reels ui patched')
