package com.unbpocket.app.ui.turmas

import com.unbpocket.core.model.turmas.AtualizacaoTurmaFeed
import org.junit.Assert.assertEquals
import org.junit.Test

class TurmasFeedKeyTest {
    @Test
    fun `turmaFeedItemKey stays unique when SIGAA feed repeats same class date and title`() {
        val duplicatedEntry = AtualizacaoTurmaFeed(
            dataTexto = "16/10/2026",
            disciplinaSemestre = "ELETRONICA EXPERIMENTAL (2026.2)",
            idTurma = "1616625",
            tituloNoticia = "Nova Notícia: Experimento 07 - Roteiro",
        )
        val feed = listOf(
            duplicatedEntry,
            duplicatedEntry,
            duplicatedEntry.copy(tituloNoticia = "Novo Arquivo Inserido"),
        )

        val keys = feed.mapIndexed { index, item -> turmaFeedItemKey(index, item) }
        assertEquals(feed.size, keys.distinct().size)
    }
}
