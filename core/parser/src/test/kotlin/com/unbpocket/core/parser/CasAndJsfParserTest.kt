package com.unbpocket.core.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CasAndJsfParserTest {
    @Test
    fun jsfCljsRegexEscapesLiteralBracesForAndroidIcuCompatibility() {
        // Android ICU PatternNative rejeita '}' literal nao escapado fora de quantificadores.
        val unescapedLiteralCloseBrace = Regex("""(?<!\\)\}""")
        assertFalse(unescapedLiteralCloseBrace.containsMatchIn(CasAndJsfParser.JSF_CLJS_PATTERN))

        val unescapedLiteralOpenBrace = Regex("""(?<!\\)\{""")
        assertFalse(unescapedLiteralOpenBrace.containsMatchIn(CasAndJsfParser.JSF_CLJS_PATTERN))
    }

    @Test
    fun extractsJsfCljsParamsAndExtratoFormWithoutCrashing() {
        val onclick = "jsfcljs(document.getElementById('formMenu'),{'formMenu:j_id_1':'formMenu:j_id_2','id':'331956'},'')"
        val params = CasAndJsfParser.extractJsfCljsParams(onclick)
        assertEquals("formMenu:j_id_2", params["formMenu:j_id_1"])
        assertEquals("331956", params["id"])

        val discenteHtml = """
            <html><body>
              <form id="formExibirExtrato">
                <a onclick="jsfcljs(document.getElementById('formExibirExtrato'),{'formExibirExtrato:btn':'formExibirExtrato:btn'},'')">Saldo</a>
              </form>
              <input type="hidden" name="javax.faces.ViewState" value="j_id1" />
            </body></html>
        """.trimIndent()
        val extratoParams = CasAndJsfParser.extractExtratoRuFormParams(discenteHtml)
        assertNotNull(extratoParams)
        assertTrue(extratoParams?.containsKey("formExibirExtrato:btn") == true)
        assertEquals("j_id1", extratoParams?.get("javax.faces.ViewState"))
    }
}
