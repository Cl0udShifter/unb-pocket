package com.unbpocket.core.parser

import com.unbpocket.core.model.discente.BoletimSemestre
import com.unbpocket.core.model.discente.IndicesIntegralizacao
import com.unbpocket.core.model.discente.NotaDisciplinaBoletim
import com.unbpocket.core.model.notification.SigaaNotificacaoConfig
import com.unbpocket.core.model.notification.SigaaNotificacaoTipo
import com.unbpocket.core.model.ru.GrupoAcessoRu
import com.unbpocket.core.model.ru.RefeicoesRestantes
import com.unbpocket.core.model.ru.SaldoExtratoRu
import com.unbpocket.core.model.ru.TransacaoRu
import com.unbpocket.core.model.turmas.AtualizacaoTurmaFeed
import com.unbpocket.core.model.turmas.AvaliacaoTurma
import com.unbpocket.core.model.turmas.MapaFrequenciaTurma
import com.unbpocket.core.model.turmas.MaterialAulaAva
import com.unbpocket.core.model.turmas.NotasParciaisTurma
import com.unbpocket.core.model.turmas.NoticiaTurmaAva
import com.unbpocket.core.model.turmas.NoticiaTurmaResumoAva
import com.unbpocket.core.model.turmas.TarefaTurma
import com.unbpocket.core.model.turmas.TopicoAulaAva
import com.unbpocket.core.model.turmas.TurmaDetalheAva
import com.unbpocket.core.model.turmas.TurmaResumo
import com.unbpocket.core.model.turmas.UnidadeNotaTurma
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

class SigaaNotificationPlannerTest {

    private fun millisBrasilia(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(
            LocalDate.of(year, month, day),
            LocalTime.of(hour, minute),
            RuMenuFreshness.brasiliaZone,
        ).toInstant().toEpochMilli()

    private fun sampleTurma(
        id: String = "100",
        frontEndId: String = "fe_100",
        nome: String = "REDES SEM FIO",
        local: String = "FT AT-31/12",
        horarioRaw: String = "2M12",
    ): TurmaResumo = TurmaResumo(
        idTurma = id,
        frontEndIdTurma = frontEndId,
        semestre = "2026.2",
        codigoDisciplina = "ENE0064",
        nomeDisciplina = nome,
        numeroTurma = "01",
        cargaHorariaHoras = 60,
        docenteNome = "Joao",
        local = local,
        codigoHorarioRaw = horarioRaw,
        horarioSlots = SigaaScheduleDecoder.decode(horarioRaw),
        horarioExtenso = emptyList(),
    )

    @Test
    fun `merges contiguous class slots respects lead minutes and ignores muted classes`() {
        // 23/11/2026 e segunda-feira
        val now = millisBrasilia(2026, 11, 23, 7, 0)
        // 2M12 = 08:00-09:50 (2 slots contiguos -> 1 bloco) + 2T23 = 14:00-15:50 (outro bloco)
        val turmaAtiva = sampleTurma(id = "100", frontEndId = "fe_100", horarioRaw = "2M12 2T23")
        val turmaSilenciada = sampleTurma(id = "200", frontEndId = "fe_200", nome = "SILENCIADA", horarioRaw = "2M34")

        val config = SigaaNotificacaoConfig(
            habilitado = true,
            turmasHabilitado = true,
            aulasLembrete = true,
            antecedenciaAulaMinutos = 30,
            provasETarefasLembrete = false,
            turmasSilenciadas = setOf("fe_200"),
        )

        val reminders = SigaaNotificationPlanner.buildUpcomingReminders(
            turmas = listOf(turmaAtiva, turmaSilenciada),
            avaliacoesByTurma = emptyMap(),
            tarefasByTurma = emptyMap(),
            config = config,
            nowEpochMillis = now,
            horizonDays = 1,
        )

        // Apenas 2 blocos na segunda-feira (08:00 e 14:00) para turma ativa
        assertEquals(2, reminders.size)
        assertEquals(millisBrasilia(2026, 11, 23, 7, 30), reminders[0].triggerAtEpochMillis)
        assertTrue(reminders[0].notificacao.titulo.contains("08:00"))
        assertTrue(reminders[0].notificacao.resumo.contains("08:00 - 09:50"))
        assertTrue(reminders[0].notificacao.resumo.contains("FT AT-31/12"))

        assertEquals(millisBrasilia(2026, 11, 23, 13, 30), reminders[1].triggerAtEpochMillis)
        assertTrue(reminders[1].notificacao.titulo.contains("14:00"))

        // Extracao no momento do primeiro alarme + deduplicacao
        val atFirstTrigger = millisBrasilia(2026, 11, 23, 7, 31)
        val due = SigaaNotificationPlanner.extractDueReminders(reminders, emptySet(), atFirstTrigger)
        assertEquals(1, due.size)
        assertEquals(reminders[0].notificacao.dedupKey, due.first().notificacao.dedupKey)

        val dueAfterMarkedSent = SigaaNotificationPlanner.extractDueReminders(
            reminders,
            setOf(reminders[0].notificacao.dedupKey),
            atFirstTrigger,
        )
        assertTrue(dueAfterMarkedSent.isEmpty())

        val next = SigaaNotificationPlanner.findNextTriggerEpochMillis(
            reminders,
            setOf(reminders[0].notificacao.dedupKey),
            atFirstTrigger,
        )
        assertEquals(millisBrasilia(2026, 11, 23, 13, 30), next)
    }

    @Test
    fun `schedules exam and task deadlines on eve 20h and morning 07h or 1h before`() {
        val now = millisBrasilia(2026, 11, 23, 10, 0)
        val turma = sampleTurma()
        val config = SigaaNotificacaoConfig(
            habilitado = true,
            turmasHabilitado = true,
            aulasLembrete = false,
            provasETarefasLembrete = true,
        )

        val avaliacoes = mapOf(
            "fe_100" to listOf(
                AvaliacaoTurma(dataTexto = "25/11/2026", horaTexto = "14:00", descricao = "Prova 1"),
                AvaliacaoTurma(dataTexto = "26/11/2026", horaTexto = null, descricao = "Seminario"),
            ),
        )
        val tarefas = mapOf(
            "fe_100" to listOf(
                TarefaTurma(
                    titulo = "Lista 3",
                    periodoEntrega = "De 20/11/2026 às 08:00 até 25/11/2026 às 23:59",
                    notaMaxima = "10",
                    possuiArquivo = false,
                    emGrupo = false,
                ),
            ),
        )

        val reminders = SigaaNotificationPlanner.buildUpcomingReminders(
            turmas = listOf(turma),
            avaliacoesByTurma = avaliacoes,
            tarefasByTurma = tarefas,
            config = config,
            nowEpochMillis = now,
            horizonDays = 5,
        )

        val triggers = reminders.map { it.triggerAtEpochMillis to it.notificacao.titulo }
        assertTrue(triggers.any { it.first == millisBrasilia(2026, 11, 24, 20, 0) && it.second.contains("Avaliação amanhã") })
        assertTrue(triggers.any { it.first == millisBrasilia(2026, 11, 25, 13, 0) && it.second.contains("14:00") })
        assertTrue(triggers.any { it.first == millisBrasilia(2026, 11, 25, 20, 0) && it.second.contains("Avaliação amanhã") })
        assertTrue(triggers.any { it.first == millisBrasilia(2026, 11, 26, 7, 0) && it.second.contains("Avaliação hoje") })
        assertTrue(triggers.any { it.first == millisBrasilia(2026, 11, 24, 20, 0) && it.second.contains("Entrega amanhã") })
        assertTrue(triggers.any { it.first == millisBrasilia(2026, 11, 25, 22, 59) && it.second.contains("23:59") })
    }

    @Test
    fun `detectSnapshotChanges seeds quietly on first run then notifies diffs across turmas and discente`() {
        val now = millisBrasilia(2026, 11, 23, 12, 0)
        val turma = sampleTurma()
        val config = SigaaNotificacaoConfig(
            habilitado = true,
            turmasHabilitado = true,
            discenteHabilitado = true,
            avisosEMateriaisNovos = true,
            notasParciaisAlteradas = true,
            riscoFaltaAlerta = true,
            saldoRuBaixoAlerta = true,
            limiteRefeicoesRuBaixo = 2,
            extratoRuMovimentacaoAlerta = true,
            boletimAlteradoAlerta = true,
            indicesAlteradosAlerta = true,
        )

        val initialDetalhe = TurmaDetalheAva(
            idTurma = "100",
            frontEndIdTurma = "fe_100",
            codigoDisciplina = "ENE0064",
            nomeDisciplina = "REDES SEM FIO",
            periodoTurmaTexto = "2026.2",
            aulasMinistradas = 20,
            aulasTotal = 60,
            percentualAndamento = 33,
            ultimaNoticia = NoticiaTurmaAva(
                idNoticia = "n1",
                titulo = "Boas vindas",
                dataHoraTexto = "10/08/2026 08:00",
                conteudoHtml = "<p>Ola</p>",
                conteudoTexto = "Ola",
                autor = "Prof",
                anexos = emptyList(),
            ),
            historicoNoticiasResumo = listOf(NoticiaTurmaResumoAva("n1", "10/08/2026 08:00", "Boas vindas")),
            topicosAula = listOf(
                TopicoAulaAva(
                    index = 0,
                    titulo = "Semana 1",
                    periodoTexto = null,
                    conteudoTexto = "",
                    materiais = listOf(MaterialAulaAva("m1", "Plano.pdf", "PDF", 0)),
                ),
            ),
        )

        val initialSnapshot = SigaaSnapshotInput(
            turmas = listOf(turma),
            feed = listOf(AtualizacaoTurmaFeed("10/08/2026", "ENE0064", "100", "Boas vindas")),
            feedAvailable = true,
            detalhesPorTurma = mapOf("fe_100" to initialDetalhe),
            notasPorTurma = mapOf(
                "fe_100" to NotasParciaisTurma(
                    notasDisponiveis = true,
                    mensagemAviso = null,
                    unidades = listOf(UnidadeNotaTurma("P1", null)),
                    mediaFinal = null,
                    faltas = 2,
                    situacao = "MATRICULADO",
                ),
            ),
            frequenciaPorTurma = mapOf(
                "fe_100" to MapaFrequenciaTurma(
                    presencasRegistradas = 18,
                    aulasComRegistro = 20,
                    percentualSobreRegistradas = 90,
                    aulasDefinidasCh = 60,
                    percentualSobreCh = 90,
                    faltasComputadas = 2,
                    limiteMaximoFaltas = 15,
                    faltasRestantes = 13,
                    emRiscoReprovacaoPorFalta = false,
                    registrosDiarios = emptyList(),
                ),
            ),
            saldoExtratoRu = SaldoExtratoRu(
                saldoAtualCentavos = 2500L,
                saldoAnterior7DiasCentavos = 2500L,
                grupoAcesso = GrupoAcessoRu.GRUPO_2,
                transacoesUltimos7Dias = listOf(TransacaoRu("22/11 12:00", "Almoco", -610L)),
                refeicoesRestantes = RefeicoesRestantes(8, 4, 0L, 0L, gratuito = false),
            ),
            boletim = listOf(
                BoletimSemestre(
                    semestre = "2026.2",
                    disciplinas = listOf(
                        NotaDisciplinaBoletim("ENE0064", "REDES SEM FIO", "-", "-", 2, "MATRICULADO"),
                    ),
                ),
            ),
            indices = IndicesIntegralizacao(4.2, 4.1, 100, 0, 0, 200, 50.0, "50.0%"),
        )

        // 1) Seed inicial nao notifica nada
        val seedRun = SigaaNotificationPlanner.detectSnapshotChanges(null, initialSnapshot, config, now)
        assertTrue(seedRun.notifications.isEmpty())

        // 2) Novo snapshot com atualizacoes em todas as categorias + novo aviso presente no feed e no detalhe
        val updatedDetalhe = initialDetalhe.copy(
            ultimaNoticia = NoticiaTurmaAva(
                idNoticia = "n2",
                titulo = "Roteiro atualizado",
                dataHoraTexto = "23/11/2026 11:50",
                conteudoHtml = "<p>Veja</p>",
                conteudoTexto = "Veja",
                autor = "Prof",
                anexos = emptyList(),
            ),
            historicoNoticiasResumo = listOf(
                NoticiaTurmaResumoAva("n2", "23/11/2026 11:50", "Roteiro atualizado"),
                NoticiaTurmaResumoAva("n1", "10/08/2026 08:00", "Boas vindas"),
            ),
            topicosAula = listOf(
                initialDetalhe.topicosAula.first().copy(
                    materiais = listOf(
                        MaterialAulaAva("m1", "Plano.pdf", "PDF", 0),
                        MaterialAulaAva("m2", "Slide2.pdf", "PDF", 0),
                    ),
                ),
            ),
        )

        val changedSnapshot = initialSnapshot.copy(
            feed = listOf(
                AtualizacaoTurmaFeed("23/11/2026", "ENE0064", "100", "Roteiro atualizado"),
                AtualizacaoTurmaFeed("10/08/2026", "ENE0064", "100", "Boas vindas"),
            ),
            detalhesPorTurma = mapOf("fe_100" to updatedDetalhe),
            notasPorTurma = mapOf(
                "fe_100" to NotasParciaisTurma(
                    notasDisponiveis = true,
                    mensagemAviso = null,
                    unidades = listOf(UnidadeNotaTurma("P1", "8,75")),
                    mediaFinal = null,
                    faltas = 14,
                    situacao = "MATRICULADO",
                ),
            ),
            frequenciaPorTurma = mapOf(
                "fe_100" to MapaFrequenciaTurma(
                    presencasRegistradas = 18,
                    aulasComRegistro = 32,
                    percentualSobreRegistradas = 56,
                    aulasDefinidasCh = 60,
                    percentualSobreCh = 76,
                    faltasComputadas = 14,
                    limiteMaximoFaltas = 15,
                    faltasRestantes = 1,
                    emRiscoReprovacaoPorFalta = true,
                    registrosDiarios = emptyList(),
                ),
            ),
            saldoExtratoRu = SaldoExtratoRu(
                saldoAtualCentavos = 1000L,
                saldoAnterior7DiasCentavos = 2500L,
                grupoAcesso = GrupoAcessoRu.GRUPO_2,
                transacoesUltimos7Dias = listOf(
                    TransacaoRu("23/11 11:55", "Almoco", -610L),
                    TransacaoRu("22/11 12:00", "Almoco", -610L),
                ),
                refeicoesRestantes = RefeicoesRestantes(4, 1, 0L, 0L, gratuito = false),
            ),
            boletim = listOf(
                BoletimSemestre(
                    semestre = "2026.2",
                    disciplinas = listOf(
                        NotaDisciplinaBoletim("ENE0064", "REDES SEM FIO", "9,0", "SS", 14, "APROVADO"),
                    ),
                ),
            ),
            indices = IndicesIntegralizacao(4.35, 4.2, 80, 0, 0, 200, 60.0, "60.0%"),
        )

        val diffRun = SigaaNotificationPlanner.detectSnapshotChanges(
            previous = seedRun.updatedBaseline,
            snapshot = changedSnapshot,
            config = config,
            nowEpochMillis = now + 10_000L,
        )

        val tipos = diffRun.notifications.map { it.tipo }
        // Aviso "Roteiro atualizado" nao duplica entre feed e detalhe
        assertEquals(2, diffRun.notifications.count { it.tipo == SigaaNotificacaoTipo.TURMA_AVISO_OU_MATERIAL })
        assertTrue(SigaaNotificacaoTipo.NOTA_PARCIAL_ALTERADA in tipos)
        assertTrue(SigaaNotificacaoTipo.RISCO_FALTAS in tipos)
        assertTrue(SigaaNotificacaoTipo.RU_SALDO_BAIXO in tipos)
        assertTrue(SigaaNotificacaoTipo.RU_EXTRATO_MOVIMENTACAO in tipos)
        assertTrue(SigaaNotificacaoTipo.BOLETIM_ATUALIZADO in tipos)
        assertTrue(SigaaNotificacaoTipo.INDICES_ATUALIZADOS in tipos)

        // 3) Reexecucao sem mudanca nao repete saldo baixo nem outras notificacoes
        val repeatRun = SigaaNotificationPlanner.detectSnapshotChanges(
            previous = diffRun.updatedBaseline,
            snapshot = changedSnapshot,
            config = config,
            nowEpochMillis = now + 20_000L,
        )
        assertTrue(repeatRun.notifications.isEmpty())

        // 4) Queda adicional de saldo abaixo do alerta anterior volta a notificar uma vez
        val lowerSaldoSnapshot = changedSnapshot.copy(
            saldoExtratoRu = changedSnapshot.saldoExtratoRu!!.copy(
                saldoAtualCentavos = 390L,
                refeicoesRestantes = RefeicoesRestantes(1, 0, 0L, 0L, gratuito = false),
            ),
        )
        val lowerRun = SigaaNotificationPlanner.detectSnapshotChanges(
            previous = repeatRun.updatedBaseline,
            snapshot = lowerSaldoSnapshot,
            config = config,
            nowEpochMillis = now + 30_000L,
        )
        assertEquals(listOf(SigaaNotificacaoTipo.RU_SALDO_BAIXO), lowerRun.notifications.map { it.tipo })

        // 5) Aluno gratuito nao recebe alerta de saldo baixo mesmo com saldo zero
        val freeStudentSnapshot = lowerSaldoSnapshot.copy(
            saldoExtratoRu = lowerSaldoSnapshot.saldoExtratoRu!!.copy(
                saldoAtualCentavos = 0L,
                refeicoesRestantes = RefeicoesRestantes(0, 0, 0L, 0L, gratuito = true),
            ),
        )
        val freeRun = SigaaNotificationPlanner.detectSnapshotChanges(
            previous = lowerRun.updatedBaseline,
            snapshot = freeStudentSnapshot,
            config = config,
            nowEpochMillis = now + 40_000L,
        )
        assertFalse(freeRun.notifications.any { it.tipo == SigaaNotificacaoTipo.RU_SALDO_BAIXO })
    }

    @Test
    fun `extractDueReminders recovers delayed class and exam reminders under Android 14 inexact alarm window`() {
        // Segunda 23/11/2026 aula 08:00-09:50 com antecedencia 15 min -> gatilho ideal 07:45
        val turma = sampleTurma(horarioRaw = "2M12")
        val config = SigaaNotificacaoConfig(
            habilitado = true,
            turmasHabilitado = true,
            aulasLembrete = true,
            antecedenciaAulaMinutos = 15,
            provasETarefasLembrete = true,
        )
        val exam = AvaliacaoTurma(dataTexto = "24/11/2026", horaTexto = null, descricao = "Prova Final")

        // Atraso de 25 min pelo alarme inexato (08:10, ja apos o inicio da aula das 08:00)
        val delayedClassNow = millisBrasilia(2026, 11, 23, 8, 10)
        val classReminders = SigaaNotificationPlanner.buildUpcomingReminders(
            turmas = listOf(turma),
            avaliacoesByTurma = emptyMap(),
            tarefasByTurma = emptyMap(),
            config = config,
            nowEpochMillis = delayedClassNow,
            horizonDays = 1,
        )
        val dueClass = SigaaNotificationPlanner.extractDueReminders(
            reminders = classReminders,
            alreadySentKeys = emptySet(),
            nowEpochMillis = delayedClassNow,
        )
        assertEquals(1, dueClass.size)
        assertTrue(dueClass.first().notificacao.titulo.contains("Aula iniciou às 08:00"))

        // Apos 45 min do inicio da aula (08:50), nao dispara lembrete tardio antigo
        val tooLateClassNow = millisBrasilia(2026, 11, 23, 8, 50)
        val expiredReminders = SigaaNotificationPlanner.buildUpcomingReminders(
            turmas = listOf(turma),
            avaliacoesByTurma = emptyMap(),
            tarefasByTurma = emptyMap(),
            config = config,
            nowEpochMillis = tooLateClassNow,
            horizonDays = 1,
        )
        assertTrue(
            SigaaNotificationPlanner.extractDueReminders(
                reminders = expiredReminders,
                alreadySentKeys = emptySet(),
                nowEpochMillis = tooLateClassNow,
            ).isEmpty(),
        )

        // Vespera de prova as 20:00 atrasada em 70 min (21:10) ainda dispara na janela de 120 min
        val delayedEveNow = millisBrasilia(2026, 11, 23, 21, 10)
        val examEveReminders = SigaaNotificationPlanner.buildUpcomingReminders(
            turmas = listOf(turma),
            avaliacoesByTurma = mapOf("fe_100" to listOf(exam)),
            tarefasByTurma = emptyMap(),
            config = config.copy(aulasLembrete = false),
            nowEpochMillis = delayedEveNow,
            horizonDays = 2,
        )
        val dueExamEve = SigaaNotificationPlanner.extractDueReminders(
            reminders = examEveReminders,
            alreadySentKeys = emptySet(),
            nowEpochMillis = delayedEveNow,
        )
        assertEquals(1, dueExamEve.size)
        assertTrue(dueExamEve.first().notificacao.titulo.contains("Avaliação amanhã"))

        // Prova no dia sem hora definida (gatilho 07:00) atrasada ate 08:15 ainda dispara
        val delayedSameDayNow = millisBrasilia(2026, 11, 24, 8, 15)
        val sameDayReminders = SigaaNotificationPlanner.buildUpcomingReminders(
            turmas = listOf(turma),
            avaliacoesByTurma = mapOf("fe_100" to listOf(exam)),
            tarefasByTurma = emptyMap(),
            config = config.copy(aulasLembrete = false),
            nowEpochMillis = delayedSameDayNow,
            horizonDays = 2,
        )
        val dueSameDay = SigaaNotificationPlanner.extractDueReminders(
            reminders = sameDayReminders,
            alreadySentKeys = emptySet(),
            nowEpochMillis = delayedSameDayNow,
        )
        assertEquals(1, dueSameDay.size)
        assertTrue(dueSameDay.first().notificacao.titulo.contains("Avaliação hoje"))
    }
}
