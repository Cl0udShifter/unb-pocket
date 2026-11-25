import {
  GRUPO_ACESSO_RU_BY_ID,
  type GrupoAcessoRu,
  type RefeicoesRestantes,
  type TabelaPrecosRu,
} from '../model/models';

const GROUP_REGEX = /Grupo\s+(\d+|I{1,3}|IV)/i;
const CURRENCY_REGEX = /(-)?\s*R\$\s*([\d.]+),(\d{2})/;

export const UNLIMITED_MEALS_SENTINEL = 2_147_483_647;

const DEFAULT_PRECOS_RESOLUCAO_0037: TabelaPrecosRu = {
  resolucaoReferencia: 'Resolução nº 0037/2025 do CAD/UnB',
  modifiedGmt: 'fallback',
  grupos: [
    {
      grupo: 'GRUPO_1',
      publicoAlvo: GRUPO_ACESSO_RU_BY_ID.GRUPO_1.descricao,
      cafeCentavos: 0,
      almocoCentavos: 0,
      jantarCentavos: 0,
      porKgCentavos: null,
    },
    {
      grupo: 'GRUPO_2',
      publicoAlvo: GRUPO_ACESSO_RU_BY_ID.GRUPO_2.descricao,
      cafeCentavos: 200,
      almocoCentavos: 450,
      jantarCentavos: 450,
      porKgCentavos: null,
    },
    {
      grupo: 'GRUPO_3',
      publicoAlvo: GRUPO_ACESSO_RU_BY_ID.GRUPO_3.descricao,
      cafeCentavos: 705,
      almocoCentavos: 1520,
      jantarCentavos: 1520,
      porKgCentavos: null,
    },
    {
      grupo: 'GRUPO_4',
      publicoAlvo: GRUPO_ACESSO_RU_BY_ID.GRUPO_4.descricao,
      cafeCentavos: 150,
      almocoCentavos: 250,
      jantarCentavos: 250,
      porKgCentavos: null,
    },
    {
      grupo: 'EXECUTIVO',
      publicoAlvo: GRUPO_ACESSO_RU_BY_ID.EXECUTIVO.descricao,
      cafeCentavos: null,
      almocoCentavos: null,
      jantarCentavos: null,
      porKgCentavos: 6042,
    },
  ],
  formasPagamento: ['Pix', 'Dinheiro', 'Cartão de Débito', 'Cartão de Crédito'],
};

export const RuCalculator = {
  defaultPrecosResolucao0037: DEFAULT_PRECOS_RESOLUCAO_0037,

  parseBrlToCentavos(raw: string): number | null {
    const match = CURRENCY_REGEX.exec(raw.trim());
    if (!match) return null;
    const isNegative = match[1] === '-';
    const reais = Number.parseInt(match[2].replace(/\./g, ''), 10);
    const cents = Number.parseInt(match[3], 10);
    if (Number.isNaN(reais) || Number.isNaN(cents)) return null;
    const total = reais * 100 + cents;
    if (total === 0) return 0;
    return isNegative ? -total : total;
  },

  detectGrupo(text: string): GrupoAcessoRu {
    const match = GROUP_REGEX.exec(text);
    const token = match?.[1]?.toUpperCase();
    if (!token) return 'DESCONHECIDO';
    switch (token) {
      case '1':
      case 'I':
        return 'GRUPO_1';
      case '2':
      case 'II':
        return 'GRUPO_2';
      case '3':
      case 'III':
        return 'GRUPO_3';
      case '4':
      case 'IV':
        return 'GRUPO_4';
      default:
        return 'DESCONHECIDO';
    }
  },

  calculateRemainingMeals(
    saldoAtualCentavos: number,
    grupo: GrupoAcessoRu,
    tabela: TabelaPrecosRu = DEFAULT_PRECOS_RESOLUCAO_0037,
  ): RefeicoesRestantes | null {
    const preco = tabela.grupos.find((g) => g.grupo === grupo);
    if (!preco || preco.cafeCentavos === null || preco.almocoCentavos === null) {
      return null;
    }
    const cafe = preco.cafeCentavos;
    const almoco = preco.almocoCentavos;

    if (cafe === 0 && almoco === 0) {
      const nonNeg = Math.max(0, saldoAtualCentavos);
      return {
        cafesRestantes: UNLIMITED_MEALS_SENTINEL,
        almocosOuJantaresRestantes: UNLIMITED_MEALS_SENTINEL,
        trocoCafeCentavos: nonNeg,
        trocoAlmocoJantarCentavos: nonNeg,
        gratuito: true,
      };
    }

    const saldoPositivo = Math.max(0, saldoAtualCentavos);
    const cafes = cafe > 0 ? Math.trunc(saldoPositivo / cafe) : 0;
    const almocos = almoco > 0 ? Math.trunc(saldoPositivo / almoco) : 0;
    return {
      cafesRestantes: cafes,
      almocosOuJantaresRestantes: almocos,
      trocoCafeCentavos: cafe > 0 ? saldoPositivo % cafe : saldoPositivo,
      trocoAlmocoJantarCentavos: almoco > 0 ? saldoPositivo % almoco : saldoPositivo,
      gratuito: false,
    };
  },
};
