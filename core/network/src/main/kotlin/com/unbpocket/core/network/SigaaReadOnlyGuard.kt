package com.unbpocket.core.network

import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Excecao lancada antes da transmissao HTTP quando uma requisicao viola
 * o contrato estrito de somente leitura ou tenta acessar hosts externos.
 */
class ReadOnlyViolationException(
    val blockedTarget: String,
    val reason: String,
) : IOException("Bloqueio Read-Only ($blockedTarget): $reason")

/**
 * Guard-rail programatico para garantir que o UnB Pocket execute apenas
 * requisicoes de leitura e navegacao/emissao mapeadas no contrato.
 */
object SigaaReadOnlyGuard {
    val defaultAllowedHosts: Set<String> = setOf(
        "sigaa.unb.br",
        "autenticacao.unb.br",
        "sig.unb.br",
        "arquivos.unb.br",
        "ru.unb.br",
        "api.github.com",
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
    )

    private val getOnlyHosts: Set<String> = setOf(
        "ru.unb.br",
        "api.github.com",
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
    )

    private val allowedPostPaths = setOf(
        "/sso-server/login",
        "/sigaa/portais/discente/discente.jsf",
        "/sigaa/ava/index.jsf",
        "/cxpostal/caixa_postal.jsf",
    )

    private val allowedCasLoginKeys = setOf(
        "username",
        "password",
        "lt",
        "execution",
        "_eventId",
        "submit",
    )

    private val forbiddenSigaaTokens = listOf(
        "retiradaMatricula",
        "trancamentoMatricula",
        "trancamentoPrograma",
        "novoTopicoForum",
        "btEnviarEmailNee",
        "dados_discente",
        "alterarSenha",
        "excluirMensagem",
        "enviarMensagem",
        "Confirmar",
    )

    fun validateRequest(
        host: String,
        path: String,
        method: String,
        formEncodedBody: String?,
    ) {
        validateRequestWithHosts(
            host = host,
            path = path,
            method = method,
            formEncodedBody = formEncodedBody,
            allowedHosts = defaultAllowedHosts,
        )
    }

    internal fun validateRequestWithHosts(
        host: String,
        path: String,
        method: String,
        formEncodedBody: String?,
        allowedHosts: Set<String>,
    ) {
        val normalizedHost = host.lowercase()
        if (normalizedHost !in allowedHosts) {
            throw ReadOnlyViolationException(
                blockedTarget = "$normalizedHost$path",
                reason = "Host nao autorizado pelo contrato do UnB Pocket.",
            )
        }

        when (method.uppercase()) {
            "GET", "HEAD" -> return
            "POST" -> validatePost(normalizedHost, path, formEncodedBody)
            else -> throw ReadOnlyViolationException(
                blockedTarget = "$method $normalizedHost$path",
                reason = "Metodo HTTP $method nao permitido no modo somente leitura.",
            )
        }
    }

    private fun validatePost(host: String, path: String, formEncodedBody: String?) {
        val cleanPath = path.substringBefore(';').substringBefore('?')
        if (host in getOnlyHosts || cleanPath !in allowedPostPaths) {
            throw ReadOnlyViolationException(
                blockedTarget = "POST $host$cleanPath",
                reason = "Endpoint POST fora da whitelist de navegacao/emissao read-only.",
            )
        }

        val rawBody = formEncodedBody.orEmpty()
        if (cleanPath == "/sso-server/login") {
            val keys = rawBody
                .split('&')
                .filter { it.isNotBlank() }
                .map { param ->
                    val rawKey = param.substringBefore('=')
                    runCatching { URLDecoder.decode(rawKey, StandardCharsets.UTF_8.name()) }.getOrDefault(rawKey)
                }
            val unexpectedKeys = keys.filter { it !in allowedCasLoginKeys }
            if (unexpectedKeys.isNotEmpty()) {
                throw ReadOnlyViolationException(
                    blockedTarget = "POST $host$cleanPath",
                    reason = "Campos nao permitidos no login CAS: ${unexpectedKeys.joinToString()}",
                )
            }
            return
        }

        val decodedBody = runCatching {
            URLDecoder.decode(rawBody, StandardCharsets.UTF_8.name())
        }.getOrDefault(rawBody)

        for (token in forbiddenSigaaTokens) {
            if (decodedBody.contains(token, ignoreCase = false)) {
                throw ReadOnlyViolationException(
                    blockedTarget = "POST $host$cleanPath",
                    reason = "Payload contem acao mutavel proibida: $token",
                )
            }
        }
    }
}
