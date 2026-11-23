package com.unbpocket.core.model.ru

import com.unbpocket.core.model.common.CoreError

/**
 * Campi da UnB atendidos pelo Restaurante Universitario conforme `ru.unb.br`.
 */
enum class CampusRu(
    val slug: String,
    val displayName: String,
    val headingKeyword: String,
    val operatingDaysSummary: String,
) {
    DARCY_RIBEIRO("darcy-ribeiro", "Darcy Ribeiro", "Darcy Ribeiro", "Segunda a Domingo"),
    CEILANDIA("ceilandia", "Ceilândia", "Ceilândia", "Segunda a Sábado"),
    GAMA("gama", "Gama", "Gama", "Segunda a Sexta"),
    PLANALTINA("planaltina", "Planaltina", "Planaltina", "Segunda a Domingo"),
    FAZENDA_AGUA_LIMPA("fazenda-agua-limpa", "Fazenda Água Limpa", "Fazenda Água Limpa", "Segunda a Sexta"),
}

enum class TipoRefeicao(val pageIndex: Int, val displayName: String) {
    CAFE_DA_MANHA(0, "Café da Manhã"),
    ALMOCO(1, "Almoço"),
    JANTAR(2, "Jantar"),
}

enum class DiaSemanaRu(
    val dayOfWeekIso: Int,
    val shortLabel: String,
    val displayName: String,
) {
    SEGUNDA(1, "SEG", "Segunda-feira"),
    TERCA(2, "TER", "Terça-feira"),
    QUARTA(3, "QUA", "Quarta-feira"),
    QUINTA(4, "QUI", "Quinta-feira"),
    SEXTA(5, "SEX", "Sexta-feira"),
    SABADO(6, "SÁB", "Sábado"),
    DOMINGO(7, "DOM", "Domingo");

    companion object {
        fun fromDayOfWeekIso(dayOfWeekIso: Int): DiaSemanaRu? =
            entries.firstOrNull { it.dayOfWeekIso == dayOfWeekIso }
    }
}

data class RuNotificacaoConfig(
    val habilitado: Boolean = false,
    val refeicoesPorDia: Map<DiaSemanaRu, Set<TipoRefeicao>> = emptyMap(),
) {
    fun isEnabledFor(dia: DiaSemanaRu, refeicao: TipoRefeicao): Boolean =
        habilitado && (refeicoesPorDia[dia]?.contains(refeicao) == true)

    fun hasAnySlotSelected(): Boolean =
        habilitado && refeicoesPorDia.values.any { it.isNotEmpty() }
}

data class RuNotificacaoConteudo(
    val campus: CampusRu,
    val dataTexto: String,
    val dayIndex: Int,
    val refeicao: TipoRefeicao,
    val titulo: String,
    val mensagem: String,
)

data class RuDeepLinkTarget(
    val campus: CampusRu?,
    val dataTexto: String?,
    val dayIndex: Int?,
    val refeicao: TipoRefeicao,
)

object RuNotificationExtras {
    const val EXTRA_CAMPUS_SLUG = "com.unbpocket.app.extra.RU_CAMPUS_SLUG"
    const val EXTRA_DATA_TEXTO = "com.unbpocket.app.extra.RU_DATA_TEXTO"
    const val EXTRA_DAY_INDEX = "com.unbpocket.app.extra.RU_DAY_INDEX"
    const val EXTRA_MEAL_PAGE_INDEX = "com.unbpocket.app.extra.RU_MEAL_PAGE_INDEX"
}

/**
 * Ordem fixa dos 10 icones de referencia no rodape de cada pagina dos PDFs do RU.
 */
enum class Alergenico(val id: String, val labelPtBr: String, val legendOrderIndex: Int) {
    COGUMELO("COGUMELO", "Cogumelo", 0),
    LEITE("LEITE", "Leite e derivados", 1),
    MEL("MEL", "Mel", 2),
    PIMENTA("PIMENTA", "Pimenta", 3),
    SOJA("SOJA", "Soja", 4),
    GLUTEN("GLUTEN", "Trigo/Glúten", 5),
    AMENDOIM("AMENDOIM", "Amendoim", 6),
    OLEAGINOSA("OLEAGINOSA", "Oleaginosa", 7),
    OVO("OVO", "Ovo", 8),
    SUINO("SUINO", "Suíno", 9),
}

enum class GrupoAcessoRu(val codigo: String, val roman: String, val descricao: String) {
    GRUPO_1("Grupo 1", "I", "Estudantes PNAES / CadÚnico / Cotas Sociais / Indígenas / Quilombolas"),
    GRUPO_2("Grupo 2", "II", "Estudantes de graduação e pós-graduação (ampla concorrência)"),
    GRUPO_3("Grupo 3", "III", "Servidores, terceirizados e visitantes"),
    GRUPO_4("Grupo 4", "IV", "Estudantes de graduação oriundos de escola pública"),
    EXECUTIVO("Executivo", "Executivo", "Restaurante Executivo (almoço por quilo)"),
    DESCONHECIDO("Desconhecido", "-", "Grupo ainda não identificado no extrato"),
}

enum class CardapioParserStatus {
    PARSED_STRUCTURED,
    FALLBACK_IMAGE_ONLY,
    FALLBACK_PDF_ONLY,
}

data class ItemCardapioRu(
    val categoria: String,
    val prato: String,
    val alergenicos: List<Alergenico>,
)

data class CardapioDiaRu(
    val dataTexto: String,
    val diaSemana: String,
    val refeicoes: Map<TipoRefeicao, List<ItemCardapioRu>>,
)

data class CardapioSemanalRu(
    val campus: CampusRu,
    val semanaLabel: String,
    val pdfUrl: String,
    val fallbackImageUrl: String?,
    val localPdfPath: String?,
    val modifiedGmt: String,
    val parserStatus: CardapioParserStatus,
    val dias: List<CardapioDiaRu>,
)

data class TransacaoRu(
    val dataHoraTexto: String,
    val descricao: String,
    val valorCentavos: Long,
)

data class RefeicoesRestantes(
    val cafesRestantes: Int,
    val almocosOuJantaresRestantes: Int,
    val trocoCafeCentavos: Long,
    val trocoAlmocoJantarCentavos: Long,
    val gratuito: Boolean,
)

data class SaldoExtratoRu(
    val saldoAtualCentavos: Long,
    val saldoAnterior7DiasCentavos: Long?,
    val grupoAcesso: GrupoAcessoRu,
    val transacoesUltimos7Dias: List<TransacaoRu>,
    val refeicoesRestantes: RefeicoesRestantes?,
)

data class PrecoGrupoRu(
    val grupo: GrupoAcessoRu,
    val publicoAlvo: String,
    val cafeCentavos: Long?,
    val almocoCentavos: Long?,
    val jantarCentavos: Long?,
    val porKgCentavos: Long?,
)

data class TabelaPrecosRu(
    val resolucaoReferencia: String,
    val modifiedGmt: String,
    val grupos: List<PrecoGrupoRu>,
    val formasPagamento: List<String>,
)

data class NoticiaRu(
    val id: Long,
    val dateIso: String,
    val title: String,
    val excerptText: String,
    val contentHtml: String,
    val link: String,
    val featuredImageUrl: String?,
    val anexoPdfUrls: List<String>,
)

data class RuPreferencias(
    val campusSelecionado: CampusRu = CampusRu.DARCY_RIBEIRO,
    val alergenicosRestritos: Set<Alergenico> = emptySet(),
    val ocultarPratosComAlergenicos: Boolean = false,
    val noticiasVistasIds: Set<Long> = emptySet(),
    val notificacoes: RuNotificacaoConfig = RuNotificacaoConfig(),
)

/**
 * Resumo da verificacao automatica semanal dos cardapios de todos os campi do RU.
 */
data class RuAutoSyncSummary(
    val checkedAtEpochMillis: Long,
    val skippedDueToCooldown: Boolean,
    val updatedCampi: Set<CampusRu>,
    val upToDateCampi: Set<CampusRu>,
    val failedCampi: Map<CampusRu, CoreError>,
    val tabelaPrecosAtualizada: Boolean = false,
    val noticiasAtualizadas: Boolean = false,
) {
    val anyUpdated: Boolean
        get() = updatedCampi.isNotEmpty() || tabelaPrecosAtualizada || noticiasAtualizadas
}
