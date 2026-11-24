package com.unbpocket.core.data.session

import com.unbpocket.core.data.local.CredentialVault
import com.unbpocket.core.data.local.FileStore
import com.unbpocket.core.data.local.SnapshotCacheStore
import com.unbpocket.core.model.auth.AuthState
import com.unbpocket.core.model.auth.CredentialsInput
import com.unbpocket.core.model.common.CoreError
import com.unbpocket.core.model.common.CoreResult
import com.unbpocket.core.model.contract.AuthRepository
import com.unbpocket.core.network.HttpPayload
import com.unbpocket.core.network.JsfPriority
import com.unbpocket.core.network.UnbHttpTransport
import com.unbpocket.core.parser.CasAndJsfParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coordenador de autenticacao Apereo CAS SSO e navegacao JSF serializada.
 * Garante o lock unico (`withJsfLock`) e re-autenticacao automatica em expiracoes de 40 min.
 */
class SigaaSessionCoordinator(
    private val transport: UnbHttpTransport,
    private val vault: CredentialVault,
    private val cacheStore: SnapshotCacheStore,
    private val fileStore: FileStore,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
    private val onLogoutHook: suspend (clearCachedData: Boolean) -> Unit = {},
    private val diagLog: (String) -> Unit = {},
) : AuthRepository {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    @Volatile
    private var freshLoginPortalHtml: String? = null

    @Volatile
    private var freshLoginCapturedAtMillis: Long = 0L

    fun consumeFreshLoginPortalHtml(): String? {
        val html = freshLoginPortalHtml ?: return null
        val capturedAt = freshLoginCapturedAtMillis
        freshLoginPortalHtml = null
        freshLoginCapturedAtMillis = 0L
        return if (clockMillis() - capturedAt <= FRESH_HTML_TTL_MILLIS) html else null
    }

    override suspend fun login(credentials: CredentialsInput): CoreResult<AuthState.Authenticated> =
        transport.session.withJsfLock(JsfPriority.INTERACTIVE) {
            loginLocked(credentials)
        }

    override suspend fun ensureValidSession(): CoreResult<AuthState.Authenticated> {
        val current = _authState.value
        val now = clockMillis()
        val expiresAt = (current as? AuthState.Authenticated)?.sessionExpiresAtEpochMillis
        if (current is AuthState.Authenticated &&
            expiresAt != null &&
            now < expiresAt - SAFETY_MARGIN_MILLIS
        ) {
            return CoreResult.Success(current)
        }

        return transport.session.withJsfLock(JsfPriority.INTERACTIVE) {
            ensureValidSessionLocked()
        }
    }

    override suspend fun logout(clearCachedData: Boolean): CoreResult<Unit> = runCatching {
        onLogoutHook(clearCachedData)
        freshLoginPortalHtml = null
        freshLoginCapturedAtMillis = 0L
        transport.session.cookieJar.clear()
        vault.clear()
        if (clearCachedData) {
            cacheStore.clearSigaaUserData()
            fileStore.clearSigaaUserFiles()
        }
        _authState.value = AuthState.Unauthenticated
    }.fold(
        onSuccess = { CoreResult.Success(Unit) },
        onFailure = { error ->
            CoreResult.Failure(
                CoreError.StorageError("logout", error.message ?: "Falha ao limpar sessao"),
            )
        },
    )

    override suspend fun hasSavedCredentials(): Boolean = vault.exists()

    /**
     * Executa uma navegacao JSF autenticado sob `withJsfLock`, tentando uma unica
     * re-autenticacao silenciosa se o servidor redirecionar para o CAS no meio da sessao.
     */
    suspend fun <T> withAuthenticatedJsf(
        priority: JsfPriority = JsfPriority.INTERACTIVE,
        block: suspend (SigaaNavigator) -> CoreResult<T>,
    ): CoreResult<T> = transport.session.withJsfLock(priority) {
        when (val sessionCheck = ensureValidSessionLocked()) {
            is CoreResult.Failure -> return@withJsfLock sessionCheck
            is CoreResult.Success -> Unit
        }

        val navigator = SigaaNavigator(transport, clockMillis, ::updateSessionTouch)
        val firstAttempt = block(navigator)
        if (firstAttempt is CoreResult.Failure && firstAttempt.error is CoreError.SessionExpired) {
            val saved = vault.load()
            if (saved == null) {
                _authState.value = AuthState.Unauthenticated
                return@withJsfLock firstAttempt
            }
            when (val relogin = loginLocked(CredentialsInput(saved.matricula, saved.password, rememberCredentials = true))) {
                is CoreResult.Failure -> return@withJsfLock relogin
                is CoreResult.Success -> Unit
            }
            return@withJsfLock block(navigator)
        }
        firstAttempt
    }

    private suspend fun ensureValidSessionLocked(): CoreResult<AuthState.Authenticated> {
        val current = _authState.value
        val now = clockMillis()
        val expiresAt = (current as? AuthState.Authenticated)?.sessionExpiresAtEpochMillis
        if (current is AuthState.Authenticated &&
            expiresAt != null &&
            now < expiresAt - SAFETY_MARGIN_MILLIS
        ) {
            return CoreResult.Success(current)
        }

        val saved = vault.load()
        if (saved == null) {
            diagLog("ensureValidSessionLocked: vault.exists=${vault.exists()} vault.load=null")
            return CoreResult.Failure(
                if (current is AuthState.Authenticated) CoreError.SessionExpired else CoreError.NoStoredCredentials,
            )
        }

        val loginRes = loginLocked(
            CredentialsInput(
                matricula = saved.matricula,
                password = saved.password,
                rememberCredentials = true,
            ),
        )
        diagLog("ensureValidSessionLocked: loginLocked=${loginRes is CoreResult.Success}")
        return loginRes
    }

    private suspend fun loginLocked(credentials: CredentialsInput): CoreResult<AuthState.Authenticated> {
        if (credentials.matricula.isBlank() || credentials.password.isBlank()) {
            return CoreResult.Failure(CoreError.InvalidCredentials)
        }

        _authState.value = AuthState.Reauthenticating(credentials.matricula.trim())
        transport.session.cookieJar.clear()

        val entryResult = transport.get(transport.endpoints.discentePortalUrl)
        val entryPayload = when (entryResult) {
            is CoreResult.Failure -> {
                _authState.value = AuthState.Unauthenticated
                return entryResult
            }
            is CoreResult.Success -> entryResult.value
        }

        val entryHtml = entryPayload.decodeSigaaIso88591()
        val portalHtml = if (CasAndJsfParser.isCasLoginPage(entryHtml)) {
            val casForm = CasAndJsfParser.parseCasLoginForm(entryHtml)
            if (casForm == null) {
                _authState.value = AuthState.Unauthenticated
                return CoreResult.Failure(
                    CoreError.ParseError("cas/login", "Formulario de login CAS (#login-form) invalido."),
                )
            }

            val postUrl = entryPayload.resolveRelativeUrl(casForm.action)
            val fields = linkedMapOf(
                "username" to credentials.matricula.trim(),
                "password" to credentials.password,
                "lt" to casForm.lt,
                "execution" to casForm.execution,
                "_eventId" to casForm.eventId,
                "submit" to "Submit",
            )

            val loginResult = transport.postForm(postUrl, fields)
            val loginPayload = when (loginResult) {
                is CoreResult.Failure -> {
                    _authState.value = AuthState.Unauthenticated
                    return loginResult
                }
                is CoreResult.Success -> loginResult.value
            }

            val responseHtml = loginPayload.decodeSigaaIso88591()
            if (CasAndJsfParser.isCasLoginPage(responseHtml) || loginPayload.finalUrl.contains("/sso-server/login")) {
                _authState.value = AuthState.Unauthenticated
                return CoreResult.Failure(CoreError.InvalidCredentials)
            }
            responseHtml
        } else {
            entryHtml
        }

        val idDiscente = CasAndJsfParser.extractDiscenteId(portalHtml)
        if (credentials.rememberCredentials) {
            vault.save(credentials.matricula.trim(), credentials.password)
        } else {
            vault.clear()
        }

        val authenticated = AuthState.Authenticated(
            matricula = credentials.matricula.trim(),
            idDiscente = idDiscente,
            sessionExpiresAtEpochMillis = clockMillis() + SESSION_TIMEOUT_MILLIS,
            hasStoredCredentials = credentials.rememberCredentials,
        )
        if (idDiscente != null) {
            freshLoginPortalHtml = portalHtml
            freshLoginCapturedAtMillis = clockMillis()
        } else {
            freshLoginPortalHtml = null
            freshLoginCapturedAtMillis = 0L
        }
        diagLog("loginLocked authenticated idDiscente=$idDiscente cached=${idDiscente != null}")
        _authState.value = authenticated
        return CoreResult.Success(authenticated)
    }

    private fun updateSessionTouch(idDiscente: String?) {
        val current = _authState.value
        if (current is AuthState.Authenticated) {
            _authState.value = current.copy(
                idDiscente = idDiscente ?: current.idDiscente,
                sessionExpiresAtEpochMillis = clockMillis() + SESSION_TIMEOUT_MILLIS,
            )
        }
    }

    private companion object {
        const val SESSION_TIMEOUT_MILLIS = 40 * 60 * 1000L
        const val SAFETY_MARGIN_MILLIS = 60 * 1000L
        const val FRESH_HTML_TTL_MILLIS = 30 * 1000L
    }
}

/**
 * Helpers de navegacao JSF executados exclusivamente dentro de `SigaaSessionCoordinator.withAuthenticatedJsf`.
 */
class SigaaNavigator internal constructor(
    private val transport: UnbHttpTransport,
    private val clockMillis: () -> Long,
    private val onTouch: (String?) -> Unit,
) {
    val endpoints get() = transport.endpoints

    fun nowMillis(): Long = clockMillis()

    suspend fun getPortalHomeHtml(): CoreResult<String> {
        val payload = when (val res = transport.get(endpoints.verPortalDiscenteUrl)) {
            is CoreResult.Failure -> return res
            is CoreResult.Success -> res.value
        }
        return checkHtmlNotExpired(payload)
    }

    suspend fun postDiscenteForm(params: Map<String, String>): CoreResult<HttpPayload> {
        val payload = when (val res = transport.postForm(endpoints.discentePortalUrl, params)) {
            is CoreResult.Failure -> return res
            is CoreResult.Success -> res.value
        }
        return checkPayloadNotExpired(payload)
    }

    suspend fun postMenuDiscenteAction(jscookAction: String): CoreResult<HttpPayload> {
        val homeHtml = when (val home = getPortalHomeHtml()) {
            is CoreResult.Failure -> return home
            is CoreResult.Success -> home.value
        }
        val params = CasAndJsfParser.buildMenuDiscenteParams(homeHtml, jscookAction)
            ?: return CoreResult.Failure(
                CoreError.ParseError("discente.jsf", "Formulario #menu:form_menu_discente nao encontrado."),
            )
        return postDiscenteForm(params)
    }

    suspend fun postAvaForm(params: Map<String, String>): CoreResult<HttpPayload> {
        val payload = when (val res = transport.postForm(endpoints.avaIndexUrl, params)) {
            is CoreResult.Failure -> return res
            is CoreResult.Success -> res.value
        }
        return checkPayloadNotExpired(payload)
    }

    suspend fun getSigaaBinary(relativeOrAbsoluteUrl: String): CoreResult<HttpPayload> {
        val targetUrl = when {
            relativeOrAbsoluteUrl.startsWith("http://", ignoreCase = true) ||
                relativeOrAbsoluteUrl.startsWith("https://", ignoreCase = true) -> relativeOrAbsoluteUrl
            relativeOrAbsoluteUrl.startsWith("//") -> "https:$relativeOrAbsoluteUrl"
            else -> "${endpoints.sigaaBaseUrl.trimEnd('/')}/${relativeOrAbsoluteUrl.trimStart('/')}"
        }
        val payload = when (val res = transport.get(targetUrl)) {
            is CoreResult.Failure -> return res
            is CoreResult.Success -> res.value
        }
        return checkPayloadNotExpired(payload)
    }

    private fun checkHtmlNotExpired(payload: HttpPayload): CoreResult<String> {
        val html = payload.decodeSigaaIso88591()
        if (payload.finalUrl.contains("/sso-server/login") || CasAndJsfParser.isCasLoginPage(html)) {
            return CoreResult.Failure(CoreError.SessionExpired)
        }
        val idDiscente = CasAndJsfParser.extractDiscenteId(html)
        onTouch(idDiscente)
        return CoreResult.Success(html)
    }

    private fun checkPayloadNotExpired(payload: HttpPayload): CoreResult<HttpPayload> {
        if (payload.finalUrl.contains("/sso-server/login")) {
            return CoreResult.Failure(CoreError.SessionExpired)
        }
        val isBinary = payload.isPdf() || payload.contentType?.startsWith("image/", ignoreCase = true) == true
        if (!isBinary) {
            val html = payload.decodeSigaaIso88591()
            if (CasAndJsfParser.isCasLoginPage(html)) {
                return CoreResult.Failure(CoreError.SessionExpired)
            }
            val idDiscente = CasAndJsfParser.extractDiscenteId(html)
            onTouch(idDiscente)
        } else {
            onTouch(null)
        }
        return CoreResult.Success(payload)
    }
}
