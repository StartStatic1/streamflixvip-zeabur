package com.streamflixvip.app.ui.mylist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Placeholder — persistência de lista (favoritos/continuar assistindo)
 * é uma feature própria com escopo próprio (precisa de tabela no
 * Supabase + sync entre dispositivos), fica pra uma rodada dedicada.
 */
@Composable
fun MyListScreen() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            "Sua lista aparecerá aqui.\nEm breve: favoritos e continuar assistindo.",
            textAlign = TextAlign.Center,
        )
    }
}
