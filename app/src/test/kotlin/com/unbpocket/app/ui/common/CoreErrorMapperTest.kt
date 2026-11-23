package com.unbpocket.app.ui.common

import com.unbpocket.core.model.common.CoreError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreErrorMapperTest {

    @Test
    fun `maps all CoreError variants with accurate login and retry flags`() {
        val allErrors: List<CoreError> = listOf(
            CoreError.InvalidCredentials,
            CoreError.SessionExpired,
            CoreError.NoStoredCredentials,
            CoreError.NetworkUnavailable,
            CoreError.HttpFailure(statusCode = 503, endpoint = "sigaa", message = null),
            CoreError.ParseError(source = "RU PDF", reason = "regex miss", recoverableFallbackAvailable = true),
            CoreError.ParseError(source = "Boletim", reason = "table changed", recoverableFallbackAvailable = false),
            CoreError.ReadOnlyViolation(blockedTarget = "trancamento", reason = "blocked"),
            CoreError.PdfExtractionError(documentType = "historico", reason = "corrupt"),
            CoreError.StorageError(operation = "room write", reason = "disk full"),
            CoreError.UnavailableForProfile(feature = "Carteirinha", reason = "egresso"),
            CoreError.Unexpected(message = "boom"),
        )

        allErrors.forEach { error ->
            val mapped = CoreErrorMapper.toUiError(error)
            assertTrue("Title blank for $error", mapped.title.isNotBlank())
            assertTrue("Message blank for $error", mapped.message.isNotBlank())
        }

        assertTrue(CoreErrorMapper.toUiError(CoreError.InvalidCredentials).requiresLogin)
        assertTrue(CoreErrorMapper.toUiError(CoreError.SessionExpired).requiresLogin)
        assertTrue(CoreErrorMapper.toUiError(CoreError.NoStoredCredentials).requiresLogin)

        val network = CoreErrorMapper.toUiError(CoreError.NetworkUnavailable)
        assertFalse(network.requiresLogin)
        assertTrue(network.canRetry)
        assertTrue(network.isWarning)

        val recoverableParse = CoreErrorMapper.toUiError(
            CoreError.ParseError("ru", "fail", recoverableFallbackAvailable = true),
        )
        assertTrue(recoverableParse.isWarning)
        assertTrue(recoverableParse.canRetry)

        val readOnly = CoreErrorMapper.toUiError(CoreError.ReadOnlyViolation("x", "y"))
        assertFalse(readOnly.canRetry)
        assertFalse(readOnly.requiresLogin)

        assertEquals(
            "Portal indisponível (503)",
            CoreErrorMapper.toUiError(CoreError.HttpFailure(503, "ru.unb.br", null)).title,
        )
    }
}
