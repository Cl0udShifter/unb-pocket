package com.unbpocket.core.data.local

import com.unbpocket.core.model.common.DataOrigin
import com.unbpocket.core.model.common.SyncedData
import com.unbpocket.core.model.discente.BoletimSemestre
import com.unbpocket.core.model.discente.CarteirinhaEstudantil
import com.unbpocket.core.model.discente.DiscenteResumo
import com.unbpocket.core.model.discente.DocumentoOficialEmitido
import com.unbpocket.core.model.discente.IndicesIntegralizacao
import com.unbpocket.core.model.discente.NotaDisciplinaBoletim
import com.unbpocket.core.model.discente.PerfilDiscente
import com.unbpocket.core.model.discente.TipoDocumentoOficial
import com.unbpocket.core.model.ru.Alergenico
import com.unbpocket.core.model.ru.CampusRu
import com.unbpocket.core.model.ru.CardapioDiaRu
import com.unbpocket.core.model.ru.CardapioParserStatus
import com.unbpocket.core.model.ru.CardapioSemanalRu
import com.unbpocket.core.model.ru.GrupoAcessoRu
import com.unbpocket.core.model.ru.ItemCardapioRu
import com.unbpocket.core.model.ru.NoticiaRu
import com.unbpocket.core.model.ru.PrecoGrupoRu
import com.unbpocket.core.model.ru.RefeicoesRestantes
import com.unbpocket.core.model.ru.SaldoExtratoRu
import com.unbpocket.core.model.ru.TabelaPrecosRu
import com.unbpocket.core.model.ru.TipoRefeicao
import com.unbpocket.core.model.ru.TransacaoRu
import com.unbpocket.core.model.turmas.AnexoArquivoSigaa
import com.unbpocket.core.model.turmas.AtualizacaoTurmaFeed
import com.unbpocket.core.model.turmas.AvaliacaoTurma
import com.unbpocket.core.model.turmas.DiaSemanaSigaa
import com.unbpocket.core.model.turmas.HorarioSlot
import com.unbpocket.core.model.turmas.MapaFrequenciaTurma
import com.unbpocket.core.model.turmas.MaterialAulaAva
import com.unbpocket.core.model.turmas.NotasParciaisTurma
import com.unbpocket.core.model.turmas.NoticiaTurmaAva
import com.unbpocket.core.model.turmas.NoticiaTurmaResumoAva
import com.unbpocket.core.model.turmas.RegistroFrequenciaDia
import com.unbpocket.core.model.turmas.TarefaTurma
import com.unbpocket.core.model.turmas.TopicoAulaAva
import com.unbpocket.core.model.turmas.TurmaDetalheAva
import com.unbpocket.core.model.turmas.TurmaResumo
import com.unbpocket.core.model.turmas.TurnoSigaa
import com.unbpocket.core.model.turmas.UnidadeNotaTurma
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Camada de serializacao e mapeamento entre modelos imutaveis de `:core:model`
 * e snapshots persistidos no Room (`SnapshotDao`).
 */
class SnapshotCacheStore(
    private val dao: SnapshotDao,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun <T, D> observe(
        cacheKey: String,
        serializer: KSerializer<D>,
        fromDto: (D) -> T,
    ): Flow<SyncedData<T>?> = dao.observeByKey(cacheKey).map { entity ->
        entity?.toSyncedData(serializer, fromDto)
    }

    suspend fun <T, D> get(
        cacheKey: String,
        serializer: KSerializer<D>,
        fromDto: (D) -> T,
    ): SyncedData<T>? = dao.getByKey(cacheKey)?.toSyncedData(serializer, fromDto)

    /**
     * Grava o snapshot apenas quando ha mudanca estrutural no payload serializado ou na origem.
     * Retorna `true` se o registro foi inserido ou alterado, ou `false` se o cache ja era identico.
     */
    suspend fun <T, D> put(
        cacheKey: String,
        domain: String,
        origin: DataOrigin,
        syncedAtEpochMillis: Long,
        value: T,
        serializer: KSerializer<D>,
        toDto: (T) -> D,
    ): Boolean {
        val payload = json.encodeToString(serializer, toDto(value))
        val existing = dao.getByKey(cacheKey)
        if (existing != null && existing.payloadJson == payload && existing.origin == origin.name) {
            return false
        }
        dao.upsert(
            SnapshotEntity(
                cacheKey = cacheKey,
                domain = domain,
                payloadJson = payload,
                origin = origin.name,
                syncedAtEpochMillis = syncedAtEpochMillis,
            ),
        )
        return true
    }

    suspend fun clearDomain(domain: String) {
        dao.clearDomain(domain)
    }

    suspend fun clearKey(cacheKey: String) {
        dao.clearKey(cacheKey)
    }

    suspend fun clearSigaaUserData() {
        dao.clearDomain(Domains.DISCENTE)
        dao.clearDomain(Domains.TURMAS)
        dao.clearKey(Keys.RU_SALDO)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }

    private fun <T, D> SnapshotEntity.toSyncedData(
        serializer: KSerializer<D>,
        fromDto: (D) -> T,
    ): SyncedData<T>? = runCatching {
        val dto = json.decodeFromString(serializer, payloadJson)
        val resolvedOrigin = runCatching { DataOrigin.valueOf(origin) }.getOrDefault(DataOrigin.CACHE)
        SyncedData(
            value = fromDto(dto),
            origin = resolvedOrigin,
            syncedAtEpochMillis = syncedAtEpochMillis,
        )
    }.getOrNull()

    object Domains {
        const val RU = "ru"
        const val TURMAS = "turmas"
        const val DISCENTE = "discente"
        const val META = "meta"
    }

    object Keys {
        fun cardapio(campus: CampusRu) = "ru_cardapio_${campus.slug}"
        const val RU_SALDO = "ru_saldo"
        const val RU_PRECOS = "ru_precos"
        const val RU_NOTICIAS = "ru_noticias"
        const val RU_AUTO_SYNC_META = "ru_auto_sync_meta"
        const val TURMAS_LIST = "turmas_semestre"
        const val TURMAS_FEED = "turmas_feed"
        fun turmaDetalhe(frontEndId: String) = "turma_detalhe_$frontEndId"
        fun turmaFrequencia(frontEndId: String) = "turma_freq_$frontEndId"
        fun turmaNotas(frontEndId: String) = "turma_notas_$frontEndId"
        fun turmaAvaliacoes(frontEndId: String) = "turma_aval_$frontEndId"
        fun turmaTarefas(frontEndId: String) = "turma_tarefas_$frontEndId"
        fun turmaNoticia(frontEndId: String, idNoticia: String) = "turma_noticia_${frontEndId}_$idNoticia"
        const val DISCENTE_RESUMO = "discente_resumo"
        const val DISCENTE_CARTEIRINHA = "discente_carteirinha"
        const val DISCENTE_BOLETIM = "discente_boletim"
        const val DISCENTE_DOCUMENTOS = "discente_documentos"
    }
}

@Serializable
data class ItemCardapioRuDto(val categoria: String, val prato: String, val alergenicos: List<String>)

@Serializable
data class CardapioDiaRuDto(
    val dataTexto: String,
    val diaSemana: String,
    val refeicoes: Map<String, List<ItemCardapioRuDto>>,
)

@Serializable
data class CardapioSemanalRuDto(
    val campus: String,
    val semanaLabel: String,
    val pdfUrl: String,
    val fallbackImageUrl: String? = null,
    val localPdfPath: String? = null,
    val modifiedGmt: String,
    val parserStatus: String,
    val dias: List<CardapioDiaRuDto>,
)

@Serializable
data class TransacaoRuDto(val dataHoraTexto: String, val descricao: String, val valorCentavos: Long)

@Serializable
data class RefeicoesRestantesDto(
    val cafesRestantes: Int,
    val almocosOuJantaresRestantes: Int,
    val trocoCafeCentavos: Long,
    val trocoAlmocoJantarCentavos: Long,
    val gratuito: Boolean,
)

@Serializable
data class SaldoExtratoRuDto(
    val saldoAtualCentavos: Long,
    val saldoAnterior7DiasCentavos: Long? = null,
    val grupoAcesso: String,
    val transacoesUltimos7Dias: List<TransacaoRuDto>,
    val refeicoesRestantes: RefeicoesRestantesDto? = null,
)

@Serializable
data class PrecoGrupoRuDto(
    val grupo: String,
    val publicoAlvo: String,
    val cafeCentavos: Long? = null,
    val almocoCentavos: Long? = null,
    val jantarCentavos: Long? = null,
    val porKgCentavos: Long? = null,
)

@Serializable
data class TabelaPrecosRuDto(
    val resolucaoReferencia: String,
    val modifiedGmt: String,
    val grupos: List<PrecoGrupoRuDto>,
    val formasPagamento: List<String>,
)

@Serializable
data class NoticiaRuDto(
    val id: Long,
    val dateIso: String,
    val title: String,
    val excerptText: String,
    val contentHtml: String,
    val link: String,
    val featuredImageUrl: String? = null,
    val anexoPdfUrls: List<String>,
)

@Serializable
data class HorarioSlotDto(
    val diaSemana: String,
    val turno: String,
    val tempoNumero: Int,
    val inicio: String,
    val fim: String,
    val codigoOriginal: String,
)

@Serializable
data class TurmaResumoDto(
    val idTurma: String,
    val frontEndIdTurma: String,
    val semestre: String,
    val codigoDisciplina: String? = null,
    val nomeDisciplina: String,
    val numeroTurma: String? = null,
    val cargaHorariaHoras: Int? = null,
    val docenteNome: String? = null,
    val local: String,
    val codigoHorarioRaw: String,
    val horarioSlots: List<HorarioSlotDto>,
    val horarioExtenso: List<String>,
)

@Serializable
data class AtualizacaoTurmaFeedDto(
    val dataTexto: String,
    val disciplinaSemestre: String,
    val idTurma: String,
    val tituloNoticia: String,
)

@Serializable
data class AnexoArquivoSigaaDto(val id: String, val nomeArquivo: String)

@Serializable
data class NoticiaTurmaResumoAvaDto(val idNoticia: String, val dataHoraTexto: String, val titulo: String)

@Serializable
data class NoticiaTurmaAvaDto(
    val idNoticia: String? = null,
    val titulo: String,
    val dataHoraTexto: String,
    val conteudoHtml: String,
    val conteudoTexto: String,
    val autor: String? = null,
    val anexos: List<AnexoArquivoSigaaDto>,
)

@Serializable
data class MaterialAulaAvaDto(val materialId: String, val nome: String, val tipoExtensao: String, val topicoIndex: Int)

@Serializable
data class TopicoAulaAvaDto(
    val index: Int,
    val titulo: String,
    val periodoTexto: String? = null,
    val conteudoTexto: String,
    val materiais: List<MaterialAulaAvaDto>,
)

@Serializable
data class TurmaDetalheAvaDto(
    val idTurma: String,
    val frontEndIdTurma: String,
    val codigoDisciplina: String,
    val nomeDisciplina: String,
    val periodoTurmaTexto: String,
    val aulasMinistradas: Int,
    val aulasTotal: Int,
    val percentualAndamento: Int,
    val ultimaNoticia: NoticiaTurmaAvaDto? = null,
    val historicoNoticiasResumo: List<NoticiaTurmaResumoAvaDto>,
    val topicosAula: List<TopicoAulaAvaDto>,
)

@Serializable
data class RegistroFrequenciaDiaDto(
    val dataTexto: String,
    val situacaoTexto: String,
    val faltasNoDia: Int,
    val presente: Boolean,
)

@Serializable
data class MapaFrequenciaTurmaDto(
    val presencasRegistradas: Int,
    val aulasComRegistro: Int,
    val percentualSobreRegistradas: Int,
    val aulasDefinidasCh: Int,
    val percentualSobreCh: Int,
    val faltasComputadas: Int,
    val limiteMaximoFaltas: Int,
    val faltasRestantes: Int,
    val emRiscoReprovacaoPorFalta: Boolean,
    val registrosDiarios: List<RegistroFrequenciaDiaDto>,
)

@Serializable
data class UnidadeNotaTurmaDto(val nome: String, val valor: String? = null)

@Serializable
data class NotasParciaisTurmaDto(
    val notasDisponiveis: Boolean,
    val mensagemAviso: String? = null,
    val unidades: List<UnidadeNotaTurmaDto>,
    val mediaFinal: String? = null,
    val faltas: Int? = null,
    val situacao: String? = null,
)

@Serializable
data class AvaliacaoTurmaDto(val dataTexto: String, val horaTexto: String? = null, val descricao: String)

@Serializable
data class TarefaTurmaDto(
    val titulo: String,
    val periodoEntrega: String,
    val notaMaxima: String? = null,
    val possuiArquivo: Boolean,
    val emGrupo: Boolean,
)

@Serializable
data class PerfilDiscenteDto(
    val idDiscente: String,
    val matricula: String,
    val nomeCompleto: String,
    val nomeAbreviado: String,
    val bioPerfil: String? = null,
    val fotoUrl: String? = null,
    val semestreAtual: String,
    val semestreEntrada: String,
    val unidadeDepartamento: String,
    val curso: String,
    val nivel: String,
    val status: String,
    val emailTruncado: String? = null,
    val idCursoPortalPublico: String? = null,
)

@Serializable
data class IndicesIntegralizacaoDto(
    val ira: Double? = null,
    val mp: Double? = null,
    val chObrigatoriaPendente: Int? = null,
    val chOptativaPendente: Int? = null,
    val chComplementarPendente: Int? = null,
    val chTotalCurriculo: Int? = null,
    val percentualIntegralizadoExato: Double? = null,
    val percentualIntegralizadoTexto: String? = null,
)

@Serializable
data class DiscenteResumoDto(val perfil: PerfilDiscenteDto, val indices: IndicesIntegralizacaoDto)

@Serializable
data class CarteirinhaEstudantilDto(
    val nomeCompleto: String,
    val matricula: String,
    val cpfTexto: String? = null,
    val curso: String,
    val nivel: String,
    val validadeTexto: String,
    val qrPayload16Digitos: String,
    val qrImageLocalPath: String,
    val foto3x4LocalPath: String? = null,
    val pdfLocalPath: String,
)

@Serializable
data class NotaDisciplinaBoletimDto(
    val codigo: String,
    val disciplina: String,
    val nota1: String? = null,
    val resultadoMencao: String? = null,
    val faltas: Int? = null,
    val situacao: String,
)

@Serializable
data class BoletimSemestreDto(val semestre: String, val disciplinas: List<NotaDisciplinaBoletimDto>)

@Serializable
data class DocumentoOficialEmitidoDto(
    val tipo: String,
    val titulo: String,
    val codigoVerificacao: String? = null,
    val mimeType: String,
    val localFilePath: String,
    val emitidoEmEpochMillis: Long,
)

internal object CacheMappers {
    val noticiaRuListSerializer = ListSerializer(NoticiaRuDto.serializer())
    val turmaResumoListSerializer = ListSerializer(TurmaResumoDto.serializer())
    val feedListSerializer = ListSerializer(AtualizacaoTurmaFeedDto.serializer())
    val avaliacaoListSerializer = ListSerializer(AvaliacaoTurmaDto.serializer())
    val tarefaListSerializer = ListSerializer(TarefaTurmaDto.serializer())
    val boletimListSerializer = ListSerializer(BoletimSemestreDto.serializer())
    val documentoListSerializer = ListSerializer(DocumentoOficialEmitidoDto.serializer())

    fun toDto(m: CardapioSemanalRu) = CardapioSemanalRuDto(
        campus = m.campus.name,
        semanaLabel = m.semanaLabel,
        pdfUrl = m.pdfUrl,
        fallbackImageUrl = m.fallbackImageUrl,
        localPdfPath = m.localPdfPath,
        modifiedGmt = m.modifiedGmt,
        parserStatus = m.parserStatus.name,
        dias = m.dias.map { d ->
            CardapioDiaRuDto(
                dataTexto = d.dataTexto,
                diaSemana = d.diaSemana,
                refeicoes = d.refeicoes.entries.associate { (k, v) ->
                    k.name to v.map { item ->
                        ItemCardapioRuDto(item.categoria, item.prato, item.alergenicos.map { it.name })
                    }
                },
            )
        },
    )

    fun fromDto(d: CardapioSemanalRuDto) = CardapioSemanalRu(
        campus = CampusRu.valueOf(d.campus),
        semanaLabel = d.semanaLabel,
        pdfUrl = d.pdfUrl,
        fallbackImageUrl = d.fallbackImageUrl,
        localPdfPath = d.localPdfPath,
        modifiedGmt = d.modifiedGmt,
        parserStatus = CardapioParserStatus.valueOf(d.parserStatus),
        dias = d.dias.map { day ->
            CardapioDiaRu(
                dataTexto = day.dataTexto,
                diaSemana = day.diaSemana,
                refeicoes = day.refeicoes.entries.associate { (k, v) ->
                    TipoRefeicao.valueOf(k) to v.map { item ->
                        ItemCardapioRu(
                            categoria = item.categoria,
                            prato = item.prato,
                            alergenicos = item.alergenicos.mapNotNull { id ->
                                Alergenico.entries.firstOrNull { it.name == id }
                            },
                        )
                    }
                },
            )
        },
    )

    fun toDto(m: SaldoExtratoRu) = SaldoExtratoRuDto(
        saldoAtualCentavos = m.saldoAtualCentavos,
        saldoAnterior7DiasCentavos = m.saldoAnterior7DiasCentavos,
        grupoAcesso = m.grupoAcesso.name,
        transacoesUltimos7Dias = m.transacoesUltimos7Dias.map { TransacaoRuDto(it.dataHoraTexto, it.descricao, it.valorCentavos) },
        refeicoesRestantes = m.refeicoesRestantes?.let {
            RefeicoesRestantesDto(it.cafesRestantes, it.almocosOuJantaresRestantes, it.trocoCafeCentavos, it.trocoAlmocoJantarCentavos, it.gratuito)
        },
    )

    fun fromDto(d: SaldoExtratoRuDto) = SaldoExtratoRu(
        saldoAtualCentavos = d.saldoAtualCentavos,
        saldoAnterior7DiasCentavos = d.saldoAnterior7DiasCentavos,
        grupoAcesso = GrupoAcessoRu.valueOf(d.grupoAcesso),
        transacoesUltimos7Dias = d.transacoesUltimos7Dias.map { TransacaoRu(it.dataHoraTexto, it.descricao, it.valorCentavos) },
        refeicoesRestantes = d.refeicoesRestantes?.let {
            RefeicoesRestantes(it.cafesRestantes, it.almocosOuJantaresRestantes, it.trocoCafeCentavos, it.trocoAlmocoJantarCentavos, it.gratuito)
        },
    )

    fun toDto(m: TabelaPrecosRu) = TabelaPrecosRuDto(
        resolucaoReferencia = m.resolucaoReferencia,
        modifiedGmt = m.modifiedGmt,
        grupos = m.grupos.map { PrecoGrupoRuDto(it.grupo.name, it.publicoAlvo, it.cafeCentavos, it.almocoCentavos, it.jantarCentavos, it.porKgCentavos) },
        formasPagamento = m.formasPagamento,
    )

    fun fromDto(d: TabelaPrecosRuDto) = TabelaPrecosRu(
        resolucaoReferencia = d.resolucaoReferencia,
        modifiedGmt = d.modifiedGmt,
        grupos = d.grupos.map { PrecoGrupoRu(GrupoAcessoRu.valueOf(it.grupo), it.publicoAlvo, it.cafeCentavos, it.almocoCentavos, it.jantarCentavos, it.porKgCentavos) },
        formasPagamento = d.formasPagamento,
    )

    fun toDto(m: NoticiaRu) = NoticiaRuDto(m.id, m.dateIso, m.title, m.excerptText, m.contentHtml, m.link, m.featuredImageUrl, m.anexoPdfUrls)
    fun fromDto(d: NoticiaRuDto) = NoticiaRu(d.id, d.dateIso, d.title, d.excerptText, d.contentHtml, d.link, d.featuredImageUrl, d.anexoPdfUrls)

    fun toDto(m: TurmaResumo) = TurmaResumoDto(
        idTurma = m.idTurma,
        frontEndIdTurma = m.frontEndIdTurma,
        semestre = m.semestre,
        codigoDisciplina = m.codigoDisciplina,
        nomeDisciplina = m.nomeDisciplina,
        numeroTurma = m.numeroTurma,
        cargaHorariaHoras = m.cargaHorariaHoras,
        docenteNome = m.docenteNome,
        local = m.local,
        codigoHorarioRaw = m.codigoHorarioRaw,
        horarioSlots = m.horarioSlots.map {
            HorarioSlotDto(it.diaSemana.name, it.turno.name, it.tempoNumero, it.inicio, it.fim, it.codigoOriginal)
        },
        horarioExtenso = m.horarioExtenso,
    )

    fun fromDto(d: TurmaResumoDto) = TurmaResumo(
        idTurma = d.idTurma,
        frontEndIdTurma = d.frontEndIdTurma,
        semestre = d.semestre,
        codigoDisciplina = d.codigoDisciplina,
        nomeDisciplina = d.nomeDisciplina,
        numeroTurma = d.numeroTurma,
        cargaHorariaHoras = d.cargaHorariaHoras,
        docenteNome = d.docenteNome,
        local = d.local,
        codigoHorarioRaw = d.codigoHorarioRaw,
        horarioSlots = d.horarioSlots.map {
            HorarioSlot(DiaSemanaSigaa.valueOf(it.diaSemana), TurnoSigaa.valueOf(it.turno), it.tempoNumero, it.inicio, it.fim, it.codigoOriginal)
        },
        horarioExtenso = d.horarioExtenso,
    )

    fun toDto(m: AtualizacaoTurmaFeed) = AtualizacaoTurmaFeedDto(m.dataTexto, m.disciplinaSemestre, m.idTurma, m.tituloNoticia)
    fun fromDto(d: AtualizacaoTurmaFeedDto) = AtualizacaoTurmaFeed(d.dataTexto, d.disciplinaSemestre, d.idTurma, d.tituloNoticia)

    fun toDto(m: TurmaDetalheAva) = TurmaDetalheAvaDto(
        idTurma = m.idTurma,
        frontEndIdTurma = m.frontEndIdTurma,
        codigoDisciplina = m.codigoDisciplina,
        nomeDisciplina = m.nomeDisciplina,
        periodoTurmaTexto = m.periodoTurmaTexto,
        aulasMinistradas = m.aulasMinistradas,
        aulasTotal = m.aulasTotal,
        percentualAndamento = m.percentualAndamento,
        ultimaNoticia = m.ultimaNoticia?.let(::toDto),
        historicoNoticiasResumo = m.historicoNoticiasResumo.map { NoticiaTurmaResumoAvaDto(it.idNoticia, it.dataHoraTexto, it.titulo) },
        topicosAula = m.topicosAula.map { t ->
            TopicoAulaAvaDto(
                index = t.index,
                titulo = t.titulo,
                periodoTexto = t.periodoTexto,
                conteudoTexto = t.conteudoTexto,
                materiais = t.materiais.map { MaterialAulaAvaDto(it.materialId, it.nome, it.tipoExtensao, it.topicoIndex) },
            )
        },
    )

    fun toDto(n: NoticiaTurmaAva) = NoticiaTurmaAvaDto(
        idNoticia = n.idNoticia,
        titulo = n.titulo,
        dataHoraTexto = n.dataHoraTexto,
        conteudoHtml = n.conteudoHtml,
        conteudoTexto = n.conteudoTexto,
        autor = n.autor,
        anexos = n.anexos.map { AnexoArquivoSigaaDto(it.id, it.nomeArquivo) },
    )

    fun fromDto(n: NoticiaTurmaAvaDto) = NoticiaTurmaAva(
        idNoticia = n.idNoticia,
        titulo = n.titulo,
        dataHoraTexto = n.dataHoraTexto,
        conteudoHtml = n.conteudoHtml,
        conteudoTexto = n.conteudoTexto,
        autor = n.autor,
        anexos = n.anexos.map { AnexoArquivoSigaa(it.id, it.nomeArquivo) },
    )

    fun fromDto(d: TurmaDetalheAvaDto) = TurmaDetalheAva(
        idTurma = d.idTurma,
        frontEndIdTurma = d.frontEndIdTurma,
        codigoDisciplina = d.codigoDisciplina,
        nomeDisciplina = d.nomeDisciplina,
        periodoTurmaTexto = d.periodoTurmaTexto,
        aulasMinistradas = d.aulasMinistradas,
        aulasTotal = d.aulasTotal,
        percentualAndamento = d.percentualAndamento,
        ultimaNoticia = d.ultimaNoticia?.let(::fromDto),
        historicoNoticiasResumo = d.historicoNoticiasResumo.map { NoticiaTurmaResumoAva(it.idNoticia, it.dataHoraTexto, it.titulo) },
        topicosAula = d.topicosAula.map { t ->
            TopicoAulaAva(
                index = t.index,
                titulo = t.titulo,
                periodoTexto = t.periodoTexto,
                conteudoTexto = t.conteudoTexto,
                materiais = t.materiais.map { MaterialAulaAva(it.materialId, it.nome, it.tipoExtensao, it.topicoIndex) },
            )
        },
    )

    fun toDto(m: MapaFrequenciaTurma) = MapaFrequenciaTurmaDto(
        m.presencasRegistradas,
        m.aulasComRegistro,
        m.percentualSobreRegistradas,
        m.aulasDefinidasCh,
        m.percentualSobreCh,
        m.faltasComputadas,
        m.limiteMaximoFaltas,
        m.faltasRestantes,
        m.emRiscoReprovacaoPorFalta,
        m.registrosDiarios.map { RegistroFrequenciaDiaDto(it.dataTexto, it.situacaoTexto, it.faltasNoDia, it.presente) },
    )

    fun fromDto(d: MapaFrequenciaTurmaDto) = MapaFrequenciaTurma(
        d.presencasRegistradas,
        d.aulasComRegistro,
        d.percentualSobreRegistradas,
        d.aulasDefinidasCh,
        d.percentualSobreCh,
        d.faltasComputadas,
        d.limiteMaximoFaltas,
        d.faltasRestantes,
        d.emRiscoReprovacaoPorFalta,
        d.registrosDiarios.map { RegistroFrequenciaDia(it.dataTexto, it.situacaoTexto, it.faltasNoDia, it.presente) },
    )

    fun toDto(m: NotasParciaisTurma) = NotasParciaisTurmaDto(
        m.notasDisponiveis,
        m.mensagemAviso,
        m.unidades.map { UnidadeNotaTurmaDto(it.nome, it.valor) },
        m.mediaFinal,
        m.faltas,
        m.situacao,
    )

    fun fromDto(d: NotasParciaisTurmaDto) = NotasParciaisTurma(
        d.notasDisponiveis,
        d.mensagemAviso,
        d.unidades.map { UnidadeNotaTurma(it.nome, it.valor) },
        d.mediaFinal,
        d.faltas,
        d.situacao,
    )

    fun toDto(m: AvaliacaoTurma) = AvaliacaoTurmaDto(m.dataTexto, m.horaTexto, m.descricao)
    fun fromDto(d: AvaliacaoTurmaDto) = AvaliacaoTurma(d.dataTexto, d.horaTexto, d.descricao)

    fun toDto(m: TarefaTurma) = TarefaTurmaDto(m.titulo, m.periodoEntrega, m.notaMaxima, m.possuiArquivo, m.emGrupo)
    fun fromDto(d: TarefaTurmaDto) = TarefaTurma(d.titulo, d.periodoEntrega, d.notaMaxima, d.possuiArquivo, d.emGrupo)

    fun toDto(m: DiscenteResumo) = DiscenteResumoDto(
        perfil = PerfilDiscenteDto(
            m.perfil.idDiscente,
            m.perfil.matricula,
            m.perfil.nomeCompleto,
            m.perfil.nomeAbreviado,
            m.perfil.bioPerfil,
            m.perfil.fotoUrl,
            m.perfil.semestreAtual,
            m.perfil.semestreEntrada,
            m.perfil.unidadeDepartamento,
            m.perfil.curso,
            m.perfil.nivel,
            m.perfil.status,
            m.perfil.emailTruncado,
            m.perfil.idCursoPortalPublico,
        ),
        indices = IndicesIntegralizacaoDto(
            m.indices.ira,
            m.indices.mp,
            m.indices.chObrigatoriaPendente,
            m.indices.chOptativaPendente,
            m.indices.chComplementarPendente,
            m.indices.chTotalCurriculo,
            m.indices.percentualIntegralizadoExato,
            m.indices.percentualIntegralizadoTexto,
        ),
    )

    fun fromDto(d: DiscenteResumoDto) = DiscenteResumo(
        perfil = PerfilDiscente(
            d.perfil.idDiscente,
            d.perfil.matricula,
            d.perfil.nomeCompleto,
            d.perfil.nomeAbreviado,
            d.perfil.bioPerfil,
            d.perfil.fotoUrl,
            d.perfil.semestreAtual,
            d.perfil.semestreEntrada,
            d.perfil.unidadeDepartamento,
            d.perfil.curso,
            d.perfil.nivel,
            d.perfil.status,
            d.perfil.emailTruncado,
            d.perfil.idCursoPortalPublico,
        ),
        indices = IndicesIntegralizacao(
            d.indices.ira,
            d.indices.mp,
            d.indices.chObrigatoriaPendente,
            d.indices.chOptativaPendente,
            d.indices.chComplementarPendente,
            d.indices.chTotalCurriculo,
            d.indices.percentualIntegralizadoExato,
            d.indices.percentualIntegralizadoTexto,
        ),
    )

    fun toDto(m: CarteirinhaEstudantil) = CarteirinhaEstudantilDto(
        m.nomeCompleto,
        m.matricula,
        m.cpfTexto,
        m.curso,
        m.nivel,
        m.validadeTexto,
        m.qrPayload16Digitos,
        m.qrImageLocalPath,
        m.foto3x4LocalPath,
        m.pdfLocalPath,
    )

    fun fromDto(d: CarteirinhaEstudantilDto) = CarteirinhaEstudantil(
        d.nomeCompleto,
        d.matricula,
        d.cpfTexto,
        d.curso,
        d.nivel,
        d.validadeTexto,
        d.qrPayload16Digitos,
        d.qrImageLocalPath,
        d.foto3x4LocalPath,
        d.pdfLocalPath,
    )

    fun toDto(m: BoletimSemestre) = BoletimSemestreDto(
        m.semestre,
        m.disciplinas.map { NotaDisciplinaBoletimDto(it.codigo, it.disciplina, it.nota1, it.resultadoMencao, it.faltas, it.situacao) },
    )

    fun fromDto(d: BoletimSemestreDto) = BoletimSemestre(
        d.semestre,
        d.disciplinas.map { NotaDisciplinaBoletim(it.codigo, it.disciplina, it.nota1, it.resultadoMencao, it.faltas, it.situacao) },
    )

    fun toDto(m: DocumentoOficialEmitido) = DocumentoOficialEmitidoDto(
        m.tipo.name,
        m.titulo,
        m.codigoVerificacao,
        m.mimeType,
        m.localFilePath,
        m.emitidoEmEpochMillis,
    )

    fun fromDto(d: DocumentoOficialEmitidoDto) = DocumentoOficialEmitido(
        TipoDocumentoOficial.valueOf(d.tipo),
        d.titulo,
        d.codigoVerificacao,
        d.mimeType,
        d.localFilePath,
        d.emitidoEmEpochMillis,
    )
}

@Serializable
data class RuAutoSyncMetaDto(
    val lastCheckEpochMillis: Long,
)
