package com.streamflixvip.tv.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton de estado global VIP — alimentação via tela de Perfil
 * e consumo por Home/Detail/Player.
 */
object VipStatusHolder {

    private val _isVip = MutableStateFlow(false)
    val isVip: StateFlow<Boolean> = _isVip.asStateFlow()

    fun update(isVip: Boolean) {
        _isVip.value = isVip
    }
}
