package com.unbpocket.core.parser

import com.unbpocket.core.model.ru.GrupoAcessoRu
import com.unbpocket.core.model.ru.PrecoGrupoRu
import com.unbpocket.core.model.ru.RefeicoesRestantes
import com.unbpocket.core.model.ru.TabelaPrecosRu

/**
 * Regras de calculo de saldo em centavos, grupo RU e refeicoes restantes
 * segundo a Resolucao n. 0037/2025 do CAD/UnB.
 */
object RuCalculator {
    private val groupRegex = Regex("""Grupo\s+(\d+|I{1,3}|IV)""", RegexOption.IGNORE_CASE)
    private val currencyRegex = Regex("""(-)?\s*R\$\s*([\d.]+),(\d{2})""")

    val defaultPrecosResolucao0037: TabelaPrecosRu = TabelaPrecosRu(
        resolucaoReferencia = "Resolução nº 0037/2025 do CAD/UnB",
        modifiedGmt = "fallback",
        grupos = listOf(
            PrecoGrupoRu(
                grupo = GrupoAcessoRu.GRUPO_1,
                publicoAlvo = GrupoAcessoRu.GRUPO_1.descricao,
                cafeCentavos = 0L,
                almocoCentavos = 0L,
                jantarCentavos = 0L,
                porKgCentavos = null,
            ),
            PrecoGrupoRu(
                grupo = GrupoAcessoRu.GRUPO_2,
                publicoAlvo = GrupoAcessoRu.GRUPO_2.descricao,
                cafeCentavos = 200L,
                almocoCentavos = 450L,
                jantarCentavos = 450L,
                porKgCentavos = null,
            ),
            PrecoGrupoRu(
                grupo = GrupoAcessoRu.GRUPO_3,
                publicoAlvo = GrupoAcessoRu.GRUPO_3.descricao,
                cafeCentavos = 705L,
                almocoCentavos = 1520L,
                jantarCentavos = 1520L,
                porKgCentavos = null,
            ),
            PrecoGrupoRu(
                grupo = GrupoAcessoRu.GRUPO_4,
                publicoAlvo = GrupoAcessoRu.GRUPO_4.descricao,
                cafeCentavos = 150L,
                almocoCentavos = 250L,
                jantarCentavos = 250L,
                porKgCentavos = null,
            ),
            PrecoGrupoRu(
                grupo = GrupoAcessoRu.EXECUTIVO,
                publicoAlvo = GrupoAcessoRu.EXECUTIVO.descricao,
                cafeCentavos = null,
                almocoCentavos = null,
                jantarCentavos = null,
                porKgCentavos = 6042L,
            ),
        ),
        formasPagamento = listOf("Pix", "Dinheiro", "Cartão de Débito", "Cartão de Crédito"),
    )

    fun parseBrlToCentavos(raw: String): Long? {
        val match = currencyRegex.find(raw.trim()) ?: return null
        val isNegative = match.groupValues[1] == "-"
        val reais = match.groupValues[2].replace(".", "").toLongOrNull() ?: return null
        val cents = match.groupValues[3].toLongOrNull() ?: return null
        val total = reais * 100L + cents
        return if (isNegative) -total else total
    }

    fun detectGrupo(text: String): GrupoAcessoRu {
        val token = groupRegex.find(text)?.groupValues?.getOrNull(1)?.uppercase() ?: return GrupoAcessoRu.DESCONHECIDO
        return when (token) {
            "1", "I" -> GrupoAcessoRu.GRUPO_1
            "2", "II" -> GrupoAcessoRu.GRUPO_2
            "3", "III" -> GrupoAcessoRu.GRUPO_3
            "4", "IV" -> GrupoAcessoRu.GRUPO_4
            else -> GrupoAcessoRu.DESCONHECIDO
        }
    }

    fun calculateRemainingMeals(
        saldoAtualCentavos: Long,
        grupo: GrupoAcessoRu,
        tabela: TabelaPrecosRu = defaultPrecosResolucao0037,
    ): RefeicoesRestantes? {
        val preco = tabela.grupos.firstOrNull { it.grupo == grupo } ?: return null
        val cafe = preco.cafeCentavos ?: return null
        val almoco = preco.almocoCentavos ?: return null

        if (cafe == 0L && almoco == 0L) {
            return RefeicoesRestantes(
                cafesRestantes = Int.MAX_VALUE,
                almocosOuJantaresRestantes = Int.MAX_VALUE,
                trocoCafeCentavos = saldoAtualCentavos.coerceAtLeast(0L),
                trocoAlmocoJantarCentavos = saldoAtualCentavos.coerceAtLeast(0L),
                gratuito = true,
            )
        }

        val saldoPositivo = saldoAtualCentavos.coerceAtLeast(0L)
        val cafes = if (cafe > 0L) (saldoPositivo / cafe).toInt() else 0
        val almocos = if (almoco > 0L) (saldoPositivo / almoco).toInt() else 0
        return RefeicoesRestantes(
            cafesRestantes = cafes,
            almocosOuJantaresRestantes = almocos,
            trocoCafeCentavos = if (cafe > 0L) saldoPositivo % cafe else saldoPositivo,
            trocoAlmocoJantarCentavos = if (almoco > 0L) saldoPositivo % almoco else saldoPositivo,
            gratuito = false,
        )
    }
}
