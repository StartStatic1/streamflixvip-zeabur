package com.streamflixvip.app.ui.explore

/**
 * Canal simples em memória (não é estado persistido, não sobrevive a
 * process death) pra levar um filtro pré-selecionado da Home até a aba
 * Explorar, sem precisar colocar categoria/gênero/ano na própria string
 * de rota — o que quebraria a comparação exata de string que a bottom
 * bar usa pra saber qual aba está selecionada.
 *
 * Fluxo: Home chama `set(filters)` antes de navegar pra "explore"; a
 * tela Explorar chama `consume()` ao montar o ViewModel, que lê o valor
 * e já limpa em seguida — assim, reabrir a aba Explorar depois (pela
 * bottom bar, sem vir de um "Ver mais") não fica presa ao último filtro usado.
 */
object PendingExploreFilter {
    private var pending: ExploreFilters? = null

    fun set(filters: ExploreFilters) {
        pending = filters
    }

    fun consume(): ExploreFilters? {
        val value = pending
        pending = null
        return value
    }
}
