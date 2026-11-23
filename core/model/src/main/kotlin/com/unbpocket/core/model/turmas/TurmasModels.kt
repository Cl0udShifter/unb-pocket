package com.unbpocket.core.model.turmas

enum class DiaSemanaSigaa(val digito: Int, val sigla: String, val nomeCompleto: String) {
    SEGUNDA(2, "SEG", "Segunda-feira"),
    TERCA(3, "TER", "Terça-feira"),
    QUARTA(4, "QUA", "Quarta-feira"),
    QUINTA(5, "QUI", "Quinta-feira"),
    SEXTA(6, "SEX", "Sexta-feira"),
    SABADO(7, "SAB", "Sábado"),
}

enum class TurnoSigaa(val letra: Char, val labelPtBr: String) {
    MANHA('M', "Manhã"),
    TARDE('T', "Tarde"),
    NOITE('N', "Noite"),
}

data class HorarioSlot(
    val diaSemana: DiaSemanaSigaa,
    val turno: TurnoSigaa,
    val tempoNumero: Int,
    val inicio: String,
    val fim: String,
    val codigoOriginal: String,
)

data class TurmaResumo(
    val idTurma: String,
    val frontEndIdTurma: String,
    val semestre: String,
    val codigoDisciplina: String?,
    val nomeDisciplina: String,
    val numeroTurma: String?,
    val cargaHorariaHoras: Int?,
    val docenteNome: String?,
    val local: String,
    val codigoHorarioRaw: String,
    val horarioSlots: List<HorarioSlot>,
    val horarioExtenso: List<String>,
)

data class AtualizacaoTurmaFeed(
    val dataTexto: String,
    val disciplinaSemestre: String,
    val idTurma: String,
    val tituloNoticia: String,
)

data class AnexoArquivoSigaa(
    val id: String,
    val nomeArquivo: String,
)

data class NoticiaTurmaResumoAva(
    val idNoticia: String,
    val dataHoraTexto: String,
    val titulo: String,
)

data class NoticiaTurmaAva(
    val idNoticia: String?,
    val titulo: String,
    val dataHoraTexto: String,
    val conteudoHtml: String,
    val conteudoTexto: String,
    val autor: String?,
    val anexos: List<AnexoArquivoSigaa>,
)

data class MaterialAulaAva(
    val materialId: String,
    val nome: String,
    val tipoExtensao: String,
    val topicoIndex: Int,
)

data class TopicoAulaAva(
    val index: Int,
    val titulo: String,
    val periodoTexto: String?,
    val conteudoTexto: String,
    val materiais: List<MaterialAulaAva>,
)

data class TurmaDetalheAva(
    val idTurma: String,
    val frontEndIdTurma: String,
    val codigoDisciplina: String,
    val nomeDisciplina: String,
    val periodoTurmaTexto: String,
    val aulasMinistradas: Int,
    val aulasTotal: Int,
    val percentualAndamento: Int,
    val ultimaNoticia: NoticiaTurmaAva?,
    val historicoNoticiasResumo: List<NoticiaTurmaResumoAva>,
    val topicosAula: List<TopicoAulaAva>,
)

data class RegistroFrequenciaDia(
    val dataTexto: String,
    val situacaoTexto: String,
    val faltasNoDia: Int,
    val presente: Boolean,
)

data class MapaFrequenciaTurma(
    val presencasRegistradas: Int,
    val aulasComRegistro: Int,
    val percentualSobreRegistradas: Int,
    val aulasDefinidasCh: Int,
    val percentualSobreCh: Int,
    val faltasComputadas: Int,
    val limiteMaximoFaltas: Int,
    val faltasRestantes: Int,
    val emRiscoReprovacaoPorFalta: Boolean,
    val registrosDiarios: List<RegistroFrequenciaDia>,
)

data class UnidadeNotaTurma(
    val nome: String,
    val valor: String?,
)

data class NotasParciaisTurma(
    val notasDisponiveis: Boolean,
    val mensagemAviso: String?,
    val unidades: List<UnidadeNotaTurma>,
    val mediaFinal: String?,
    val faltas: Int?,
    val situacao: String?,
)

data class AvaliacaoTurma(
    val dataTexto: String,
    val horaTexto: String?,
    val descricao: String,
)

data class TarefaTurma(
    val titulo: String,
    val periodoEntrega: String,
    val notaMaxima: String?,
    val possuiArquivo: Boolean,
    val emGrupo: Boolean,
)

data class ArquivoBaixado(
    val nomeArquivo: String,
    val mimeType: String,
    val localFilePath: String,
    val tamanhoBytes: Long,
)
