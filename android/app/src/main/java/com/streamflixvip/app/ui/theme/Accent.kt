package com.streamflixvip.app.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Cor de destaque da marca (Perfil > Aparência). Mesmas opções da TV. */
enum class AccentTheme(val label: String, val accent: Color, val accent2: Color, val onAccent: Color) {
    RUBI("Rubi", Color(0xFFE11D48), Color(0xFFFB7185), Color.White),
    AURORA("Aurora", Color(0xFF22D3EE), Color(0xFF6366F1), Color(0xFF041016)),
    OURO("Ouro", Color(0xFFFBBF24), Color(0xFFF97316), Color(0xFF1A1000)),
    VIOLETA("Violeta", Color(0xFFA78BFA), Color(0xFFEC4899), Color(0xFF12052A)),
    ESMERALDA("Esmeralda", Color(0xFF34D399), Color(0xFF22D3EE), Color(0xFF03140D)),
    CLASSICO("Clássico", Color(0xFFFFFFFF), Color(0xFFE5E5E5), Color(0xFF0A0A0A)),
}

object AccentStore {
    private const val PREFS = "appearance"
    private const val KEY = "accent"
    private var ctx: Context? = null
    private val _state = MutableStateFlow(AccentTheme.RUBI)
    val state: StateFlow<AccentTheme> = _state
    val current: AccentTheme get() = _state.value

    fun init(context: Context) {
        ctx = context.applicationContext
        val saved = runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        }.getOrNull()
        _state.value = AccentTheme.entries.firstOrNull { it.name == saved } ?: AccentTheme.RUBI
    }

    fun set(theme: AccentTheme) {
        _state.value = theme
        runCatching {
            ctx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString(KEY, theme.name)?.apply()
        }
    }
}
