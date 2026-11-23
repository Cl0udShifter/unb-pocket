package com.unbpocket.core.network

import org.junit.Assert.assertThrows
import org.junit.Test

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
    }
}
