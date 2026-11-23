package com.unbpocket.core.network

import com.unbpocket.core.model.common.CoreError
import com.unbpocket.core.model.common.CoreResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Endpoints base configuraveis para producao e testes isolados com MockWebServer.
 */
data class NetworkEndpoints(
    val sigaaBaseUrl: String = "https://sigaa.unb.br",
    val casBaseUrl: String = "https://autenticacao.unb.br",
    val sigBaseUrl: String = "https://sig.unb.br",
    val ruBaseUrl: String = "https://ru.unb.br",
) {
    val discentePortalUrl: String get() = "$sigaaBaseUrl/sigaa/portais/discente/discente.jsf"
    val verPortalDiscenteUrl: String get() = "$sigaaBaseUrl/sigaa/verPortalDiscente.do"
    val avaIndexUrl: String get() = "$sigaaBaseUrl/sigaa/ava/index.jsf"
    val turmasAnterioresUrl: String get() = "$sigaaBaseUrl/sigaa/portais/discente/turmas.jsf"
    val ruCardapiosPageUrl: String get() = "$ruBaseUrl/wp-json/wp/v2/pages/12853?_fields=id,modified_gmt,content"
    val ruMediaPdfsUrl: String get() = "$ruBaseUrl/wp-json/wp/v2/media?per_page=15&mime_type=application/pdf"
    fun ruNoticiasUrl(perPage: Int): String = "$ruBaseUrl/wp-json/wp/v2/posts?per_page=$perPage&_embed"
    val ruPrecosPageUrl: String get() = "$ruBaseUrl/wp-json/wp/v2/pages?slug=precos-e-grupos&_fields=id,modified_gmt,content"
}

/**
 * Resposta HTTP bruta com URL final pos-redirecionamentos e helpers de charset.
 */
class HttpPayload(
    val finalUrl: String,
    val statusCode: Int,
    val contentType: String?,
    val contentDisposition: String?,
    val bytes: ByteArray,
) {
    fun decodeSigaaIso88591(): String = UnbHttpSession.decodeSigaaBytes(bytes)

    fun decodeUtf8(): String = String(bytes, Charsets.UTF_8)

    fun isPdf(): Boolean =
        contentType?.contains("application/pdf", ignoreCase = true) == true ||
            (bytes.size >= 4 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() &&
                bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte())

    fun resolveRelativeUrl(relativeOrAbsolute: String): String {
        val base = finalUrl.toHttpUrlOrNull() ?: return relativeOrAbsolute
        return base.resolve(relativeOrAbsolute)?.toString() ?: relativeOrAbsolute
    }

    fun extractAttachmentFileName(fallbackName: String): String {
        val disposition = contentDisposition.orEmpty()
        val utf8Match = Regex("""filename\*=UTF-8''([^;\r\n]+)""", RegexOption.IGNORE_CASE).find(disposition)
        if (utf8Match != null) {
            val raw = utf8Match.groupValues[1].trim('"', ' ')
            return runCatching { URLDecoder.decode(raw, StandardCharsets.UTF_8.name()) }.getOrDefault(raw)
        }
        val stdMatch = Regex("""filename="?([^";\r\n]+)"?""", RegexOption.IGNORE_CASE).find(disposition)
        if (stdMatch != null) return stdMatch.groupValues[1].trim('"', ' ')
        return fallbackName
    }
}

/**
 * Camada de transporte HTTP responsavel por executar GET/POST com tratamento
 * explicito de erros de rede, bloqueios Read-Only e falhas HTTP.
 */
class UnbHttpTransport(
    val session: UnbHttpSession = UnbHttpSession(),
    val endpoints: NetworkEndpoints = NetworkEndpoints(),
) {
    suspend fun get(url: String): CoreResult<HttpPayload> {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", USER_AGENT)
            .build()
        return execute(request, url)
    }

    suspend fun postForm(url: String, fields: Map<String, String>): CoreResult<HttpPayload> {
        val formBuilder = FormBody.Builder(UnbHttpSession.SIGAA_CHARSET)
        fields.forEach { (key, value) -> formBuilder.add(key, value) }
        val request = Request.Builder()
            .url(url)
            .post(formBuilder.build())
            .header("User-Agent", USER_AGENT)
            .build()
        return execute(request, url)
    }

    private suspend fun execute(request: Request, targetUrl: String): CoreResult<HttpPayload> =
        withContext(Dispatchers.IO) {
            try {
                session.sigaaClient.newCall(request).execute().use { response ->
                    val bytes = response.body?.bytes() ?: ByteArray(0)
                    if (!response.isSuccessful) {
                        return@withContext CoreResult.Failure(
                            CoreError.HttpFailure(
                                statusCode = response.code,
                                endpoint = targetUrl,
                                message = response.message.ifBlank { null },
                            ),
                        )
                    }
                    CoreResult.Success(
                        HttpPayload(
                            finalUrl = response.request.url.toString(),
                            statusCode = response.code,
                            contentType = response.header("Content-Type"),
                            contentDisposition = response.header("Content-Disposition"),
                            bytes = bytes,
                        ),
                    )
                }
            } catch (violation: ReadOnlyViolationException) {
                CoreResult.Failure(
                    CoreError.ReadOnlyViolation(
                        blockedTarget = violation.blockedTarget,
                        reason = violation.reason,
                    ),
                )
            } catch (_: IOException) {
                CoreResult.Failure(CoreError.NetworkUnavailable)
            } catch (e: IllegalArgumentException) {
                CoreResult.Failure(
                    CoreError.HttpFailure(
                        statusCode = 0,
                        endpoint = targetUrl,
                        message = e.message,
                    ),
                )
            }
        }

    private companion object {
        const val USER_AGENT = "UnBPocket-Android/1.0"
    }
}
