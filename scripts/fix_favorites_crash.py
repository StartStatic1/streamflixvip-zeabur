#!/usr/bin/env python3
from pathlib import Path

# MyListScreen
p = Path("android/app/src/main/java/com/streamflixvip/app/ui/mylist/MyListScreen.kt")
t = p.read_text()
if "LifecycleEventObserver" in t and "LifecycleEventEffect" not in t:
    print("MyList already fixed")
else:
    t = t.replace(
        "import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.collectAsState\nimport androidx.compose.runtime.getValue\nimport androidx.compose.ui.Alignment\nimport androidx.compose.ui.Modifier\nimport androidx.compose.ui.draw.clip\nimport androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.layout.ContentScale\nimport androidx.compose.ui.text.font.FontWeight\nimport androidx.compose.ui.text.style.TextAlign\nimport androidx.compose.ui.unit.dp\nimport androidx.compose.ui.unit.sp\nimport androidx.lifecycle.Lifecycle\nimport androidx.lifecycle.compose.LifecycleEventEffect\nimport coil.compose.AsyncImage\nimport com.streamflixvip.app.network.FavoriteEntry",
        "import androidx.activity.ComponentActivity\nimport androidx.compose.runtime.Composable\nimport androidx.compose.runtime.DisposableEffect\nimport androidx.compose.runtime.collectAsState\nimport androidx.compose.runtime.getValue\nimport androidx.compose.ui.Alignment\nimport androidx.compose.ui.Modifier\nimport androidx.compose.ui.draw.clip\nimport androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.layout.ContentScale\nimport androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.text.font.FontWeight\nimport androidx.compose.ui.text.style.TextAlign\nimport androidx.compose.ui.unit.dp\nimport androidx.compose.ui.unit.sp\nimport androidx.lifecycle.Lifecycle\nimport androidx.lifecycle.LifecycleEventObserver\nimport coil.compose.AsyncImage\nimport com.streamflixvip.app.network.FavoriteEntry",
    )
    old = """    // Recarrega a lista sempre que a tela volta a ficar visível — sem
    // isso, favoritar um título na tela de Detalhe e voltar pra cá não
    // atualizava a grade, porque o ViewModel desta aba é reaproveitado
    // (não recriado) ao trocar de aba pela bottom bar, então o load()
    // do init{} só rodava uma vez na vida do app.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.load()
    }"""
    new = """    // Recarrega a lista ao voltar pra tela.
    // Usa Lifecycle do Activity direto — evita crash
    // "LocalLifecycleOwner not present" em release/minify.
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val owner = context as ComponentActivity
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.load()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }"""
    if old not in t:
        raise SystemExit("MyList effect block not found")
    t = t.replace(old, new)
    p.write_text(t)
    print("MyList fixed", len(t))

# MainActivity
p2 = Path("android/app/src/main/java/com/streamflixvip/app/MainActivity.kt")
m = p2.read_text()
if "LocalLifecycleOwner provides this" in m:
    print("MainActivity already fixed")
else:
    if "import androidx.compose.runtime.CompositionLocalProvider" not in m:
        m = m.replace(
            "import androidx.compose.runtime.Composable\n",
            "import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.CompositionLocalProvider\n",
        )
    if "import androidx.lifecycle.compose.LocalLifecycleOwner" not in m:
        m = m.replace(
            "import androidx.lifecycle.viewmodel.compose.viewModel\n",
            "import androidx.lifecycle.compose.LocalLifecycleOwner\nimport androidx.lifecycle.viewmodel.compose.viewModel\n",
        )
    old_set = """        setContent {
            StreamFlixTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }"""
    new_set = """        setContent {
            // Garante LocalLifecycleOwner p/ lifecycle-compose (release/minify)
            CompositionLocalProvider(LocalLifecycleOwner provides this) {
                StreamFlixTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AppRoot()
                    }
                }
            }
        }"""
    if old_set not in m:
        raise SystemExit("MainActivity setContent not found")
    m = m.replace(old_set, new_set)
    p2.write_text(m)
    print("MainActivity fixed", len(m))
print("done")
