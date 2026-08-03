package com.streamflixvip.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Cache em memória do status VIP do usuário logado.
 *
 * - Guarda também expiresAt (ISO) quando o servidor envia, para o relógio
 *   local cortar o VIP após a duração (ex.: código de 1h) mesmo se o app
 *   ficar aberto a sessão inteira.
 * - Na dúvida (sem expiresAt / antes da 1ª consulta): isVip = false.
 * - NÃO faz rede sozinho — quem atualiza é MainActivity / VipViewModel.
 */
object VipStatusHolder {
    private val _isVip = MutableStateFlow(false)
    val isVip: StateFlow<Boolean> = _isVip

    /** Epoch ms da expiração; 0 = desconhecido (usa só o flag do servidor). */
    @Volatile
    private var expiresAtMs: Long = 0L

    /**
     * Atualiza o cache. Preferir sempre passar [expiresAtIso] quando a API
     * devolver (vip-status / redeem). Se expiresAt for no passado, isVip
     * fica false mesmo que o flag venha true.
     */
    fun update(isVip: Boolean, expiresAtIso: String? = null) {
        expiresAtMs = parseIso(expiresAtIso) ?: 0L
        val effective = when {
            expiresAtMs > 0L -> expiresAtMs > System.currentTimeMillis()
            else -> isVip
        }
        _isVip.value = effective
    }

    /**
     * Reavalia só o relógio local (sem rede). Útil antes de liberar play /
     * remover cadeado — se o código de 1h já venceu com o app aberto,
     * bloqueia na hora.
     */
    fun refreshFromClock() {
        if (expiresAtMs > 0L) {
            val still = expiresAtMs > System.currentTimeMillis()
            if (_isVip.value != still) _isVip.value = still
        }
    }

    /** Consulta imediata com relógio local (não depende de recomposição). */
    fun isVipNow(): Boolean {
        refreshFromClock()
        return _isVip.value
    }

    private fun parseIso(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return runCatching { java.time.Instant.parse(iso.trim()).toEpochMilli() }.getOrNull()
    }
}
