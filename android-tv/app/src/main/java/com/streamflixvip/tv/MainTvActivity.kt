package com.streamflixvip.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.streamflixvip.tv.ui.home.HomeTvScreen
import com.streamflixvip.tv.ui.theme.StreamFlixTvTheme

/**
 * Activity única do app de TV — assim como o app de celular usa uma
 * MainActivity + Navigation Compose (ver android/.../MainActivity.kt),
 * este app também roda tudo dentro de uma Activity só, delegando a
 * troca de tela pra Compose Navigation internamente conforme novas
 * telas forem adicionadas (Detail, Player, etc.).
 */
class MainTvActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StreamFlixTvTheme {
                HomeTvScreen()
            }
        }
    }
}
