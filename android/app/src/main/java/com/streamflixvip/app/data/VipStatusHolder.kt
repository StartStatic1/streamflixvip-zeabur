package com.streamflixvip.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Guarda em memória o último status VIP conhecido do usuário atual, pra
 * qualquer tela (Detail, Player, Home) consultar "esse usuário é VIP?"
 * sem precisar disparar uma nova chamada de rede cada vez — a tela de
 * Perfil já busca isso ao abrir o app; aqui só compartilhamos o
 * resultado. Segue o mesmo padrão de objeto singleton simples que
 * NetworkModule já usa (sem DI framework nesta fase do projeto).
 *
 * Importante: por ser só um cache em memória, começa como `false` até a
 * primeira consulta real completar — então qualquer checagem de bloqueio
 * feita ANTES do Perfil carregar assume "não-VIP" (comportamento seguro:
 * na dúvida, mostra o cadeado, nunca libera de graça por engano).
 */
object VipStatusHolder {
    private val _isVip = MutableStateFlow(false)
    val isVip: StateFlow<Boolean> = _isVip

    fun update(isVip: Boolean) {
        _isVip.value = isVip
    }
}
