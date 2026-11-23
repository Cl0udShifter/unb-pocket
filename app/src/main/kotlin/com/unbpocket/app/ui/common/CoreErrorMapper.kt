package com.unbpocket.app.ui.common

import com.unbpocket.core.model.auth.AuthState
import com.unbpocket.core.model.common.CoreError

data class UiErrorBanner(
    val title: String,
    val message: String,
    val requiresLogin: Boolean = false,
    val canRetry: Boolean = false,
    val isWarning: Boolean = false,
)

internal fun UiErrorBanner?.withoutStaleAuth(authState: AuthState): UiErrorBanner? =
    if (authState is AuthState.Authenticated && this?.requiresLogin == true) null else this

object CoreErrorMapper {
    fun toUiError(error: CoreError): UiErrorBanner = when (error) {
        CoreError.InvalidCredentials -> UiErrorBanner(
            title = "Credenciais recusadas",
            message = "Matrícula ou senha inválida no CAS da UnB. Confira os dados e tente novamente.",
            requiresLogin = true,
            canRetry = false,
        )

        CoreError.SessionExpired -> UiErrorBanner(
            title = "Sessão expirada",
            message = "Sua sessão do SIGAA expirou (40 min). Entre novamente para sincronizar.",
            requiresLogin = true,
            canRetry = false,
            isWarning = true,
        )

        CoreError.NoStoredCredentials -> UiErrorBanner(
            title = "Conta SIGAA desconectada",
            message = "Entre com sua matrícula e senha do SIGAA para acessar estes dados.",
            requiresLogin = true,
            canRetry = false,
            isWarning = true,
        )

        CoreError.NetworkUnavailable -> UiErrorBanner(
            title = "Sem conexão",
            message = "Não foi possível conectar agora. Os dados já salvos continuam disponíveis offline.",
            requiresLogin = false,
            canRetry = true,
            isWarning = true,
        )

        is CoreError.HttpFailure -> UiErrorBanner(
            title = "Portal indisponível (${error.statusCode})",
            message = error.message?.takeIf { it.isNotBlank() }
                ?: "Falha ao consultar ${error.endpoint}. Tente novamente em instantes.",
            requiresLogin = false,
            canRetry = true,
        )

        is CoreError.ParseError -> UiErrorBanner(
            title = if (error.recoverableFallbackAvailable) "Leitura parcial do portal" else "Layout alterado",
            message = if (error.recoverableFallbackAvailable) {
                "O layout de ${error.source} mudou; exibindo versão de contingência."
            } else {
                "Não foi possível interpretar ${error.source}: ${error.reason}"
            },
            requiresLogin = false,
            canRetry = true,
            isWarning = error.recoverableFallbackAvailable,
        )

        is CoreError.ReadOnlyViolation -> UiErrorBanner(
            title = "Bloqueio somente leitura",
            message = "Operação bloqueada por segurança (${error.blockedTarget}): ${error.reason}",
            requiresLogin = false,
            canRetry = false,
        )

        is CoreError.PdfExtractionError -> UiErrorBanner(
            title = "Falha ao ler documento",
            message = "Não foi possível extrair ${error.documentType}: ${error.reason}",
            requiresLogin = false,
            canRetry = true,
        )

        is CoreError.StorageError -> UiErrorBanner(
            title = "Erro de armazenamento local",
            message = "Falha em ${error.operation}: ${error.reason}",
            requiresLogin = false,
            canRetry = true,
        )

        is CoreError.UnavailableForProfile -> UiErrorBanner(
            title = "${error.feature} indisponível",
            message = error.reason,
            requiresLogin = false,
            canRetry = false,
            isWarning = true,
        )

        is CoreError.Unexpected -> UiErrorBanner(
            title = "Erro inesperado",
            message = error.message.ifBlank { "Ocorreu uma falha não classificada." },
            requiresLogin = false,
            canRetry = true,
        )
    }
}
