package com.unbpocket.core.model.auth

/**
 * Credenciais fornecidas pelo discente para autenticacao no CAS da UnB.
 */
data class CredentialsInput(
    val matricula: String,
    val password: String,
    val rememberCredentials: Boolean = true,
)

/**
 * Estado reativo da sessao autenticada no SIGAA/CAS.
 */
sealed interface AuthState {
    data object Unauthenticated : AuthState

    data class Reauthenticating(
        val matricula: String,
    ) : AuthState

    data class Authenticated(
        val matricula: String,
        val idDiscente: String?,
        val sessionExpiresAtEpochMillis: Long?,
        val hasStoredCredentials: Boolean,
    ) : AuthState
}
