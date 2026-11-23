package com.unbpocket.core.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.text.Normalizer

data class CasLoginForm(
    val action: String,
    val lt: String,
    val execution: String,
    val eventId: String,
)

/**
 * Utilitarios de extracao de estado JSF (`javax.faces.ViewState`, `jsfcljs`,
 * `idDiscente`) e tokens do formulario Apereo CAS.
 */
object CasAndJsfParser {
    internal const val JSF_CLJS_PATTERN = """jsfcljs\([^,]+,\s*\{(.*?)\}\s*,?"""
    private val jsfCljsObjectRegex = Regex(JSF_CLJS_PATTERN, RegexOption.DOT_MATCHES_ALL)
    private val keyValuePairRegex = Regex("""'([^']*)'\s*:\s*'([^']*)'""")

    fun isCasLoginPage(html: String): Boolean {
        val doc = Jsoup.parse(html)
        return doc.selectFirst("form#login-form") != null ||
            (doc.selectFirst("input[name=lt]") != null && doc.selectFirst("input[name=execution]") != null)
    }

    fun parseCasLoginForm(html: String): CasLoginForm? {
        val doc = Jsoup.parse(html)
        val form = doc.selectFirst("form#login-form") ?: return null
        val action = form.attr("action").trim()
        val lt = form.selectFirst("input[name=lt]")?.attr("value")?.trim()
        val execution = form.selectFirst("input[name=execution]")?.attr("value")?.trim()
        val eventId = form.selectFirst("input[name=_eventId]")?.attr("value")?.trim().orEmpty().ifEmpty { "submit" }
        if (action.isEmpty() || lt.isNullOrEmpty() || execution.isNullOrEmpty()) return null
        return CasLoginForm(
            action = action,
            lt = lt,
            execution = execution,
            eventId = eventId,
        )
    }

    fun extractViewState(doc: Document): String? =
        doc.selectFirst("input[name=javax.faces.ViewState]")?.attr("value")?.trim()?.takeIf { it.isNotEmpty() }

    fun extractViewState(html: String): String? = extractViewState(Jsoup.parse(html))

    fun extractDiscenteId(html: String): String? = extractDiscenteId(Jsoup.parse(html))

    fun extractDiscenteId(doc: Document): String? =
        doc.selectFirst("form[id='menu:form_menu_discente'] input[name=id]")
            ?.attr("value")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    fun extractJsfCljsParams(onClick: String): Map<String, String> {
        val match = jsfCljsObjectRegex.find(onClick) ?: return emptyMap()
        val objectBody = match.groupValues[1]
        val params = linkedMapOf<String, String>()
        for (pair in keyValuePairRegex.findAll(objectBody)) {
            params[pair.groupValues[1]] = pair.groupValues[2]
        }
        return params
    }

    fun extractExtratoRuFormParams(html: String): Map<String, String>? =
        extractExtratoRuFormParams(Jsoup.parse(html))

    fun extractExtratoRuFormParams(doc: Document): Map<String, String>? {
        val form = doc.selectFirst("form#formExibirExtrato") ?: return null
        val link = form.selectFirst("a[onclick]") ?: return null
        val viewState = extractViewState(doc) ?: return null
        val params = linkedMapOf<String, String>()
        val formId = form.attr("id").ifBlank { "formExibirExtrato" }
        params[formId] = formId
        params.putAll(extractJsfCljsParams(link.attr("onclick")))
        if (params.size <= 1) return null
        params["javax.faces.ViewState"] = viewState
        return params
    }

    fun buildMenuDiscenteParams(
        html: String,
        jscookAction: String,
    ): Map<String, String>? = buildMenuDiscenteParams(Jsoup.parse(html), jscookAction)

    fun buildMenuDiscenteParams(
        doc: Document,
        jscookAction: String,
    ): Map<String, String>? {
        val idDiscente = extractDiscenteId(doc) ?: return null
        val viewState = extractViewState(doc) ?: return null
        return linkedMapOf(
            "menu:form_menu_discente" to "menu:form_menu_discente",
            "id" to idDiscente,
            "jscook_action" to jscookAction,
            "javax.faces.ViewState" to viewState,
        )
    }

    fun normalizeLabel(raw: String): String {
        val decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("""\p{InCombiningDiacriticalMarks}+"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .lowercase()
    }
}
