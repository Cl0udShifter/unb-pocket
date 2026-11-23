package com.unbpocket.core.network

import com.unbpocket.core.model.common.CoreError
import com.unbpocket.core.model.common.CoreResult
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class SigaaReadOnlyGuardTest {
    @Test
    fun allowsReadOnlyGetsAndWhitelistedPosts() {
        SigaaReadOnlyGuard.validateRequest(
            host = "ru.unb.br",
            path = "/wp-json/wp/v2/pages/12853",
            method = "GET",
            formEncodedBody = null,
        )
        SigaaReadOnlyGuard.validateRequest(
            host = "sigaa.unb.br",
            path = "/sigaa/portais/discente/discente.jsf",
            method = "POST",
            formEncodedBody = "formExibirExtrato=formExibirExtrato&javax.faces.ViewState=j_id1",
        )
        SigaaReadOnlyGuard.validateRequest(
            host = "autenticacao.unb.br",
            path = "/sso-server/login;jsessionid=ABC?service=https%3A%2F%2Fsig.unb.br",
            method = "POST",
            formEncodedBody = "username=190026308&password=Confirmar123&lt=LT-1&execution=e1s1&_eventId=submit&submit=Submit",
        )
    }

    @Test
    fun blocksMutatingPayloadsAndUnauthorizedHosts() {
        assertThrows(ReadOnlyViolationException::class.java) {
            SigaaReadOnlyGuard.validateRequest(
                host = "sigaa.unb.br",
                path = "/sigaa/portais/discente/discente.jsf",
                method = "POST",
                formEncodedBody = "jscook_action=retiradaMatricula&javax.faces.ViewState=j_id1",
            )
        }
        assertThrows(ReadOnlyViolationException::class.java) {
            SigaaReadOnlyGuard.validateRequest(
                host = "sigaa.unb.br",
                path = "/sigaa/graduacao/matricula/painel.jsf",
                method = "POST",
                formEncodedBody = "confirmar=1",
            )
        }
        assertThrows(ReadOnlyViolationException::class.java) {
            SigaaReadOnlyGuard.validateRequest(
                host = "example.com",
                path = "/api",
                method = "GET",
                formEncodedBody = null,
            )
        }
        assertThrows(ReadOnlyViolationException::class.java) {
            SigaaReadOnlyGuard.validateRequest(
                host = "sigaa.unb.br",
                path = "/sigaa/portais/discente/discente.jsf",
                method = "DELETE",
                formEncodedBody = null,
            )
        }
    }

    @Test
    fun transportDecodesIso88591AndMapsHttpAndReadOnlyErrors() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val baseUrl = server.url("").toString().trimEnd('/')
            val session = UnbHttpSession.forTesting(setOf(server.hostName.lowercase(), "localhost", "127.0.0.1"))
            val transport = UnbHttpTransport(
                session = session,
                endpoints = NetworkEndpoints(
                    sigaaBaseUrl = baseUrl,
                    casBaseUrl = baseUrl,
                    sigBaseUrl = baseUrl,
                    ruBaseUrl = baseUrl,
                ),
            )

            val latin1Html = "<html><body>Portal Discente: Ação e Média</body></html>"
                .toByteArray(Charset.forName("ISO-8859-1"))
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/html;charset=ISO-8859-1")
                    .setBody(okio.Buffer().write(latin1Html)),
            )

            val okResult = transport.get("$baseUrl/sigaa/portais/discente/discente.jsf")
            assertTrue(okResult is CoreResult.Success)
            assertEquals(
                "<html><body>Portal Discente: Ação e Média</body></html>",
                (okResult as CoreResult.Success).value.decodeSigaaIso88591(),
            )

            val blockedResult = transport.postForm(
                "$baseUrl/sigaa/portais/discente/discente.jsf",
                mapOf("jscook_action" to "trancamentoMatricula"),
            )
            assertTrue(blockedResult is CoreResult.Failure)
            assertTrue((blockedResult as CoreResult.Failure).error is CoreError.ReadOnlyViolation)

            server.enqueue(MockResponse().setResponseCode(503).setBody("Unavailable"))
            val httpFailure = transport.get("$baseUrl/sigaa/verPortalDiscente.do")
            assertTrue(httpFailure is CoreResult.Failure)
            val err = (httpFailure as CoreResult.Failure).error as CoreError.HttpFailure
            assertEquals(503, err.statusCode)
        } finally {
            server.shutdown()
        }
    }
}
