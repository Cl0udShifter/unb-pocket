package com.unbpocket.core.model.discente

data class PerfilDiscente(
    val idDiscente: String,
    val matricula: String,
    val nomeCompleto: String,
    val nomeAbreviado: String,
    val bioPerfil: String?,
    val fotoUrl: String?,
    val semestreAtual: String,
    val semestreEntrada: String,
    val unidadeDepartamento: String,
    val curso: String,
    val nivel: String,
    val status: String,
    val emailTruncado: String?,
    val idCursoPortalPublico: String?,
)

data class IndicesIntegralizacao(
    val ira: Double?,
    val mp: Double?,
    val chObrigatoriaPendente: Int?,
    val chOptativaPendente: Int?,
    val chComplementarPendente: Int?,
    val chTotalCurriculo: Int?,
    val percentualIntegralizadoExato: Double?,
    val percentualIntegralizadoTexto: String?,
)

data class DiscenteResumo(
    val perfil: PerfilDiscente,
    val indices: IndicesIntegralizacao,
)

data class CarteirinhaEstudantil(
    val nomeCompleto: String,
    val matricula: String,
    val cpfTexto: String?,
    val curso: String,
    val nivel: String,
    val validadeTexto: String,
    val qrPayload16Digitos: String,
    val qrImageLocalPath: String,
    val foto3x4LocalPath: String?,
    val pdfLocalPath: String,
)

data class NotaDisciplinaBoletim(
    val codigo: String,
    val disciplina: String,
    val nota1: String?,
    val resultadoMencao: String?,
    val faltas: Int?,
    val situacao: String,
)

data class BoletimSemestre(
    val semestre: String,
    val disciplinas: List<NotaDisciplinaBoletim>,
)

enum class TipoDocumentoOficial(val displayName: String, val mimeType: String) {
    ATESTADO_MATRICULA("Atestado de Matrícula", "text/html"),
    DECLARACAO_VINCULO("Declaração de Vínculo", "application/pdf"),
    HISTORICO_ESCOLAR("Histórico Escolar Oficial", "application/pdf"),
    PASSE_LIVRE_ESTUDANTIL("Declaração Passe Livre Estudantil", "text/html"),
    CARTEIRINHA_ESTUDANTIL("Carteirinha Estudantil", "application/pdf"),
}

data class DocumentoOficialEmitido(
    val tipo: TipoDocumentoOficial,
    val titulo: String,
    val codigoVerificacao: String?,
    val mimeType: String,
    val localFilePath: String,
    val emitidoEmEpochMillis: Long,
)
