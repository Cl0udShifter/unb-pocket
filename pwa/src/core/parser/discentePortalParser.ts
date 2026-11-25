import {
  CoreResults,
  type AtualizacaoTurmaFeed,
  type CoreResult,
  type DiscenteResumo,
  type GrupoAcessoRu,
  type IndicesIntegralizacao,
  type PerfilDiscente,
  type SaldoExtratoRu,
  type TabelaPrecosRu,
  type TransacaoRu,
  type TurmaResumo,
} from '../model/models';
import { CasAndJsfParser, parseHtmlDocument, parseHtmlFragmentText } from './casAndJsfParser';
import { RuCalculator } from './ruCalculator';
import { SigaaScheduleDecoder } from './sigaaScheduleDecoder';

export interface ParsedHomeTurma {
  readonly resumo: TurmaResumo;
  readonly formId: string;
  readonly formParams: Record<string, string>;
}

const PUBLIC_COURSE_REGEX = /curso\/portal\.jsf\?id=(\d+)/;
const PROGRESS_WIDTH_REGEX = /width:\s*([\d.,]+)%/i;
const ID_TURMA_ONCLICK_REGEX = /'idTurma'\s*:\s*'(\d+)'/;
const FRONTEND_ID_REGEX = /'frontEndIdTurma'\s*:\s*'([A-F0-9]{40})'/;
const DIGITS_REGEX = /\d+/;

function cleanText(node: Node | null | undefined): string {
  return (node?.textContent ?? '').replace(/\s+/g, ' ').trim();
}

export const DiscentePortalParser = {
  parseDiscenteResumo(html: string): CoreResult<DiscenteResumo> {
    const doc = parseHtmlDocument(html);
    const agendaRows = parseAgendaRows(doc);
    const idDiscente = CasAndJsfParser.extractDiscenteIdFromDoc(doc) ?? '';
    const matricula = agendaRows['matricula'];

    const rawNomeStrong = cleanText(doc.querySelector('#perfil-docente p.info-docente span.nome b'));
    const rawNomeUsuario = cleanText(doc.querySelector('#info-usuario p.usuario span'));
    const nomeCompleto = rawNomeStrong || rawNomeUsuario;

    if (!matricula || !nomeCompleto) {
      return CoreResults.failure({
        kind: 'ParseError',
        source: 'discente.jsf',
        reason: 'Campos obrigatorios de matricula ou nome nao encontrados no portal discente.',
      });
    }

    const nomeAbreviado = rawNomeUsuario || nomeCompleto;

    const infoDocenteEl = doc.querySelector('#perfil-docente p.info-docente');
    let bioPerfil: string | null = null;
    if (infoDocenteEl) {
      const clone = infoDocenteEl.cloneNode(true) as Element;
      clone.querySelectorAll('span.nome').forEach((n) => n.remove());
      const bio = cleanText(clone);
      if (bio.length > 0) bioPerfil = bio;
    }

    const fotoEl =
      doc.querySelector('#perfil-docente div.foto img') ??
      doc.querySelector('#perfil-docente .foto img');
    const rawFotoSrc = (fotoEl?.getAttribute('src') ?? '').trim();
    const isPlaceholderFoto =
      rawFotoSrc.length === 0 ||
      /no_picture|sem_foto|semfoto/i.test(rawFotoSrc);
    const fotoUrl = isPlaceholderFoto
      ? null
      : rawFotoSrc.startsWith('//')
        ? `https:${rawFotoSrc}`
        : rawFotoSrc;

    const idCursoPublico = PUBLIC_COURSE_REGEX.exec(html)?.[1] ?? null;

    const perfil: PerfilDiscente = {
      idDiscente,
      matricula,
      nomeCompleto,
      nomeAbreviado,
      bioPerfil,
      fotoUrl,
      semestreAtual: cleanText(doc.querySelector('#info-usuario p.periodo-atual strong')),
      semestreEntrada: agendaRows['entrada'] ?? '',
      unidadeDepartamento: cleanText(doc.querySelector('#info-usuario p.unidade')),
      curso: agendaRows['curso'] ?? '',
      nivel: agendaRows['nivel'] ?? '',
      status: agendaRows['status'] ?? '',
      emailTruncado: agendaRows['e-mail'] ?? null,
      idCursoPortalPublico: idCursoPublico,
      fotoLocalPath: null,
    };

    const progressDiv = doc.querySelector(
      '#agenda-docente div[style*="width:"], #agenda-docente .progress-bar',
    );
    const widthMatch = PROGRESS_WIDTH_REGEX.exec(progressDiv?.getAttribute('style') ?? '');
    const percentualExato = widthMatch ? parseDoubleOrNull(widthMatch[1]) : null;

    const integralizacaoCells = Array.from(doc.querySelectorAll('#agenda-docente td')).filter((td) =>
      /integralizado/i.test(cleanText(td)),
    );
    const lastIntegralizacaoText =
      integralizacaoCells.length > 0
        ? cleanText(integralizacaoCells[integralizacaoCells.length - 1])
        : null;
    let percentualTexto: string | null = null;
    if (lastIntegralizacaoText) {
      const m = /\d+(?:[.,]\d+)?%\s*Integralizado/i.exec(lastIntegralizacaoText);
      percentualTexto = m ? m[0].replace(/\s+/g, ' ').trim() : lastIntegralizacaoText;
    }

    const indices: IndicesIntegralizacao = {
      ira: parseDoubleOrNull(agendaRows['ira']),
      mp: parseDoubleOrNull(agendaRows['mp']),
      chObrigatoriaPendente: parseIntOrNull(agendaRows['ch. obrigatoria pendente']),
      chOptativaPendente: parseIntOrNull(agendaRows['ch. optativa pendente']),
      chComplementarPendente: parseIntOrNull(agendaRows['ch. complementar pendente']),
      chTotalCurriculo: parseIntOrNull(agendaRows['ch. total curriculo']),
      percentualIntegralizadoExato: percentualExato,
      percentualIntegralizadoTexto: percentualTexto,
    };

    return CoreResults.success({ perfil, indices });
  },

  parseAtualizacoesFeed(html: string): AtualizacaoTurmaFeed[] {
    const doc = parseHtmlDocument(html);
    const tables = Array.from(doc.querySelectorAll('#atualizacoes-turma div.rotator > table'));
    const out: AtualizacaoTurmaFeed[] = [];

    for (const table of tables) {
      const rows = Array.from(table.querySelectorAll('tr'));
      if (rows.length < 2) continue;
      const firstCell = rows[0].querySelector('td');
      if (!firstCell) continue;
      const link = firstCell.querySelector('a');
      if (!link) continue;

      const fullFirstText = cleanText(firstCell);
      const dashIdx = fullFirstText.indexOf('-');
      const dataTexto = (dashIdx >= 0 ? fullFirstText.slice(0, dashIdx) : fullFirstText).trim();
      const disciplinaSemestre = cleanText(link);
      const idTurma = ID_TURMA_ONCLICK_REGEX.exec(link.getAttribute('onclick') ?? '')?.[1] ?? '';
      const tituloNoticia = cleanText(rows[1].querySelector('td'));
      if (!tituloNoticia && !disciplinaSemestre) continue;

      out.push({
        dataTexto,
        disciplinaSemestre,
        idTurma,
        tituloNoticia,
      });
    }
    return out;
  },

  parseTurmasDaHome(html: string): ParsedHomeTurma[] {
    const doc = parseHtmlDocument(html);
    const semestreAtual = cleanText(doc.querySelector('#info-usuario p.periodo-atual strong'));
    const portal = doc.querySelector('#turmas-portal');
    if (!portal) return [];

    const tables = Array.from(portal.querySelectorAll('table'));
    const turmaTable = tables.find((t) => t.querySelector('td.descricao form') !== null);
    if (!turmaTable) return [];

    const tbody = turmaTable.querySelector('tbody') ?? turmaTable;
    const rows = Array.from(tbody.children).filter((el) => el.tagName.toLowerCase() === 'tr');
    const viewState = CasAndJsfParser.extractViewStateFromDoc(doc) ?? '';

    const result: ParsedHomeTurma[] = [];
    let i = 0;
    while (i < rows.length) {
      const row = rows[i];
      const descForm = row.querySelector('td.descricao form');
      if (!descForm) {
        i += 1;
        continue;
      }

      const link = descForm.querySelector('a[onclick]');
      const nomeDisciplina = link ? cleanText(link) : cleanText(descForm);
      const onClick = link?.getAttribute('onclick') ?? '';
      const formId = (descForm.getAttribute('id') ?? '').trim();
      const frontEndIdTurma = FRONTEND_ID_REGEX.exec(onClick)?.[1] ?? '';

      const infoCells = Array.from(row.querySelectorAll('td.info'));
      const local = cleanText(infoCells[0]);
      const horarioCell = infoCells[1];

      const popUpEl = horarioCell?.querySelector('div.popUp');
      const horarioExtenso = popUpEl
        ? popUpEl.innerHTML
            .split(/<br\s*\/?>/i)
            .map((part) => parseHtmlFragmentText(part))
            .filter((s) => s.length > 0)
        : [];

      let horarioRaw = '';
      if (horarioCell) {
        const clone = horarioCell.cloneNode(true) as Element;
        clone.querySelectorAll('div.popUp, img').forEach((n) => n.remove());
        horarioRaw = cleanText(clone);
      }

      let idTurma = '';
      if (i + 1 < rows.length) {
        const hiddenTd = rows[i + 1].querySelector('td[id^="linha_"]');
        if (hiddenTd) {
          idTurma = (hiddenTd.getAttribute('id') ?? '').replace(/^linha_/, '');
          i += 1;
        }
      }

      if (frontEndIdTurma.length > 0) {
        const params: Record<string, string> = {};
        if (formId.length > 0) params[formId] = formId;
        Object.assign(params, CasAndJsfParser.extractJsfCljsParams(onClick));
        if (viewState.length > 0) params['javax.faces.ViewState'] = viewState;

        result.push({
          resumo: {
            idTurma,
            frontEndIdTurma,
            semestre: semestreAtual,
            codigoDisciplina: null,
            nomeDisciplina,
            numeroTurma: null,
            cargaHorariaHoras: null,
            docenteNome: null,
            local,
            codigoHorarioRaw: horarioRaw,
            horarioSlots: SigaaScheduleDecoder.decode(horarioRaw),
            horarioExtenso,
          },
          formId,
          formParams: params,
        });
      }
      i += 1;
    }

    return result;
  },

  parseExtratoRu(
    html: string,
    tabelaPrecos: TabelaPrecosRu = RuCalculator.defaultPrecosResolucao0037,
  ): CoreResult<SaldoExtratoRu> {
    const doc = parseHtmlDocument(html);
    const heading = Array.from(doc.querySelectorAll('h4')).find((h) =>
      CasAndJsfParser.normalizeLabel(cleanText(h)).includes('extrato no restaurante universitario'),
    );

    if (!heading) {
      if (
        doc.querySelector('#agenda-docente') !== null &&
        doc.querySelector('#formExibirExtrato') === null
      ) {
        return CoreResults.failure({
          kind: 'UnavailableForProfile',
          feature: 'Extrato RU',
          reason: 'Formulario de extrato RU indisponivel para este perfil discente.',
        });
      }
      return CoreResults.failure({
        kind: 'ParseError',
        source: 'discente.jsf#extratoRu',
        reason: 'Bloco de extrato RU nao encontrado na resposta.',
      });
    }

    const container = heading.parentElement;
    if (!container) {
      return CoreResults.failure({
        kind: 'ParseError',
        source: 'discente.jsf#extratoRu',
        reason: 'Container do extrato RU ausente.',
      });
    }

    const rows = Array.from(container.querySelectorAll('table tr'));
    let saldoAtual: number | null = null;
    let saldoAnterior: number | null = null;
    const transacoes: TransacaoRu[] = [];
    let grupoDetectado: GrupoAcessoRu = 'DESCONHECIDO';

    for (const row of rows) {
      const cols = Array.from(row.querySelectorAll('td'));
      if (cols.length < 3) continue;
      const dataHora = cleanText(cols[0]);
      const descricao = cleanText(cols[1]);
      const valorRaw = cleanText(cols[2]);
      const valorCentavos = RuCalculator.parseBrlToCentavos(valorRaw);
      if (valorCentavos === null) continue;

      const normDesc = CasAndJsfParser.normalizeLabel(descricao);
      if (normDesc === 'saldo') {
        saldoAtual = valorCentavos;
      } else if (normDesc === 'saldo anterior') {
        saldoAnterior = valorCentavos;
      } else {
        transacoes.push({
          dataHoraTexto: dataHora,
          descricao,
          valorCentavos,
        });
        if (grupoDetectado === 'DESCONHECIDO') {
          grupoDetectado = RuCalculator.detectGrupo(descricao);
        }
      }
    }

    if (saldoAtual === null && transacoes.length === 0) {
      return CoreResults.success({
        saldoAtualCentavos: 0,
        saldoAnterior7DiasCentavos: saldoAnterior,
        grupoAcesso: 'DESCONHECIDO',
        transacoesUltimos7Dias: [],
        refeicoesRestantes: null,
      });
    }

    const finalSaldo = saldoAtual ?? 0;
    const refeicoes =
      grupoDetectado !== 'DESCONHECIDO'
        ? RuCalculator.calculateRemainingMeals(finalSaldo, grupoDetectado, tabelaPrecos)
        : null;

    return CoreResults.success({
      saldoAtualCentavos: finalSaldo,
      saldoAnterior7DiasCentavos: saldoAnterior,
      grupoAcesso: grupoDetectado,
      transacoesUltimos7Dias: transacoes,
      refeicoesRestantes: refeicoes,
    });
  },
};

function parseAgendaRows(doc: Document): Record<string, string> {
  const map: Record<string, string> = {};
  for (const row of Array.from(doc.querySelectorAll('#agenda-docente table tr'))) {
    const cells = Array.from(row.querySelectorAll('td'));
    if (cells.length < 2) continue;
    const key = CasAndJsfParser.normalizeLabel(cleanText(cells[0])).replace(/:+$/, '');
    const value = cleanText(cells[1]);
    if (key.length > 0 && value.length > 0) {
      map[key] = value;
    }
  }
  return map;
}

function parseDoubleOrNull(raw?: string | null): number | null {
  if (!raw) return null;
  const trimmed = raw.trim();
  if (!trimmed || trimmed === '-' || trimmed === '--') return null;
  const parsed = Number.parseFloat(trimmed.replace(',', '.'));
  return Number.isFinite(parsed) ? parsed : null;
}

function parseIntOrNull(raw?: string | null): number | null {
  if (!raw) return null;
  const m = DIGITS_REGEX.exec(raw);
  if (!m) return null;
  const parsed = Number.parseInt(m[0], 10);
  return Number.isFinite(parsed) ? parsed : null;
}
