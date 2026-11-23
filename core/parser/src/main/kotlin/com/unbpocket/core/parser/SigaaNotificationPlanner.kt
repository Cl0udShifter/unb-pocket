package com.unbpocket.core.parser

import com.unbpocket.core.model.discente.BoletimSemestre
import com.unbpocket.core.model.discente.IndicesIntegralizacao
import com.unbpocket.core.model.notification.AulaLeadMinutes
import com.unbpocket.core.model.notification.SigaaNotificacaoConfig
import com.unbpocket.core.model.notification.SigaaNotificacaoConteudo
import com.unbpocket.core.model.notification.SigaaNotificacaoTipo
import com.unbpocket.core.model.ru.SaldoExtratoRu
import com.unbpocket.core.model.turmas.AtualizacaoTurmaFeed
import com.unbpocket.core.model.turmas.AvaliacaoTurma
import com.unbpocket.core.model.turmas.DiaSemanaSigaa
import com.unbpocket.core.model.turmas.HorarioSlot
import com.unbpocket.core.model.turmas.MapaFrequenciaTurma
import com.unbpocket.core.model.turmas.NotasParciaisTurma
import com.unbpocket.core.model.turmas.TarefaTurma
import com.unbpocket.core.model.turmas.TurmaDetalheAva
import com.unbpocket.core.model.turmas.TurmaResumo
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Evento local agendavel (proxima aula, prova ou entrega de tarefa) no fuso de Brasilia.
 */
data class ScheduledSigaaReminder(
    val notificacao: SigaaNotificacaoConteudo,
    val triggerAtEpochMillis: Long,
    val eventAtEpochMillis: Long,
)

/**
 * Registro serializavel da frequencia de uma turma para deteccao de transicao de risco.
 */
data class FrequenciaBaselineState(
    val faltasComputadas: Int,
    val limiteMaximoFaltas: Int,
    val faltasRestantes: Int,
    val emRiscoReprovacaoPorFalta: Boolean,
)

/**
 * Registro serializavel de notas parciais de uma turma.
 */
data class NotasParciaisBaselineState(
    val unidades: Map<String, String>,
    val mediaFinal: String?,
    val situacao: String?,
)

/**
 * Registro serializavel de integralizacao curricular.
 */
data class IndicesBaselineState(
    val ira: Double?,
    val mp: Double?,
    val percentualIntegralizadoExato: Double?,
    val percentualIntegralizadoTexto: String?,
)

/**
 * Estado consolidado da ultima sincronizacao usada para detectar novidades sem spam inicial.
 */
data class SigaaNotificationBaseline(
    val seededAtEpochMillis: Long = 0L,
    val feedInitialized: Boolean = false,
    val feedAssinaturas: Set<String> = emptySet(),
    val avisosPorTurma: Map<String, Set<String>> = emptyMap(),
    val materiaisPorTurma: Map<String, Set<String>> = emptyMap(),
    val notasPorTurma: Map<String, NotasParciaisBaselineState> = emptyMap(),
    val frequenciaPorTurma: Map<String, FrequenciaBaselineState> = emptyMap(),
    val ruSaldoInitialized: Boolean = false,
    val ruTransacoesAssinaturas: List<String> = emptyList(),
    val lastAlertedLowRuSaldoCentavos: Long? = null,
    val lastAlertedLowRuMeals: Int? = null,
    val boletimInitialized: Boolean = false,
    val boletimDisciplinas: Map<String, String> = emptyMap(),
    val indices: IndicesBaselineState? = null,
)

/**
 * Entrada atual consolidada dos caches/repositorios para comparacao contra o baseline.
 */
data class SigaaSnapshotInput(
    val turmas: List<TurmaResumo> = emptyList(),
    val feed: List<AtualizacaoTurmaFeed> = emptyList(),
    val feedAvailable: Boolean = false,
    val detalhesPorTurma: Map<String, TurmaDetalheAva> = emptyMap(),
    val notasPorTurma: Map<String, NotasParciaisTurma> = emptyMap(),
    val frequenciaPorTurma: Map<String, MapaFrequenciaTurma> = emptyMap(),
    val saldoExtratoRu: SaldoExtratoRu? = null,
    val boletim: List<BoletimSemestre>? = null,
    val indices: IndicesIntegralizacao? = null,
)

/**
 * Resultado da avaliacao de diferencas entre snapshots SIGAA/Discente.
 */
data class SigaaDiffResult(
    val notifications: List<SigaaNotificacaoConteudo>,
    val updatedBaseline: SigaaNotificationBaseline,
)

/**
 * Planner puro (JVM) para lembretes locais de aulas/provas/tarefas e deteccao de alteracoes
 * em segundo plano para Aulas & Turmas e Painel Discente.
 */
object SigaaNotificationPlanner {
    const val TRIGGER_GRACE_WINDOW_MILLIS: Long = 15 * 60 * 1000L

    private val brasiliaZone: ZoneId = RuMenuFreshness.brasiliaZone
    private val ptBrLocale: Locale = Locale.forLanguageTag("pt-BR")
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val timeRegex = Regex("""(\d{1,2})\s*[:hH]\s*(\d{2})""")
    private val dateTokenRegex = Regex("""(\d{2}/\d{2}/\d{4}|\d{2}/\d{2}/\d{2}|\d{2}/\d{2})(?!\d)""")
    private val shortYearFormatter = DateTimeFormatter.ofPattern("dd/MM/yy")

    fun buildUpcomingReminders(
        turmas: List<TurmaResumo>,
        avaliacoesByTurma: Map<String, List<AvaliacaoTurma>>,
        tarefasByTurma: Map<String, List<TarefaTurma>>,
        config: SigaaNotificacaoConfig,
        nowEpochMillis: Long,
        horizonDays: Int = 7,
    ): List<ScheduledSigaaReminder> {
        if (!config.hasAnyLocalScheduleEnabled) return emptyList()

        val nowZoned = Instant.ofEpochMilli(nowEpochMillis).atZone(brasiliaZone)
        val today = nowZoned.toLocalDate()
        val endEpochMillis = nowEpochMillis + horizonDays.coerceAtLeast(1) * 24L * 60L * 60L * 1000L
        val minTriggerEpochMillis = nowEpochMillis - TRIGGER_GRACE_WINDOW_MILLIS

        val reminders = mutableListOf<ScheduledSigaaReminder>()
        val leadMinutes = resolveValidLeadMinutes(config.antecedenciaAulaMinutos)

        for (turma in turmas) {
            if (isTurmaMuted(turma, config)) continue

            if (config.aulasLembrete) {
                reminders += buildClassRemindersForTurma(
                    turma = turma,
                    leadMinutes = leadMinutes,
                    today = today,
                    horizonDays = horizonDays,
                    nowEpochMillis = nowEpochMillis,
                    minTriggerEpochMillis = minTriggerEpochMillis,
                    endEpochMillis = endEpochMillis,
                )
            }

            if (config.provasETarefasLembrete) {
                val avaliacoes = avaliacoesByTurma[turma.frontEndIdTurma]
                    ?: avaliacoesByTurma[turma.idTurma]
                    ?: emptyList()
                for (avaliacao in avaliacoes) {
                    reminders += buildExamReminders(
                        turma = turma,
                        avaliacao = avaliacao,
                        referenceDate = today,
                        nowEpochMillis = nowEpochMillis,
                        minTriggerEpochMillis = minTriggerEpochMillis,
                        endEpochMillis = endEpochMillis,
                    )
                }

                val tarefas = tarefasByTurma[turma.frontEndIdTurma]
                    ?: tarefasByTurma[turma.idTurma]
                    ?: emptyList()
                for (tarefa in tarefas) {
                    reminders += buildTaskReminders(
                        turma = turma,
                        tarefa = tarefa,
                        referenceDate = today,
                        nowEpochMillis = nowEpochMillis,
                        minTriggerEpochMillis = minTriggerEpochMillis,
                        endEpochMillis = endEpochMillis,
                    )
                }
            }
        }

        return reminders.sortedBy { it.triggerAtEpochMillis }
    }

    fun extractDueReminders(
        reminders: List<ScheduledSigaaReminder>,
        alreadySentKeys: Set<String>,
        nowEpochMillis: Long,
    ): List<ScheduledSigaaReminder> = reminders.filter { reminder ->
        reminder.triggerAtEpochMillis <= nowEpochMillis &&
            reminder.triggerAtEpochMillis >= nowEpochMillis - TRIGGER_GRACE_WINDOW_MILLIS &&
            reminder.eventAtEpochMillis > nowEpochMillis &&
            reminder.notificacao.dedupKey !in alreadySentKeys
    }

    fun findNextTriggerEpochMillis(
        reminders: List<ScheduledSigaaReminder>,
        alreadySentKeys: Set<String>,
        nowEpochMillis: Long,
    ): Long? = reminders
        .asSequence()
        .filter { it.triggerAtEpochMillis > nowEpochMillis && it.notificacao.dedupKey !in alreadySentKeys }
        .map { it.triggerAtEpochMillis }
        .minOrNull()

    fun seedBaseline(
        snapshot: SigaaSnapshotInput,
        nowEpochMillis: Long,
    ): SigaaNotificationBaseline {
        val currentFeedSignatures = snapshot.feed.map(::feedSignature).toSet()
        val noticesByTurma = buildNoticesSignatureMap(snapshot.detalhesPorTurma)
        val materialsByTurma = buildMaterialsSignatureMap(snapshot.detalhesPorTurma)
        val gradesByTurma = snapshot.notasPorTurma.mapValues { (_, notas) -> notas.toBaselineState() }
        val freqByTurma = snapshot.frequenciaPorTurma.mapValues { (_, freq) -> freq.toBaselineState() }
        val saldo = snapshot.saldoExtratoRu
        val boletimMap = snapshot.boletim?.let(::buildBoletimSignatureMap).orEmpty()

        return SigaaNotificationBaseline(
            seededAtEpochMillis = nowEpochMillis,
            feedInitialized = snapshot.feedAvailable,
            feedAssinaturas = currentFeedSignatures,
            avisosPorTurma = noticesByTurma,
            materiaisPorTurma = materialsByTurma,
            notasPorTurma = gradesByTurma,
            frequenciaPorTurma = freqByTurma,
            ruSaldoInitialized = saldo != null,
            ruTransacoesAssinaturas = saldo?.transacoesUltimos7Dias?.map(::transactionSignature).orEmpty(),
            lastAlertedLowRuSaldoCentavos = null,
            lastAlertedLowRuMeals = null,
            boletimInitialized = snapshot.boletim != null,
            boletimDisciplinas = boletimMap,
            indices = snapshot.indices?.toBaselineState(),
        )
    }

    fun detectSnapshotChanges(
        previous: SigaaNotificationBaseline?,
        snapshot: SigaaSnapshotInput,
        config: SigaaNotificacaoConfig,
        nowEpochMillis: Long,
    ): SigaaDiffResult {
        if (previous == null || previous.seededAtEpochMillis <= 0L) {
            return SigaaDiffResult(
                notifications = emptyList(),
                updatedBaseline = seedBaseline(snapshot, nowEpochMillis),
            )
        }

        val notifications = mutableListOf<SigaaNotificacaoConteudo>()
        val turmaByAnyId = buildTurmaLookup(snapshot.turmas)
        val mutedById = resolveMutedIds(snapshot.turmas, config)

        val currentNoticesMap = buildNoticesSignatureMap(snapshot.detalhesPorTurma)
        val currentMaterialsMap = buildMaterialsSignatureMap(snapshot.detalhesPorTurma)
        val notifiedTitlesByTurma = mutableMapOf<String, MutableSet<String>>()

        // 1) Novos avisos e materiais nos topicos das turmas
        val updatedNoticesMap = previous.avisosPorTurma.toMutableMap()
        val updatedMaterialsMap = previous.materiaisPorTurma.toMutableMap()
        for ((turmaKey, detalhe) in snapshot.detalhesPorTurma) {
            val prevNotices = previous.avisosPorTurma[turmaKey]
            val prevMaterials = previous.materiaisPorTurma[turmaKey]
            val currNotices = currentNoticesMap[turmaKey].orEmpty()
            val currMaterials = currentMaterialsMap[turmaKey].orEmpty()

            if (prevNotices != null && config.isTurmasAtivo && config.avisosEMateriaisNovos &&
                turmaKey !in mutedById && detalhe.idTurma !in mutedById
            ) {
                val addedNoticeTitles = collectNewNoticeTitles(detalhe, prevNotices)
                if (addedNoticeTitles.isNotEmpty()) {
                    notifiedTitlesByTurma.getOrPut(detalhe.idTurma) { mutableSetOf() }
                        .addAll(addedNoticeTitles.map { normalizeComparable(it) })
                    val resumoTexto = if (addedNoticeTitles.size == 1) {
                        addedNoticeTitles.first()
                    } else {
                        "${addedNoticeTitles.size} novos comunicados: ${addedNoticeTitles.first()}"
                    }
                    val dedupKey = "turma_aviso:${detalhe.idTurma}:${addedNoticeTitles.sorted().joinToString("|").hashCode()}"
                    notifications += SigaaNotificacaoConteudo(
                        dedupKey = dedupKey,
                        notificationId = stableNotificationId(dedupKey),
                        tipo = SigaaNotificacaoTipo.TURMA_AVISO_OU_MATERIAL,
                        titulo = "Novo aviso em ${detalhe.nomeDisciplina}",
                        resumo = resumoTexto,
                        detalhesExpandido = addedNoticeTitles.joinToString("\n"),
                        triggerAtEpochMillis = nowEpochMillis,
                    )
                }
            }

            if (prevMaterials != null && config.isTurmasAtivo && config.avisosEMateriaisNovos &&
                turmaKey !in mutedById && detalhe.idTurma !in mutedById
            ) {
                val addedMaterials = collectNewMaterialNames(detalhe, prevMaterials)
                if (addedMaterials.isNotEmpty()) {
                    val resumoTexto = if (addedMaterials.size == 1) {
                        "Material: ${addedMaterials.first()}"
                    } else {
                        "${addedMaterials.size} novos materiais: ${addedMaterials.first()}"
                    }
                    val dedupKey = "turma_material:${detalhe.idTurma}:${addedMaterials.sorted().joinToString("|").hashCode()}"
                    notifications += SigaaNotificacaoConteudo(
                        dedupKey = dedupKey,
                        notificationId = stableNotificationId(dedupKey),
                        tipo = SigaaNotificacaoTipo.TURMA_AVISO_OU_MATERIAL,
                        titulo = "Novo material em ${detalhe.nomeDisciplina}",
                        resumo = resumoTexto,
                        detalhesExpandido = addedMaterials.joinToString("\n"),
                        triggerAtEpochMillis = nowEpochMillis,
                    )
                }
            }

            updatedNoticesMap[turmaKey] = currNotices
            updatedMaterialsMap[turmaKey] = currMaterials
        }

        // 2) Feed geral de atualizacoes do portal (para avisos que ainda nao vieram pelo detalhe da turma)
        val nextFeedInitialized = previous.feedInitialized || snapshot.feedAvailable
        val nextFeedSignatures = if (snapshot.feedAvailable) {
            snapshot.feed.map(::feedSignature).toSet()
        } else {
            previous.feedAssinaturas
        }
        if (snapshot.feedAvailable && previous.feedInitialized && config.isTurmasAtivo && config.avisosEMateriaisNovos) {
            val newFeedItems = snapshot.feed.filter { item ->
                feedSignature(item) !in previous.feedAssinaturas &&
                    item.idTurma !in mutedById &&
                    normalizeComparable(item.tituloNoticia) !in notifiedTitlesByTurma[item.idTurma].orEmpty()
            }
            for (item in newFeedItems) {
                val turma = turmaByAnyId[item.idTurma]
                val disciplinaNome = turma?.nomeDisciplina ?: item.disciplinaSemestre
                val dedupKey = "feed:${item.idTurma}:${feedSignature(item).hashCode()}"
                notifications += SigaaNotificacaoConteudo(
                    dedupKey = dedupKey,
                    notificationId = stableNotificationId(dedupKey),
                    tipo = SigaaNotificacaoTipo.TURMA_AVISO_OU_MATERIAL,
                    titulo = "Atualização em $disciplinaNome",
                    resumo = item.tituloNoticia,
                    triggerAtEpochMillis = nowEpochMillis,
                )
            }
        }

        // 3) Notas parciais postadas ou alteradas
        val updatedNotasMap = previous.notasPorTurma.toMutableMap()
        for ((turmaKey, notas) in snapshot.notasPorTurma) {
            val prevNotas = previous.notasPorTurma[turmaKey]
            val currState = notas.toBaselineState()
            if (prevNotas != null && config.isTurmasAtivo && config.notasParciaisAlteradas && turmaKey !in mutedById) {
                val changedUnits = mutableListOf<String>()
                for ((unitName, unitValue) in currState.unidades) {
                    val oldVal = prevNotas.unidades[unitName]
                    if (unitValue.isNotBlank() && unitValue != "-" && unitValue != oldVal) {
                        changedUnits += "$unitName: $unitValue"
                    }
                }
                if (!currState.mediaFinal.isNullOrBlank() && currState.mediaFinal != "-" &&
                    currState.mediaFinal != prevNotas.mediaFinal
                ) {
                    changedUnits += "Média final: ${currState.mediaFinal}"
                }
                if (!currState.situacao.isNullOrBlank() && currState.situacao != prevNotas.situacao &&
                    changedUnits.isEmpty()
                ) {
                    changedUnits += "Situação: ${currState.situacao}"
                }
                if (changedUnits.isNotEmpty()) {
                    val nomeTurma = turmaByAnyId[turmaKey]?.nomeDisciplina
                        ?: snapshot.detalhesPorTurma[turmaKey]?.nomeDisciplina
                        ?: turmaKey
                    val dedupKey = "nota_parcial:$turmaKey:${changedUnits.joinToString("|").hashCode()}"
                    notifications += SigaaNotificacaoConteudo(
                        dedupKey = dedupKey,
                        notificationId = stableNotificationId(dedupKey),
                        tipo = SigaaNotificacaoTipo.NOTA_PARCIAL_ALTERADA,
                        titulo = "Nota atualizada em $nomeTurma",
                        resumo = changedUnits.joinToString(" · "),
                        detalhesExpandido = changedUnits.joinToString("\n"),
                        triggerAtEpochMillis = nowEpochMillis,
                    )
                }
            }
            updatedNotasMap[turmaKey] = currState
        }

        // 4) Risco de reprovacao por faltas
        val updatedFreqMap = previous.frequenciaPorTurma.toMutableMap()
        for ((turmaKey, freq) in snapshot.frequenciaPorTurma) {
            val prevFreq = previous.frequenciaPorTurma[turmaKey]
            val currState = freq.toBaselineState()
            if (prevFreq != null && config.isTurmasAtivo && config.riscoFaltaAlerta && turmaKey !in mutedById) {
                val prevTier = riskTierOf(prevFreq)
                val currTier = riskTierOf(currState)
                val worsened = currTier.severity > prevTier.severity
                val gotMoreAbsencesInRisk = currTier.severity > RiskTier.OK.severity &&
                    currState.faltasComputadas > prevFreq.faltasComputadas
                if (worsened || gotMoreAbsencesInRisk) {
                    val nomeTurma = turmaByAnyId[turmaKey]?.nomeDisciplina
                        ?: snapshot.detalhesPorTurma[turmaKey]?.nomeDisciplina
                        ?: turmaKey
                    val titulo = if (currTier == RiskTier.RISCO) {
                        "Risco de reprovação por faltas: $nomeTurma"
                    } else {
                        "Atenção nas faltas: $nomeTurma"
                    }
                    val resumo = buildString {
                        append("${currState.faltasComputadas}/${currState.limiteMaximoFaltas} faltas")
                        if (currState.faltasRestantes >= 0) {
                            append(" (${currState.faltasRestantes} restantes)")
                        }
                    }
                    val dedupKey = "risco_falta:$turmaKey:${currTier.name}:${currState.faltasComputadas}"
                    notifications += SigaaNotificacaoConteudo(
                        dedupKey = dedupKey,
                        notificationId = stableNotificationId(dedupKey),
                        tipo = SigaaNotificacaoTipo.RISCO_FALTAS,
                        titulo = titulo,
                        resumo = resumo,
                        triggerAtEpochMillis = nowEpochMillis,
                    )
                }
            }
            updatedFreqMap[turmaKey] = currState
        }

        // 5) Painel Discente: saldo baixo RU + novas movimentacoes no extrato
        val saldo = snapshot.saldoExtratoRu
        val nextRuSaldoInitialized = previous.ruSaldoInitialized || saldo != null
        val nextRuTxList = saldo?.transacoesUltimos7Dias?.map(::transactionSignature)
            ?: previous.ruTransacoesAssinaturas
        var nextAlertedLowSaldo = previous.lastAlertedLowRuSaldoCentavos
        var nextAlertedLowMeals = previous.lastAlertedLowRuMeals

        if (saldo != null && previous.ruSaldoInitialized) {
            val rest = saldo.refeicoesRestantes
            val isGratuito = rest?.gratuito == true
            val mealsRemaining = rest?.almocosOuJantaresRestantes
            val threshold = config.limiteRefeicoesRuBaixo.coerceAtLeast(1)

            if (!isGratuito && mealsRemaining != null && mealsRemaining <= threshold) {
                val shouldAlert = config.isDiscenteAtivo && config.saldoRuBaixoAlerta && (
                    previous.lastAlertedLowRuSaldoCentavos == null ||
                        saldo.saldoAtualCentavos < previous.lastAlertedLowRuSaldoCentavos ||
                        mealsRemaining < (previous.lastAlertedLowRuMeals ?: Int.MAX_VALUE)
                    )
                if (shouldAlert) {
                    val saldoFormatado = formatBrlFromCentavos(saldo.saldoAtualCentavos)
                    val mealWord = if (mealsRemaining == 1) "refeição" else "refeições"
                    val dedupKey = "ru_saldo_baixo:${saldo.saldoAtualCentavos}:$mealsRemaining:$threshold"
                    notifications += SigaaNotificacaoConteudo(
                        dedupKey = dedupKey,
                        notificationId = stableNotificationId(dedupKey),
                        tipo = SigaaNotificacaoTipo.RU_SALDO_BAIXO,
                        titulo = "Saldo do RU baixo ($mealsRemaining $mealWord)",
                        resumo = "Saldo atual: $saldoFormatado",
                        triggerAtEpochMillis = nowEpochMillis,
                    )
                    nextAlertedLowSaldo = saldo.saldoAtualCentavos
                    nextAlertedLowMeals = mealsRemaining
                }
            } else {
                nextAlertedLowSaldo = null
                nextAlertedLowMeals = null
            }

            if (config.isDiscenteAtivo && config.extratoRuMovimentacaoAlerta) {
                val newTransactions = findAddedTransactions(
                    previous = previous.ruTransacoesAssinaturas,
                    current = saldo.transacoesUltimos7Dias,
                )
                if (newTransactions.isNotEmpty()) {
                    val resumo = if (newTransactions.size == 1) {
                        val tx = newTransactions.first()
                        val sinal = if (tx.valorCentavos >= 0) "+" else "-"
                        "${tx.descricao}: $sinal${formatBrlFromCentavos(abs(tx.valorCentavos))}"
                    } else {
                        val firstTx = newTransactions.first()
                        "${newTransactions.size} movimentações · ${firstTx.descricao}"
                    }
                    val dedupKey = "ru_extrato:${newTransactions.joinToString("|", transform = ::transactionSignature).hashCode()}"
                    notifications += SigaaNotificacaoConteudo(
                        dedupKey = dedupKey,
                        notificationId = stableNotificationId(dedupKey),
                        tipo = SigaaNotificacaoTipo.RU_EXTRATO_MOVIMENTACAO,
                        titulo = if (newTransactions.size == 1) {
                            "Nova movimentação no extrato do RU"
                        } else {
                            "Novas movimentações no extrato do RU"
                        },
                        resumo = resumo,
                        triggerAtEpochMillis = nowEpochMillis,
                    )
                }
            }
        }

        // 6) Boletim geral atualizado
        val currentBoletimMap = snapshot.boletim?.let(::buildBoletimSignatureMap)
            ?: previous.boletimDisciplinas
        val nextBoletimInitialized = previous.boletimInitialized || snapshot.boletim != null
        if (snapshot.boletim != null && previous.boletimInitialized &&
            config.isDiscenteAtivo && config.boletimAlteradoAlerta
        ) {
            val changedSubjects = mutableListOf<String>()
            for (semestre in snapshot.boletim) {
                for (disc in semestre.disciplinas) {
                    val key = "${semestre.semestre}|${disc.codigo}"
                    val currSig = boletimSubjectSignature(disc)
                    val prevSig = previous.boletimDisciplinas[key]
                    if (currSig != prevSig) {
                        val status = listOfNotNull(
                            disc.resultadoMencao?.takeIf { it.isNotBlank() && it != "-" },
                            disc.nota1?.takeIf { it.isNotBlank() && it != "-" },
                            disc.situacao.takeIf { it.isNotBlank() },
                        ).firstOrNull().orEmpty()
                        changedSubjects += if (status.isNotBlank()) {
                            "${disc.disciplina}: $status"
                        } else {
                            disc.disciplina
                        }
                    }
                }
            }
            if (changedSubjects.isNotEmpty()) {
                val resumo = if (changedSubjects.size == 1) {
                    changedSubjects.first()
                } else {
                    "${changedSubjects.size} alterações: ${changedSubjects.first()}"
                }
                val dedupKey = "boletim:${changedSubjects.sorted().joinToString("|").hashCode()}"
                notifications += SigaaNotificacaoConteudo(
                    dedupKey = dedupKey,
                    notificationId = stableNotificationId(dedupKey),
                    tipo = SigaaNotificacaoTipo.BOLETIM_ATUALIZADO,
                    titulo = "Boletim atualizado no SIGAA",
                    resumo = resumo,
                    detalhesExpandido = changedSubjects.joinToString("\n"),
                    triggerAtEpochMillis = nowEpochMillis,
                )
            }
        }

        // 7) Indices academicos / integralizacao
        val currIndices = snapshot.indices?.toBaselineState()
        val prevIndices = previous.indices
        val nextIndices = currIndices ?: prevIndices
        if (currIndices != null && prevIndices != null &&
            config.isDiscenteAtivo && config.indicesAlteradosAlerta
        ) {
            val indexChanges = detectIndicesChanges(prevIndices, currIndices)
            if (indexChanges.isNotEmpty()) {
                val dedupKey = "indices:${indexChanges.joinToString("|").hashCode()}"
                notifications += SigaaNotificacaoConteudo(
                    dedupKey = dedupKey,
                    notificationId = stableNotificationId(dedupKey),
                    tipo = SigaaNotificacaoTipo.INDICES_ATUALIZADOS,
                    titulo = "Índices acadêmicos atualizados",
                    resumo = indexChanges.joinToString(" · "),
                    detalhesExpandido = indexChanges.joinToString("\n"),
                    triggerAtEpochMillis = nowEpochMillis,
                )
            }
        }

        val updatedBaseline = SigaaNotificationBaseline(
            seededAtEpochMillis = previous.seededAtEpochMillis,
            feedInitialized = nextFeedInitialized,
            feedAssinaturas = nextFeedSignatures,
            avisosPorTurma = updatedNoticesMap,
            materiaisPorTurma = updatedMaterialsMap,
            notasPorTurma = updatedNotasMap,
            frequenciaPorTurma = updatedFreqMap,
            ruSaldoInitialized = nextRuSaldoInitialized,
            ruTransacoesAssinaturas = nextRuTxList,
            lastAlertedLowRuSaldoCentavos = nextAlertedLowSaldo,
            lastAlertedLowRuMeals = nextAlertedLowMeals,
            boletimInitialized = nextBoletimInitialized,
            boletimDisciplinas = currentBoletimMap,
            indices = nextIndices,
        )

        return SigaaDiffResult(
            notifications = notifications,
            updatedBaseline = updatedBaseline,
        )
    }

    private fun buildClassRemindersForTurma(
        turma: TurmaResumo,
        leadMinutes: Int,
        today: LocalDate,
        horizonDays: Int,
        nowEpochMillis: Long,
        minTriggerEpochMillis: Long,
        endEpochMillis: Long,
    ): List<ScheduledSigaaReminder> {
        if (turma.horarioSlots.isEmpty()) return emptyList()

        val result = mutableListOf<ScheduledSigaaReminder>()
        val slotsByDay = turma.horarioSlots.groupBy { it.diaSemana }

        for (dayOffset in 0..horizonDays) {
            val targetDate = today.plusDays(dayOffset.toLong())
            val sigaaDay = toSigaaDayOfWeek(targetDate.dayOfWeek) ?: continue
            val daySlots = slotsByDay[sigaaDay].orEmpty()
            if (daySlots.isEmpty()) continue

            val blocks = mergeContiguousClassBlocks(daySlots)
            for (block in blocks) {
                val classStart = LocalDateTime.of(targetDate, block.start)
                val classStartEpoch = classStart.atZone(brasiliaZone).toInstant().toEpochMilli()
                val triggerAt = classStart.minusMinutes(leadMinutes.toLong())
                val triggerEpoch = triggerAt.atZone(brasiliaZone).toInstant().toEpochMilli()

                if (classStartEpoch <= nowEpochMillis) continue
                if (triggerEpoch < minTriggerEpochMillis || triggerEpoch > endEpochMillis) continue

                val startStr = block.start.format(timeFormatter)
                val endStr = block.end.format(timeFormatter)
                val localPart = turma.local.trim().takeIf { it.isNotEmpty() }
                val resumo = if (localPart != null) {
                    "$startStr - $endStr · Local: $localPart"
                } else {
                    "$startStr - $endStr"
                }
                val dedupKey = "aula:${turma.idTurma}:$targetDate:$startStr:$leadMinutes"
                result += ScheduledSigaaReminder(
                    notificacao = SigaaNotificacaoConteudo(
                        dedupKey = dedupKey,
                        notificationId = stableNotificationId(dedupKey),
                        tipo = SigaaNotificacaoTipo.AULA_PROXIMA,
                        titulo = "Aula às $startStr: ${turma.nomeDisciplina}",
                        resumo = resumo,
                        triggerAtEpochMillis = triggerEpoch,
                    ),
                    triggerAtEpochMillis = triggerEpoch,
                    eventAtEpochMillis = classStartEpoch,
                )
            }
        }
        return result
    }

    private fun buildExamReminders(
        turma: TurmaResumo,
        avaliacao: AvaliacaoTurma,
        referenceDate: LocalDate,
        nowEpochMillis: Long,
        minTriggerEpochMillis: Long,
        endEpochMillis: Long,
    ): List<ScheduledSigaaReminder> {
        val examDate = parseLastDate(avaliacao.dataTexto, referenceDate) ?: return emptyList()
        val examTime = avaliacao.horaTexto?.let(::parseFirstTime)
        val eventTime = examTime ?: LocalTime.of(8, 0)
        val eventEpoch = LocalDateTime.of(examDate, eventTime).atZone(brasiliaZone).toInstant().toEpochMilli()
        if (eventEpoch <= nowEpochMillis) return emptyList()

        val reminders = mutableListOf<ScheduledSigaaReminder>()
        val timeText = examTime?.format(timeFormatter)
        val localSuffix = turma.local.trim().takeIf { it.isNotEmpty() }?.let { " · $it" }.orEmpty()
        val baseResumo = buildString {
            append(avaliacao.descricao.trim())
            if (timeText != null) {
                append(" ($timeText)")
            }
            append(localSuffix)
        }

        // Vespera as 20:00
        val eveTrigger = LocalDateTime.of(examDate.minusDays(1), LocalTime.of(20, 0))
        val eveEpoch = eveTrigger.atZone(brasiliaZone).toInstant().toEpochMilli()
        if (eveEpoch in minTriggerEpochMillis..endEpochMillis) {
            val dedupKey = "aval_vespera:${turma.idTurma}:$examDate:${avaliacao.descricao.hashCode()}"
            reminders += ScheduledSigaaReminder(
                notificacao = SigaaNotificacaoConteudo(
                    dedupKey = dedupKey,
                    notificationId = stableNotificationId(dedupKey),
                    tipo = SigaaNotificacaoTipo.AVALIACAO_OU_TAREFA,
                    titulo = "Avaliação amanhã: ${turma.nomeDisciplina}",
                    resumo = baseResumo,
                    triggerAtEpochMillis = eveEpoch,
                ),
                triggerAtEpochMillis = eveEpoch,
                eventAtEpochMillis = eventEpoch,
            )
        }

        // No dia: 1h antes se horario conhecido, ou 07:00
        val sameDayTrigger = if (examTime != null) {
            LocalDateTime.of(examDate, examTime).minusHours(1)
        } else {
            LocalDateTime.of(examDate, LocalTime.of(7, 0))
        }
        val sameDayEpoch = sameDayTrigger.atZone(brasiliaZone).toInstant().toEpochMilli()
        if (sameDayEpoch in minTriggerEpochMillis..endEpochMillis) {
            val title = if (timeText != null) {
                "Avaliação às $timeText: ${turma.nomeDisciplina}"
            } else {
                "Avaliação hoje: ${turma.nomeDisciplina}"
            }
            val dedupKey = "aval_dia:${turma.idTurma}:$examDate:${timeText ?: "0700"}:${avaliacao.descricao.hashCode()}"
            reminders += ScheduledSigaaReminder(
                notificacao = SigaaNotificacaoConteudo(
                    dedupKey = dedupKey,
                    notificationId = stableNotificationId(dedupKey),
                    tipo = SigaaNotificacaoTipo.AVALIACAO_OU_TAREFA,
                    titulo = title,
                    resumo = baseResumo,
                    triggerAtEpochMillis = sameDayEpoch,
                ),
                triggerAtEpochMillis = sameDayEpoch,
                eventAtEpochMillis = eventEpoch,
            )
        }

        return reminders
    }

    private fun buildTaskReminders(
        turma: TurmaResumo,
        tarefa: TarefaTurma,
        referenceDate: LocalDate,
        nowEpochMillis: Long,
        minTriggerEpochMillis: Long,
        endEpochMillis: Long,
    ): List<ScheduledSigaaReminder> {
        val (deadlineDate, deadlineTime) = parseTaskDeadline(tarefa.periodoEntrega, referenceDate)
            ?: return emptyList()
        val effectiveEventTime = deadlineTime ?: LocalTime.of(23, 59)
        val eventEpoch = LocalDateTime.of(deadlineDate, effectiveEventTime)
            .atZone(brasiliaZone)
            .toInstant()
            .toEpochMilli()
        if (eventEpoch <= nowEpochMillis) return emptyList()

        val reminders = mutableListOf<ScheduledSigaaReminder>()
        val timeText = deadlineTime?.format(timeFormatter)
        val resumo = buildString {
            append(tarefa.titulo.trim())
            if (timeText != null) {
                append(" · Prazo até $timeText")
            } else if (tarefa.periodoEntrega.isNotBlank()) {
                append(" · ${tarefa.periodoEntrega.trim()}")
            }
        }

        // Vespera as 20:00
        val eveTrigger = LocalDateTime.of(deadlineDate.minusDays(1), LocalTime.of(20, 0))
        val eveEpoch = eveTrigger.atZone(brasiliaZone).toInstant().toEpochMilli()
        if (eveEpoch in minTriggerEpochMillis..endEpochMillis) {
            val dedupKey = "tarefa_vespera:${turma.idTurma}:$deadlineDate:${tarefa.titulo.hashCode()}"
            reminders += ScheduledSigaaReminder(
                notificacao = SigaaNotificacaoConteudo(
                    dedupKey = dedupKey,
                    notificationId = stableNotificationId(dedupKey),
                    tipo = SigaaNotificacaoTipo.AVALIACAO_OU_TAREFA,
                    titulo = "Entrega amanhã: ${turma.nomeDisciplina}",
                    resumo = resumo,
                    triggerAtEpochMillis = eveEpoch,
                ),
                triggerAtEpochMillis = eveEpoch,
                eventAtEpochMillis = eventEpoch,
            )
        }

        // No dia: 1h antes se horario conhecido, ou 07:00
        val sameDayTrigger = if (deadlineTime != null) {
            LocalDateTime.of(deadlineDate, deadlineTime).minusHours(1)
        } else {
            LocalDateTime.of(deadlineDate, LocalTime.of(7, 0))
        }
        val sameDayEpoch = sameDayTrigger.atZone(brasiliaZone).toInstant().toEpochMilli()
        if (sameDayEpoch in minTriggerEpochMillis..endEpochMillis) {
            val dedupKey = "tarefa_dia:${turma.idTurma}:$deadlineDate:${timeText ?: "0700"}:${tarefa.titulo.hashCode()}"
            reminders += ScheduledSigaaReminder(
                notificacao = SigaaNotificacaoConteudo(
                    dedupKey = dedupKey,
                    notificationId = stableNotificationId(dedupKey),
                    tipo = SigaaNotificacaoTipo.AVALIACAO_OU_TAREFA,
                    titulo = if (timeText != null) {
                        "Entrega até $timeText: ${turma.nomeDisciplina}"
                    } else {
                        "Entrega hoje: ${turma.nomeDisciplina}"
                    },
                    resumo = resumo,
                    triggerAtEpochMillis = sameDayEpoch,
                ),
                triggerAtEpochMillis = sameDayEpoch,
                eventAtEpochMillis = eventEpoch,
            )
        }

        return reminders
    }

    private data class ClassBlock(
        val start: LocalTime,
        val end: LocalTime,
    )

    private fun mergeContiguousClassBlocks(slots: List<HorarioSlot>): List<ClassBlock> {
        val parsed = slots.mapNotNull { slot ->
            val start = parseFirstTime(slot.inicio) ?: return@mapNotNull null
            val end = parseFirstTime(slot.fim) ?: return@mapNotNull null
            ClassBlock(start = start, end = end)
        }.sortedBy { it.start }

        if (parsed.isEmpty()) return emptyList()

        val merged = mutableListOf<ClassBlock>()
        var current = parsed.first()
        for (index in 1 until parsed.size) {
            val next = parsed[index]
            val gapMinutes = Duration.between(current.end, next.start).toMinutes()
            if (gapMinutes <= 15L) {
                current = current.copy(
                    end = if (next.end.isAfter(current.end)) next.end else current.end,
                )
            } else {
                merged += current
                current = next
            }
        }
        merged += current
        return merged
    }

    private fun parseTaskDeadline(
        periodoEntrega: String,
        referenceDate: LocalDate,
    ): Pair<LocalDate, LocalTime?>? {
        val dateMatches = dateTokenRegex.findAll(periodoEntrega).toList()
        val lastMatch = dateMatches.lastOrNull() ?: return null
        val parsedDate = parseDateToken(lastMatch.groupValues[1], referenceDate) ?: return null
        val trailingSubstring = periodoEntrega.substring(lastMatch.range.first)
        val parsedTime = parseFirstTime(trailingSubstring)
        return parsedDate to parsedTime
    }

    private fun parseLastDate(
        text: String,
        referenceDate: LocalDate,
    ): LocalDate? {
        val lastToken = dateTokenRegex.findAll(text).lastOrNull()?.groupValues?.get(1)
        if (lastToken != null) {
            parseDateToken(lastToken, referenceDate)?.let { return it }
        }
        return RuMenuFreshness.extractDatesFromText(text, referenceDate).lastOrNull()
    }

    private fun parseDateToken(
        token: String,
        referenceDate: LocalDate,
    ): LocalDate? {
        val trimmed = token.trim()
        if (trimmed.length == 8 && trimmed.count { it == '/' } == 2) {
            return runCatching { LocalDate.parse(trimmed, shortYearFormatter) }.getOrNull()
        }
        return RuMenuFreshness.extractDatesFromText(trimmed, referenceDate).firstOrNull()
    }

    private fun parseFirstTime(text: String): LocalTime? {
        val match = timeRegex.find(text) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }

    private fun toSigaaDayOfWeek(dayOfWeek: DayOfWeek): DiaSemanaSigaa? = when (dayOfWeek) {
        DayOfWeek.MONDAY -> DiaSemanaSigaa.SEGUNDA
        DayOfWeek.TUESDAY -> DiaSemanaSigaa.TERCA
        DayOfWeek.WEDNESDAY -> DiaSemanaSigaa.QUARTA
        DayOfWeek.THURSDAY -> DiaSemanaSigaa.QUINTA
        DayOfWeek.FRIDAY -> DiaSemanaSigaa.SEXTA
        DayOfWeek.SATURDAY -> DiaSemanaSigaa.SABADO
        DayOfWeek.SUNDAY -> null
    }

    private fun resolveValidLeadMinutes(rawMinutes: Int): Int = when (rawMinutes) {
        AulaLeadMinutes.MIN_15.minutes -> AulaLeadMinutes.MIN_15.minutes
        AulaLeadMinutes.MIN_30.minutes -> AulaLeadMinutes.MIN_30.minutes
        AulaLeadMinutes.MIN_60.minutes -> AulaLeadMinutes.MIN_60.minutes
        else -> AulaLeadMinutes.MIN_30.minutes
    }

    private fun isTurmaMuted(
        turma: TurmaResumo,
        config: SigaaNotificacaoConfig,
    ): Boolean = turma.frontEndIdTurma in config.turmasSilenciadas || turma.idTurma in config.turmasSilenciadas

    private fun resolveMutedIds(
        turmas: List<TurmaResumo>,
        config: SigaaNotificacaoConfig,
    ): Set<String> {
        if (config.turmasSilenciadas.isEmpty()) return emptySet()
        val resolved = config.turmasSilenciadas.toMutableSet()
        for (turma in turmas) {
            if (isTurmaMuted(turma, config)) {
                resolved += turma.frontEndIdTurma
                resolved += turma.idTurma
            }
        }
        return resolved
    }

    private fun buildTurmaLookup(turmas: List<TurmaResumo>): Map<String, TurmaResumo> {
        val map = mutableMapOf<String, TurmaResumo>()
        for (turma in turmas) {
            map[turma.frontEndIdTurma] = turma
            map[turma.idTurma] = turma
        }
        return map
    }

    private fun buildNoticesSignatureMap(
        detalhesPorTurma: Map<String, TurmaDetalheAva>,
    ): Map<String, Set<String>> = detalhesPorTurma.mapValues { (_, detalhe) ->
        val set = mutableSetOf<String>()
        for (resumo in detalhe.historicoNoticiasResumo) {
            set += noticeSignature(resumo.titulo, resumo.dataHoraTexto)
        }
        detalhe.ultimaNoticia?.let { ultima ->
            set += noticeSignature(ultima.titulo, ultima.dataHoraTexto)
        }
        set
    }

    private fun buildMaterialsSignatureMap(
        detalhesPorTurma: Map<String, TurmaDetalheAva>,
    ): Map<String, Set<String>> = detalhesPorTurma.mapValues { (_, detalhe) ->
        detalhe.topicosAula
            .flatMap { it.materiais }
            .map { "${it.materialId}|${it.nome.trim()}" }
            .toSet()
    }

    private fun collectNewNoticeTitles(
        detalhe: TurmaDetalheAva,
        previousSignatures: Set<String>,
    ): List<String> {
        val seenThisRound = mutableSetOf<String>()
        val addedTitles = mutableListOf<String>()

        detalhe.ultimaNoticia?.let { ultima ->
            val sig = noticeSignature(ultima.titulo, ultima.dataHoraTexto)
            if (sig !in previousSignatures && seenThisRound.add(sig) && ultima.titulo.isNotBlank()) {
                addedTitles += ultima.titulo.trim()
            }
        }
        for (resumo in detalhe.historicoNoticiasResumo) {
            val sig = noticeSignature(resumo.titulo, resumo.dataHoraTexto)
            if (sig !in previousSignatures && seenThisRound.add(sig) && resumo.titulo.isNotBlank()) {
                addedTitles += resumo.titulo.trim()
            }
        }
        return addedTitles
    }

    private fun collectNewMaterialNames(
        detalhe: TurmaDetalheAva,
        previousSignatures: Set<String>,
    ): List<String> = detalhe.topicosAula
        .flatMap { it.materiais }
        .mapNotNull { mat ->
            val sig = "${mat.materialId}|${mat.nome.trim()}"
            if (sig !in previousSignatures && mat.nome.isNotBlank()) mat.nome.trim() else null
        }
        .distinct()

    private fun noticeSignature(title: String, dateText: String): String =
        "${normalizeComparable(title)}|${normalizeComparable(dateText)}"

    private fun feedSignature(item: AtualizacaoTurmaFeed): String =
        "${normalizeComparable(item.dataTexto)}|${item.idTurma.trim()}|${normalizeComparable(item.tituloNoticia)}"

    private fun transactionSignature(
        tx: com.unbpocket.core.model.ru.TransacaoRu,
    ): String = "${normalizeComparable(tx.dataHoraTexto)}|${normalizeComparable(tx.descricao)}|${tx.valorCentavos}"

    private fun findAddedTransactions(
        previous: List<String>,
        current: List<com.unbpocket.core.model.ru.TransacaoRu>,
    ): List<com.unbpocket.core.model.ru.TransacaoRu> {
        val remainingCounts = previous.groupingBy { it }.eachCount().toMutableMap()
        val added = mutableListOf<com.unbpocket.core.model.ru.TransacaoRu>()
        for (tx in current) {
            val sig = transactionSignature(tx)
            val prevCount = remainingCounts[sig] ?: 0
            if (prevCount > 0) {
                remainingCounts[sig] = prevCount - 1
            } else {
                added += tx
            }
        }
        return added
    }

    private fun buildBoletimSignatureMap(boletim: List<BoletimSemestre>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (semestre in boletim) {
            for (disc in semestre.disciplinas) {
                val key = "${semestre.semestre}|${disc.codigo}"
                map[key] = boletimSubjectSignature(disc)
            }
        }
        return map
    }

    private fun boletimSubjectSignature(
        disc: com.unbpocket.core.model.discente.NotaDisciplinaBoletim,
    ): String = "${disc.nota1.orEmpty().trim()}|${disc.resultadoMencao.orEmpty().trim()}|${disc.situacao.trim()}"

    private fun detectIndicesChanges(
        prev: IndicesBaselineState,
        curr: IndicesBaselineState,
    ): List<String> {
        val changes = mutableListOf<String>()
        if (curr.ira != null && (prev.ira == null || abs(curr.ira - prev.ira) >= 0.0001)) {
            changes += if (prev.ira != null) {
                "IRA: ${formatDecimal(prev.ira)} -> ${formatDecimal(curr.ira)}"
            } else {
                "IRA: ${formatDecimal(curr.ira)}"
            }
        }
        if (curr.mp != null && (prev.mp == null || abs(curr.mp - prev.mp) >= 0.0001)) {
            changes += if (prev.mp != null) {
                "MP: ${formatDecimal(prev.mp)} -> ${formatDecimal(curr.mp)}"
            } else {
                "MP: ${formatDecimal(curr.mp)}"
            }
        }
        val currPct = curr.percentualIntegralizadoExato
        if (currPct != null && (prev.percentualIntegralizadoExato == null || abs(currPct - prev.percentualIntegralizadoExato) >= 0.01)) {
            val pctText = curr.percentualIntegralizadoTexto?.takeIf { it.isNotBlank() }
                ?: String.format(ptBrLocale, "%.1f%%", currPct)
            changes += "Integralização: $pctText"
        }
        return changes
    }

    private enum class RiskTier(val severity: Int) {
        OK(0),
        ATENCAO(1),
        RISCO(2),
    }

    private fun riskTierOf(state: FrequenciaBaselineState): RiskTier = when {
        state.emRiscoReprovacaoPorFalta -> RiskTier.RISCO
        state.limiteMaximoFaltas > 0 && state.faltasComputadas >= state.limiteMaximoFaltas -> RiskTier.RISCO
        state.limiteMaximoFaltas > 0 && state.faltasComputadas > 0 && state.faltasRestantes in 1..2 -> RiskTier.ATENCAO
        else -> RiskTier.OK
    }

    private fun NotasParciaisTurma.toBaselineState(): NotasParciaisBaselineState = NotasParciaisBaselineState(
        unidades = unidades.associate { it.nome.trim() to (it.valor?.trim().orEmpty()) },
        mediaFinal = mediaFinal?.trim(),
        situacao = situacao?.trim(),
    )

    private fun MapaFrequenciaTurma.toBaselineState(): FrequenciaBaselineState = FrequenciaBaselineState(
        faltasComputadas = faltasComputadas,
        limiteMaximoFaltas = limiteMaximoFaltas,
        faltasRestantes = faltasRestantes,
        emRiscoReprovacaoPorFalta = emRiscoReprovacaoPorFalta,
    )

    private fun IndicesIntegralizacao.toBaselineState(): IndicesBaselineState = IndicesBaselineState(
        ira = ira,
        mp = mp,
        percentualIntegralizadoExato = percentualIntegralizadoExato,
        percentualIntegralizadoTexto = percentualIntegralizadoTexto?.trim(),
    )

    private fun normalizeComparable(raw: String): String =
        raw.trim().replace(Regex("\\s+"), " ").lowercase(ptBrLocale)

    private fun formatDecimal(value: Double): String =
        String.format(ptBrLocale, "%.4f", value).trimEnd('0').trimEnd(',', '.')

    private fun formatBrlFromCentavos(centavos: Long): String {
        val reais = centavos / 100.0
        return "R$ " + String.format(ptBrLocale, "%.2f", reais)
    }

    private fun stableNotificationId(dedupKey: String): Int =
        2000 + (dedupKey.hashCode() and 0x7FFF_FFFF) % 800_000
}
