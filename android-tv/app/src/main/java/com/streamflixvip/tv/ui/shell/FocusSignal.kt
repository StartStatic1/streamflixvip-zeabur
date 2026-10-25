package com.streamflixvip.tv.ui.shell

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Pedido de foco do "casco" (menu lateral) para a secao aberta: quando o usuario
 * escolhe uma aba no menu, ou volta de outra tela, a secao recebe um token novo
 * e coloca o foco no item certo (o ultimo focado, ou o primeiro).
 */
@Stable
class FocusSignal {
    var token by mutableIntStateOf(1)
        private set

    fun request() {
        token++
    }
}
