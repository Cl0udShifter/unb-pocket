package com.unbpocket.core.model.common

/**
 * Origem de um dado entregue pelo Core para a camada de UI.
 */
enum class DataOrigin {
    CACHE,
    REMOTE,
    FALLBACK,
}

/**
 * Snapshot sincronizado com metadados de frescor e eventual aviso nao-fatal.
 */
data class SyncedData<out T>(
    val value: T,
    val origin: DataOrigin,
    val syncedAtEpochMillis: Long,
    val staleWarning: CoreError? = null,
)

/**
 * Resumo da sincronizacao unica da tela inicial do SIGAA (`discente.jsf`).
 */
data class PortalHomeSyncSummary(
    val syncedAtEpochMillis: Long,
    val turmasCount: Int,
    val atualizacoesCount: Int,
    val saldoRuAtualizado: Boolean,
)

/**
 * Erros explicitos tratados e expostos pelo Core para a camada de UI.
 */
sealed interface CoreError {
    data object InvalidCredentials : CoreError

    data object SessionExpired : CoreError

    data object NoStoredCredentials : CoreError

    data object NetworkUnavailable : CoreError

    data class HttpFailure(
        val statusCode: Int,
        val endpoint: String,
        val message: String? = null,
    ) : CoreError

    data class ParseError(
        val source: String,
        val reason: String,
        val recoverableFallbackAvailable: Boolean = false,
    ) : CoreError

    data class ReadOnlyViolation(
        val blockedTarget: String,
        val reason: String,
    ) : CoreError

    data class PdfExtractionError(
        val documentType: String,
        val reason: String,
    ) : CoreError

    data class StorageError(
        val operation: String,
        val reason: String,
    ) : CoreError

    data class UnavailableForProfile(
        val feature: String,
        val reason: String,
    ) : CoreError

    data class Unexpected(
        val message: String,
    ) : CoreError
}

/**
 * Resultado tipado para operacoes assincronas expostas pelo Core.
 */
sealed interface CoreResult<out T> {
    data class Success<out T>(val value: T) : CoreResult<T>

    data class Failure(val error: CoreError) : CoreResult<Nothing>
}

inline fun <T, R> CoreResult<T>.map(transform: (T) -> R): CoreResult<R> = when (this) {
    is CoreResult.Success -> CoreResult.Success(transform(value))
    is CoreResult.Failure -> this
}
