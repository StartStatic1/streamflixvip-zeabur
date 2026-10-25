package com.streamflixvip.app.ui.livetv

import com.streamflixvip.app.network.LiveChannel
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveTvUiStateTest {
    private val globo = LiveChannel(id = "globo", name = "Globo", categoryId = "abertos")
    private val espn = LiveChannel(id = "espn", name = "ESPN", categoryId = "esportes")
    private val sbt = LiveChannel(id = "sbt", name = "SBT", categoryId = "abertos")
    private val base = LiveTvUiState(
        isLoading = false,
        channels = listOf(espn, globo, sbt),
        tagsByChannel = mapOf("globo" to setOf("abertos"), "espn" to setOf("esportes"), "sbt" to setOf("abertos")),
        selectedCategoryId = "abertos",
    )

    @Test
    fun categoriaFiltraEBuscaProcuraEmTodos() {
        assertEquals(listOf("globo", "sbt"), base.filteredChannels.map { it.id })
        assertEquals(listOf("espn"), base.copy(searchQuery = "esp").filteredChannels.map { it.id })
    }

    @Test
    fun recentesNaOrdemEContagem() {
        val st = base.copy(tab = LiveTvTab.RECENT, recentIds = listOf("sbt", "sumiu", "espn"))
        assertEquals(listOf("sbt", "espn"), st.filteredChannels.map { it.id })
        assertEquals(2, base.countByCategory["abertos"])
        assertEquals(3, base.countByCategory["all"])
    }
}
