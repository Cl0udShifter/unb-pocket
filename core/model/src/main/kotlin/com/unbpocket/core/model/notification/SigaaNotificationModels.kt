package com.unbpocket.core.model.notification

/**
 * Frequencia de sincronizacao em segundo plano para alertas do SIGAA e saldo do RU.
 */
enum class SigaaSyncIntervalHours(
    val hours: Long,
    val label: String,
) {
    H2(2L, "A cada 2h"),
    H4(4L, "A cada 4h"),
    H6(6L, "A cada 6h"),
    H12(12L, "A cada 12h"),
}

/**
 * Antecedencia configuravel para lembretes de proximas aulas.
 */
enum class AulaLeadMinutes(
    val minutes: Int,
    val label: String,
) {
    MIN_15(15, "15 min antes"),
    MIN_30(30, "30 min antes"),
    MIN_60(60, "1h antes"),
}

/**
 * Destino de navegacao principal ao tocar em uma notificacao SIGAA ou de saldo discente.
 */
enum class SigaaNotificacaoTarget(
    val routeTabKey: String,
) {
    TURMAS("TURMAS"),
    DISCENTE("DISCENTE"),
}

/**
 * Categorias de notificacao local e por sincronizacao em segundo plano.
 */
enum class SigaaNotificacaoTipo(
    val target: SigaaNotificacaoTarget,
) {
    AULA_PROXIMA(SigaaNotificacaoTarget.TURMAS),
    TURMA_AVISO_OU_MATERIAL(SigaaNotificacaoTarget.TURMAS),
    AVALIACAO_OU_TAREFA(SigaaNotificacaoTarget.TURMAS),
    NOTA_PARCIAL_ALTERADA(SigaaNotificacaoTarget.TURMAS),
    RISCO_FALTAS(SigaaNotificacaoTarget.TURMAS),
    RU_SALDO_BAIXO(SigaaNotificacaoTarget.DISCENTE),
    RU_EXTRATO_MOVIMENTACAO(SigaaNotificacaoTarget.DISCENTE),
    BOLETIM_ATUALIZADO(SigaaNotificacaoTarget.DISCENTE),
    INDICES_ATUALIZADOS(SigaaNotificacaoTarget.DISCENTE),
}

/**
 * Configuracoes de notificacoes das telas Aulas & Turmas e Painel Discente.
 */
data class SigaaNotificacaoConfig(
    val habilitado: Boolean = false,
    val turmasHabilitado: Boolean = true,
    val aulasLembrete: Boolean = true,
    val antecedenciaAulaMinutos: Int = AulaLeadMinutes.MIN_30.minutes,
    val avisosEMateriaisNovos: Boolean = true,
    val provasETarefasLembrete: Boolean = true,
    val notasParciaisAlteradas: Boolean = true,
    val riscoFaltaAlerta: Boolean = true,
    val turmasSilenciadas: Set<String> = emptySet(),
    val discenteHabilitado: Boolean = true,
    val saldoRuBaixoAlerta: Boolean = true,
    val limiteRefeicoesRuBaixo: Int = DEFAULT_LOW_RU_MEALS_THRESHOLD,
    val extratoRuMovimentacaoAlerta: Boolean = true,
    val boletimAlteradoAlerta: Boolean = true,
    val indicesAlteradosAlerta: Boolean = true,
    val syncIntervalHours: SigaaSyncIntervalHours = SigaaSyncIntervalHours.H4,
    val somenteWifi: Boolean = false,
    val userConfigured: Boolean = false,
) {
    val isTurmasAtivo: Boolean
        get() = habilitado && turmasHabilitado && (
            aulasLembrete ||
                avisosEMateriaisNovos ||
                provasETarefasLembrete ||
                notasParciaisAlteradas ||
                riscoFaltaAlerta
            )

    val isDiscenteAtivo: Boolean
        get() = habilitado && discenteHabilitado && (
            saldoRuBaixoAlerta ||
                extratoRuMovimentacaoAlerta ||
                boletimAlteradoAlerta ||
                indicesAlteradosAlerta
            )

    val hasAnyEnabled: Boolean
        get() = isTurmasAtivo || isDiscenteAtivo

    val hasAnyLocalScheduleEnabled: Boolean
        get() = habilitado && turmasHabilitado && (aulasLembrete || provasETarefasLembrete)

    val hasAnyRemoteSyncEnabled: Boolean
        get() = (
            habilitado &&
                turmasHabilitado &&
                (avisosEMateriaisNovos || notasParciaisAlteradas || riscoFaltaAlerta)
            ) || isDiscenteAtivo

    val needsTurmaDetailsSync: Boolean
        get() = habilitado && turmasHabilitado && (
            avisosEMateriaisNovos ||
                provasETarefasLembrete ||
                notasParciaisAlteradas ||
                riscoFaltaAlerta
            )

    val needsBoletimSync: Boolean
        get() = habilitado && discenteHabilitado && boletimAlteradoAlerta

    val needsDiscenteSaldoSync: Boolean
        get() = habilitado && discenteHabilitado && (
            saldoRuBaixoAlerta ||
                extratoRuMovimentacaoAlerta ||
                indicesAlteradosAlerta
            )

    companion object {
        const val DEFAULT_LOW_RU_MEALS_THRESHOLD: Int = 2
        val LIMITE_REFEICOES_OPCOES: List<Int> = listOf(1, 2, 3, 5)
    }
}

/**
 * Conteudo pronto para exibicao em notificacao do sistema para eventos SIGAA/Discente.
 */
data class SigaaNotificacaoConteudo(
    val dedupKey: String,
    val notificationId: Int,
    val tipo: SigaaNotificacaoTipo,
    val titulo: String,
    val resumo: String,
    val detalhesExpandido: String? = null,
    val target: SigaaNotificacaoTarget = tipo.target,
    val triggerAtEpochMillis: Long,
)

/**
 * Destino de navegacao recebido pela Activity ao tocar em uma notificacao SIGAA/Discente.
 */
data class SigaaDeepLinkTarget(
    val target: SigaaNotificacaoTarget,
)

/**
 * Chaves de Extras da Intent usadas pelas notificacoes de Aulas & Turmas e Painel Discente.
 */
object SigaaNotificationExtras {
    const val EXTRA_TARGET_TAB: String = "sigaa_notification_target_tab"
    const val EXTRA_NOTIFICATION_TYPE: String = "sigaa_notification_type"
}
