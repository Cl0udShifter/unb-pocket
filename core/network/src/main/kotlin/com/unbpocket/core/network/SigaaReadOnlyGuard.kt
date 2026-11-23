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
    private val allowedHosts = setOf(
        "sigaa.unb.br",
        "autenticacao.unb.br",
        "sig.unb.br",
        "arquivos.unb.br",
        "ru.unb.br",
    )

    private val allowedPostPaths = setOf(
        "/sso-server/login",
        "/sigaa/portais/discente/discente.jsf",
        "/sigaa/ava/index.jsf",
        "/cxpostal/caixa_postal.jsf",
    )

    private val forbiddenPayloadTokens = listOf(
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
        if (host == "ru.unb.br" || cleanPath !in allowedPostPaths) {
            throw ReadOnlyViolationException(
                blockedTarget = "POST $host$cleanPath",
                reason = "Endpoint POST fora da whitelist de navegacao/emissao read-only.",
            )
        }

        val decodedBody = formEncodedBody
            ?.let { raw ->
                runCatching { URLDecoder.decode(raw, StandardCharsets.UTF_8.name()) }.getOrDefault(raw)
            }
            .orEmpty()

        for (token in forbiddenPayloadTokens) {
            if (decodedBody.contains(token, ignoreCase = false)) {
                throw ReadOnlyViolationException(
                    blockedTarget = "POST $host$cleanPath",
                    reason = "Payload contem acao mutavel proibida: $token",
                )
            }
        }
    }
}
