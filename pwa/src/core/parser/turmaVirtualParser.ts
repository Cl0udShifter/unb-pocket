import {
  CoreResults,
  type AnexoArquivoSigaa,
  type AvaliacaoTurma,
  type CoreResult,
  type MapaFrequenciaTurma,
  type MaterialAulaAva,
  type NotasParciaisTurma,
  type NoticiaTurmaAva,
  type NoticiaTurmaResumoAva,
  type RegistroFrequenciaDia,
  type TarefaTurma,
  type TopicoAulaAva,
  type TurmaDetalheAva,
  type UnidadeNotaTurma,
} from '../model/models';
import { CasAndJsfParser, parseHtmlDocument, parseHtmlFragmentText } from './casAndJsfParser';

export interface AtestadoTurmaItem {
  readonly codigoDisciplina: string;
  readonly nomeDisciplina: string;
  readonly docenteNome: string | null;
  readonly local: string | null;
  readonly numeroTurma: string | null;
  readonly horarioRaw: string;
}

export interface ParsedAtestadoMatricula {
  readonly turmas: readonly AtestadoTurmaItem[];
  readonly codigoVerificacao: string | null;
}

export interface ParsedAvaFormTurmaItem {
  readonly frontEndIdTurma: string;
  readonly codigoDisciplina: string;
  readonly nomeDisciplina: string;
  readonly cargaHorariaHoras: number | null;
  readonly semestre: string;
}

export interface ParsedAvaMaterialRef {
  readonly material: MaterialAulaAva;
  readonly formParams: Record<string, string>;
}

export interface ParsedAvaAnexoRef {
  readonly anexo: AnexoArquivoSigaa;
  readonly downloadUrl: string | null;
  readonly formParams: Record<string, string> | null;
}

export interface ParsedAvaNoticiaRef {
  readonly resumo: NoticiaTurmaResumoAva;
  readonly formParams: Record<string, string>;
}

export interface ParsedAvaHome {
  readonly detalhe: TurmaDetalheAva;
  readonly formTurmaItems: readonly ParsedAvaFormTurmaItem[];
  readonly menuActions: Record<string, Record<string, string>>;
  readonly materialRefs: Record<string, ParsedAvaMaterialRef>;
  readonly anexoRefs: Record<string, ParsedAvaAnexoRef>;
  readonly noticiaRefs: Record<string, ParsedAvaNoticiaRef>;
}

export interface ParsedAvaNoticiaCompleta {
  readonly noticia: NoticiaTurmaAva;
  readonly anexoRefs: Record<string, ParsedAvaAnexoRef>;
}

const AULAS_COUNT_REGEX = /(\d+)\s*\/\s*(\d+)/;
const PROGRESS_REGEX = /(\d+)\s*%/;
const FORM_TURMA_REGEX = /^([A-Z]{2,}\d+)\s*-\s*(.+?)\s*\((\d+)h\)\s*\(([^)]+)\)$/;
const ID_ARQUIVO_REGEX = /idArquivo=(\d+)/;
const VERIF_CODE_REGEX = /(?:código de verificação|codigo de verificacao)\s*:?\s*([a-zA-Z0-9]{6,20})/i;
const DIGITS_REGEX = /\d+/;
const NOTICIA_DATE_REGEX = /\d{2}\/\d{2}\/\d{4}(?:\s+\d{2}:\d{2})?/g;

const GENERIC_NOTICE_ACTIONS = new Set([
  'visualizar',
  'ver',
  'abrir',
  'ler',
  'detalhes',
  'ver mais',
  'saiba mais',
  'acessar',
]);

const UNREGISTERED_FREQUENCIA_MARKERS = [
  'nao registrad',
  'nao lancad',
  'nao informad',
  'sem registr',
  'sem chamad',
  'nao houve',
  'nao ministrad',
  'feriado',
  'cancelad',
  'suspens',
];

function cleanText(node: Node | null | undefined): string {
  return (node?.textContent ?? '').replace(/\s+/g, ' ').trim();
}

export const TurmaVirtualParser = {
  parseAtestadoMatricula(html: string): ParsedAtestadoMatricula {
    const doc = parseHtmlDocument(html);
    const rows = Array.from(
      doc.querySelectorAll('table.listagem tbody tr, table#matriculas tbody tr'),
    );
    const items: AtestadoTurmaItem[] = [];

    for (const row of rows) {
      const cols = Array.from(row.querySelectorAll('td'));
      if (cols.length < 5) continue;
      const codigo = cleanText(cols[0]);
      if (!codigo || !/\d/.test(codigo)) continue;

      const discTextLines = cols[1].innerHTML
        .split(/<br\s*\/?>/i)
        .map((part) => parseHtmlFragmentText(part))
        .filter((s) => s.length > 0);

      const nomeDisciplina = discTextLines[0] ?? '';
      const secondLine = discTextLines[1];
      const docenteNome =
        secondLine && !/^(tipo|local):/i.test(secondLine) ? secondLine : null;
      const localLine = discTextLines.find((l) => /local:/i.test(l));
      const local = localLine ? localLine.replace(/^.*?local:\s*/i, '').trim() : null;

      const numeroTurma = cleanText(cols[2]) || null;
      const horarioRaw = cleanText(cols[4]);

      items.push({
        codigoDisciplina: codigo,
        nomeDisciplina,
        docenteNome,
        local,
        numeroTurma,
        horarioRaw,
      });
    }

    const verif = VERIF_CODE_REGEX.exec(cleanText(doc.body))?.[1] ?? null;
    return { turmas: items, codigoVerificacao: verif };
  },

  parseAvaHome(html: string, frontEndIdTurma: string): CoreResult<ParsedAvaHome> {
    const doc = parseHtmlDocument(html);
    const codigo = cleanText(doc.querySelector('#linkCodigoTurma')).replace(/[-\s]+$/, '');
    const nome = cleanText(doc.querySelector('#linkNomeTurma'));
    if (!codigo && !nome) {
      return CoreResults.failure({
        kind: 'ParseError',
        source: 'ava/index.jsf',
        reason: 'Cabecalho da Turma Virtual (#nomeTurma) nao encontrado.',
      });
    }

    const periodoTurmaTexto = cleanText(doc.querySelector('#linkPeriodoTurma'));
    const viewState = CasAndJsfParser.extractViewStateFromDoc(doc) ?? '';

    const barraDireitaText = cleanText(doc.querySelector('#barraDireita'));
    const aulasMatch = AULAS_COUNT_REGEX.exec(barraDireitaText);
    const aulasMinistradas = aulasMatch ? Number.parseInt(aulasMatch[1], 10) : 0;
    const aulasTotal = aulasMatch ? Number.parseInt(aulasMatch[2], 10) : 0;

    const progressEl = doc.querySelector('#barraDireita .progress-bar');
    const progressMatch = progressEl
      ? PROGRESS_REGEX.exec(`${progressEl.getAttribute('style') ?? ''} ${cleanText(progressEl)}`)
      : null;
    const percentualAndamento = progressMatch
      ? Number.parseInt(progressMatch[1], 10)
      : aulasTotal > 0
        ? Math.trunc((aulasMinistradas * 100) / aulasTotal)
        : 0;

    const anexoRefs: Record<string, ParsedAvaAnexoRef> = {};
    const ultimaNoticia = parseUltimaNoticia(doc, anexoRefs);
    const { noticiasResumo, noticiaRefs } = parseHistoricoNoticias(doc, viewState, ultimaNoticia);

    const materialRefs: Record<string, ParsedAvaMaterialRef> = {};
    const topicosAula = parseTopicosAula(doc, viewState, materialRefs);
    const menuActions = parseFormMenuActions(doc, viewState);
    const formTurmaItems = parseFormTurmaItems(doc);

    const idTurmaInput = doc.querySelector('input[name="idTurma"]')?.getAttribute('value')?.trim();
    const idTurma = idTurmaInput && idTurmaInput.length > 0 ? idTurmaInput : frontEndIdTurma;

    return CoreResults.success({
      detalhe: {
        idTurma,
        frontEndIdTurma,
        codigoDisciplina: codigo,
        nomeDisciplina: nome,
        periodoTurmaTexto,
        aulasMinistradas,
        aulasTotal,
        percentualAndamento,
        ultimaNoticia,
        historicoNoticiasResumo: noticiasResumo,
        topicosAula,
      },
      formTurmaItems,
      menuActions,
      materialRefs,
      anexoRefs,
      noticiaRefs,
    });
  },

  parseAvaNoticiaCompleta(
    html: string,
    fallbackIdNoticia: string | null = null,
  ): CoreResult<ParsedAvaNoticiaCompleta> {
    const doc = parseHtmlDocument(html);
    const container =
      doc.querySelector('#ultimaNoticia') ??
      doc.querySelector('.conteudoNoticia')?.parentElement ??
      doc.querySelector('fieldset');
    if (!container) {
      return CoreResults.failure({
        kind: 'ParseError',
        source: 'ava/noticia',
        reason: 'Conteudo completo da noticia nao encontrado.',
      });
    }

    const headerText = cleanText(container.querySelector('h4, h3, legend'));
    const parts = headerText.split(' - ');
    const maybeLast = parts[parts.length - 1] ?? '';
    const dataHora = maybeLast.includes('/') ? maybeLast.trim() : '';
    const titulo =
      dataHora.length > 0 && parts.length > 1
        ? parts.slice(0, -1).join(' - ').trim()
        : headerText;

    const contentSpan = container.querySelector('span.conteudoNoticia, .conteudoNoticia, p');
    const conteudoHtml = contentSpan?.innerHTML ?? '';
    const conteudoTexto = cleanText(contentSpan);
    const smallItalics = Array.from(container.querySelectorAll('small i'));
    const autor = smallItalics.length > 0 ? cleanText(smallItalics[smallItalics.length - 1]) : null;

    const anexoRefs: Record<string, ParsedAvaAnexoRef> = {};
    const anexos = Array.from(container.querySelectorAll('a[href*="/sigaa/verArquivo"]')).map(
      (link, idx) => {
        const href = (link.getAttribute('href') ?? '').trim();
        const id = ID_ARQUIVO_REGEX.exec(href)?.[1] ?? `anexo_${idx}`;
        const anexo: AnexoArquivoSigaa = { id, nomeArquivo: cleanText(link) };
        anexoRefs[id] = { anexo, downloadUrl: href, formParams: null };
        return anexo;
      },
    );

    return CoreResults.success({
      noticia: {
        idNoticia: fallbackIdNoticia,
        titulo,
        dataHoraTexto: dataHora,
        conteudoHtml,
        conteudoTexto,
        autor,
        anexos,
      },
      anexoRefs,
    });
  },

  parseAvaFrequencia(html: string): CoreResult<MapaFrequenciaTurma> {
    const doc = parseHtmlDocument(html);
    const bodyText = cleanText(doc.body);
    if (!CasAndJsfParser.normalizeLabel(bodyText).includes('frequencia')) {
      return CoreResults.failure({
        kind: 'ParseError',
        source: 'ava/frequencia',
        reason: 'Mapa de frequencias nao encontrado.',
      });
    }

    const registros: RegistroFrequenciaDia[] = [];
    const rows = Array.from(
      doc.querySelectorAll('table.listagem tbody tr, fieldset table tbody tr'),
    );
    for (const row of rows) {
      const cols = Array.from(row.querySelectorAll('td'));
      if (cols.length < 2) continue;
      const data = cleanText(cols[0]);
      const situacao = cleanText(cols[1]);
      if (!data.includes('/')) continue;

      const normSit = CasAndJsfParser.normalizeLabel(situacao);
      const presente = normSit.includes('presente') || normSit.includes('presenca');
      const unregistered =
        normSit.length === 0 ||
        /^[-_]+$/.test(normSit) ||
        UNREGISTERED_FREQUENCIA_MARKERS.some((m) => normSit.includes(m));

      let faltasNoDia = 0;
      if (!presente && !unregistered) {
        const digits = DIGITS_REGEX.exec(situacao)?.[0];
        if (digits !== undefined) {
          faltasNoDia = Number.parseInt(digits, 10);
        } else if (normSit.includes('falta') || normSit.includes('ausent')) {
          faltasNoDia = 1;
        }
      }

      registros.push({
        dataTexto: data,
        situacaoTexto: situacao.length > 0 ? situacao : 'Não registrado',
        faltasNoDia,
        presente,
      });
    }

    const presencas = extractMetricInt(bodyText, 'Presenças Registradas') ?? 0;
    const comRegistro =
      extractMetricInt(bodyText, 'Número de Aulas com Registro de Frequência') ?? 0;
    const percRegistradas =
      extractMetricInt(
        bodyText,
        'Porcentagem de Frequência em relação as Aulas com Registro de Frequência',
      ) ?? 100;
    const aulasCh =
      extractMetricInt(bodyText, 'Número de Aulas definidas pela CH do Componente') ?? 0;
    const percCh =
      extractMetricInt(bodyText, 'Porcentagem de Frequência em relação a CH') ?? 100;

    const sumDailyFaltas = registros.reduce((acc, r) => acc + r.faltasNoDia, 0);
    const faltasComputadas = Math.max(comRegistro - presencas, sumDailyFaltas);
    const limiteMaximo = Math.floor(aulasCh * 0.25);
    const restantes = Math.max(0, limiteMaximo - faltasComputadas);
    const emRisco = aulasCh > 0 && (faltasComputadas > limiteMaximo || restantes <= 2);

    return CoreResults.success({
      presencasRegistradas: presencas,
      aulasComRegistro: comRegistro,
      percentualSobreRegistradas: percRegistradas,
      aulasDefinidasCh: aulasCh,
      percentualSobreCh: percCh,
      faltasComputadas,
      limiteMaximoFaltas: limiteMaximo,
      faltasRestantes: restantes,
      emRiscoReprovacaoPorFalta: emRisco,
      registrosDiarios: registros,
    });
  },

  parseAvaNotasParciais(html: string): CoreResult<NotasParciaisTurma> {
    const doc = parseHtmlDocument(html);
    const normalizedBody = CasAndJsfParser.normalizeLabel(cleanText(doc.body));
    if (normalizedBody.includes('ainda nao foram lancadas notas')) {
      const avisoEl = Array.from(
        doc.querySelectorAll('.aviso, .painelUsuario, fieldset p, #mensagens p'),
      ).find((el) =>
        CasAndJsfParser.normalizeLabel(cleanText(el)).includes('ainda nao foram lancadas notas'),
      );
      return CoreResults.success({
        notasDisponiveis: false,
        mensagemAviso: avisoEl ? cleanText(avisoEl) : 'Ainda não foram lançadas notas.',
        unidades: [],
        mediaFinal: null,
        faltas: null,
        situacao: null,
      });
    }

    const table = doc.querySelector('table.tabelaRelatorio, table.listagem');
    if (!table) {
      return CoreResults.failure({
        kind: 'ParseError',
        source: 'ava/notas',
        reason: 'Tabela de notas parciais nao encontrada.',
      });
    }

    const headers = Array.from(table.querySelectorAll('thead th, tr:first-child th')).map((th) =>
      cleanText(th),
    );
    const dataRow = Array.from(table.querySelectorAll('tbody tr')).find(
      (r) => r.querySelector('td') !== null,
    );
    if (!dataRow) {
      return CoreResults.success({
        notasDisponiveis: false,
        mensagemAviso: 'Sem notas lancadas.',
        unidades: [],
        mediaFinal: null,
        faltas: null,
        situacao: null,
      });
    }

    const cells = Array.from(dataRow.querySelectorAll('td')).map((td) => cleanText(td));
    const unidades: UnidadeNotaTurma[] = [];
    let mediaFinal: string | null = null;
    let faltas: number | null = null;
    let situacao: string | null = null;

    cells.forEach((value, idx) => {
      const header = headers[idx] ?? `Coluna ${idx + 1}`;
      const normHeader = CasAndJsfParser.normalizeLabel(header);
      if (normHeader.includes('media') || normHeader.includes('resultado')) {
        mediaFinal = value || null;
      } else if (normHeader.includes('falta')) {
        const parsed = Number.parseInt(value, 10);
        faltas = Number.isFinite(parsed) ? parsed : null;
      } else if (normHeader.includes('situacao')) {
        situacao = value || null;
      } else if (
        normHeader.includes('matricula') ||
        normHeader.includes('nome') ||
        normHeader.includes('discente')
      ) {
        // Skip student identifier columns
      } else {
        unidades.push({ nome: header, valor: value || null });
      }
    });

    return CoreResults.success({
      notasDisponiveis: true,
      mensagemAviso: null,
      unidades,
      mediaFinal,
      faltas,
      situacao,
    });
  },

  parseAvaAvaliacoes(html: string): CoreResult<AvaliacaoTurma[]> {
    const doc = parseHtmlDocument(html);
    const table = doc.querySelector('table.listagem');
    if (!table) {
      if (CasAndJsfParser.normalizeLabel(cleanText(doc.body)).includes('avaliac')) {
        return CoreResults.success([]);
      }
      return CoreResults.failure({
        kind: 'ParseError',
        source: 'ava/avaliacoes',
        reason: 'Pagina de avaliacoes invalida.',
      });
    }

    const items: AvaliacaoTurma[] = [];
    for (const row of Array.from(table.querySelectorAll('tbody tr'))) {
      const cols = Array.from(row.querySelectorAll('td'));
      if (cols.length < 2) continue;
      const dataTexto = cleanText(cols[0]);
      const horaTexto = cols.length >= 3 ? cleanText(cols[1]) || null : null;
      const descricao = cleanText(cols[cols.length - 1]);
      if (!descricao) continue;
      items.push({ dataTexto, horaTexto, descricao });
    }
    return CoreResults.success(items);
  },

  parseAvaTarefas(html: string): CoreResult<TarefaTurma[]> {
    const doc = parseHtmlDocument(html);
    const table = doc.querySelector('table.listagem');
    if (!table) {
      if (CasAndJsfParser.normalizeLabel(cleanText(doc.body)).includes('tarefa')) {
        return CoreResults.success([]);
      }
      return CoreResults.failure({
        kind: 'ParseError',
        source: 'ava/tarefas',
        reason: 'Pagina de tarefas invalida.',
      });
    }

    const items: TarefaTurma[] = [];
    for (const row of Array.from(table.querySelectorAll('tbody tr'))) {
      const cols = Array.from(row.querySelectorAll('td'));
      if (cols.length < 2) continue;
      const titulo = cleanText(cols[0]);
      if (!titulo) continue;
      const periodoEntrega = cleanText(cols[1]);
      const notaMaxima = cols[2] ? cleanText(cols[2]) || null : null;
      const possuiArquivo = cols[3]
        ? CasAndJsfParser.normalizeLabel(cleanText(cols[3])).includes('sim') ||
          cols[3].querySelector('img, a') !== null
        : false;
      const emGrupo = cols[4]
        ? CasAndJsfParser.normalizeLabel(cleanText(cols[4])).includes('sim')
        : false;
      items.push({ titulo, periodoEntrega, notaMaxima, possuiArquivo, emGrupo });
    }
    return CoreResults.success(items);
  },

  parseAvaMenuActions(html: string): Record<string, Record<string, string>> {
    const doc = parseHtmlDocument(html);
    const viewState = CasAndJsfParser.extractViewStateFromDoc(doc) ?? '';
    return parseFormMenuActions(doc, viewState);
  },
};

function parseUltimaNoticia(
  doc: Document,
  anexoRefs: Record<string, ParsedAvaAnexoRef>,
): NoticiaTurmaAva | null {
  const box = doc.querySelector('#ultimaNoticia');
  if (!box) return null;

  const header = cleanText(box.querySelector('h4'));
  const parts = header.split(' - ');
  const maybeLast = parts[parts.length - 1] ?? '';
  const dataHora = maybeLast.includes('/') ? maybeLast.trim() : '';
  const titulo =
    dataHora.length > 0 && parts.length > 1
      ? parts.slice(0, -1).join(' - ').trim()
      : header;

  const span = box.querySelector('span.conteudoNoticia');
  const smallItalics = Array.from(box.querySelectorAll('small i'));
  const autor = smallItalics.length > 0 ? cleanText(smallItalics[smallItalics.length - 1]) : null;
  const anexos = Array.from(box.querySelectorAll('a[href*="/sigaa/verArquivo"]')).map(
    (link, idx) => {
      const href = (link.getAttribute('href') ?? '').trim();
      const id = ID_ARQUIVO_REGEX.exec(href)?.[1] ?? `ultima_${idx}`;
      const anexo: AnexoArquivoSigaa = { id, nomeArquivo: cleanText(link) };
      anexoRefs[id] = { anexo, downloadUrl: href, formParams: null };
      return anexo;
    },
  );

  if (!titulo && !span) return null;
  return {
    idNoticia: null,
    titulo,
    dataHoraTexto: dataHora,
    conteudoHtml: span?.innerHTML ?? '',
    conteudoTexto: cleanText(span),
    autor,
    anexos,
  };
}

function parseHistoricoNoticias(
  doc: Document,
  viewState: string,
  ultimaNoticia: NoticiaTurmaAva | null,
): {
  noticiasResumo: NoticiaTurmaResumoAva[];
  noticiaRefs: Record<string, ParsedAvaNoticiaRef>;
} {
  const noticiasResumo: NoticiaTurmaResumoAva[] = [];
  const noticiaRefs: Record<string, ParsedAvaNoticiaRef> = {};

  for (const form of Array.from(doc.querySelectorAll('#barraDireita form'))) {
    const idInput = form.querySelector('input[name="id"]')?.getAttribute('value')?.trim();
    const link = form.querySelector('a[onclick]');
    if (!idInput || !link) continue;

    const { dataHora, titulo } = extractNoticiaResumoHeader(form, link, ultimaNoticia);
    const formId = (form.getAttribute('id') ?? '').trim();
    const params: Record<string, string> = {};
    if (formId.length > 0) params[formId] = formId;
    Object.assign(params, CasAndJsfParser.extractJsfCljsParams(link.getAttribute('onclick') ?? ''));
    params['id'] = idInput;
    if (viewState.length > 0) params['javax.faces.ViewState'] = viewState;

    const resumo: NoticiaTurmaResumoAva = { idNoticia: idInput, dataHoraTexto: dataHora, titulo };
    noticiasResumo.push(resumo);
    noticiaRefs[idInput] = { resumo, formParams: params };
  }

  return { noticiasResumo, noticiaRefs };
}

function extractNoticiaResumoHeader(
  form: Element,
  link: Element,
  ultimaNoticia: NoticiaTurmaAva | null,
): { dataHora: string; titulo: string } {
  const segmentNodes = collectPrecedingNoticeNodes(form);
  const precedingText = renderNodesText(segmentNodes);

  const precedingDates = Array.from(precedingText.matchAll(NOTICIA_DATE_REGEX)).map((m) => m[0]);
  const formDate = Array.from(cleanText(form).matchAll(NOTICIA_DATE_REGEX))[0]?.[0] ?? '';
  const dataHora =
    precedingDates.length > 0 ? precedingDates[precedingDates.length - 1] : formDate;

  const candidateElements: Element[] = [];
  const inspectNodes = [
    ...segmentNodes,
    ...Array.from(form.children).filter((c) => c.tagName.toLowerCase() !== 'a'),
  ];
  for (const node of inspectNodes) {
    if (node.nodeType === 1) {
      const el = node as Element;
      const tag = el.tagName.toLowerCase();
      if (tag === 'i' || tag === 'b' || tag === 'strong') {
        candidateElements.push(el);
      }
      el.querySelectorAll('i, b, strong').forEach((inner) => candidateElements.push(inner));
    }
  }

  const styledCandidates = candidateElements
    .map((el) => cleanText(el))
    .filter((t) => t.length > 0 && !isGenericNoticeAction(t));

  const linkText = cleanText(link);
  const linkTitle = !isGenericNoticeAction(linkText) ? linkText : null;

  const leftoverRaw = precedingText
    .replace(NOTICIA_DATE_REGEX, ' ')
    .replace(/\s+/g, ' ')
    .replace(/^[\s\-:|–•]+|[\s\-:|–•]+$/g, '');
  const plainLeftover =
    leftoverRaw.length > 0 && !isGenericNoticeAction(leftoverRaw) ? leftoverRaw : null;

  const titulo =
    styledCandidates[styledCandidates.length - 1] ??
    linkTitle ??
    plainLeftover ??
    (ultimaNoticia && dataHora && ultimaNoticia.dataHoraTexto === dataHora && ultimaNoticia.titulo
      ? ultimaNoticia.titulo
      : 'Comunicado');

  return { dataHora, titulo };
}

function collectPrecedingNoticeNodes(form: Element): Node[] {
  const nodes: Node[] = [];
  let cursor = form.previousSibling;
  while (cursor) {
    if (cursor.nodeType === 1 && isNoticeBoundary(cursor as Element)) {
      break;
    }
    nodes.unshift(cursor);
    cursor = cursor.previousSibling;
  }

  const hasMetadataInSiblings = renderNodesText(nodes).length > 0;
  const hasDateInForm = Array.from(cleanText(form).matchAll(NOTICIA_DATE_REGEX)).length > 0;
  if (!hasMetadataInSiblings && !hasDateInForm) {
    const parent = form.parentElement;
    if (
      parent &&
      parent.id !== 'barraDireita' &&
      parent.querySelectorAll('form').length === 1
    ) {
      const prevContainer = parent.previousElementSibling;
      if (prevContainer && !isNoticeBoundary(prevContainer) && cleanText(prevContainer).length > 0) {
        return [prevContainer];
      }
    }
  }
  return nodes;
}

function isNoticeBoundary(el: Element): boolean {
  const tag = el.tagName.toLowerCase();
  if (['form', 'hr', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'table'].includes(tag)) {
    return true;
  }
  if (el.querySelector('form') !== null) return true;
  if (
    el.classList.contains('rich-panel') ||
    el.classList.contains('rich-panel-header') ||
    el.classList.contains('progress-bar')
  ) {
    return true;
  }
  const norm = CasAndJsfParser.normalizeLabel(cleanText(el));
  return norm.includes('ministradas') || norm.includes('carga horaria');
}

function renderNodesText(nodes: readonly Node[]): string {
  return nodes
    .map((n) => n.textContent ?? '')
    .join(' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function isGenericNoticeAction(text: string): boolean {
  const cleaned = CasAndJsfParser.normalizeLabel(text)
    .replace(/^[()[\].:\s]+|[()[\].:\s]+$/g, '')
    .trim();
  return cleaned.length === 0 || GENERIC_NOTICE_ACTIONS.has(cleaned);
}

function parseTopicosAula(
  doc: Document,
  viewState: string,
  materialRefs: Record<string, ParsedAvaMaterialRef>,
): TopicoAulaAva[] {
  const avaHiddenInputs: Record<string, string> = {};
  doc.querySelectorAll('form#formAva input[type="hidden"]').forEach((input) => {
    const name = (input.getAttribute('name') ?? '').trim();
    if (name.length > 0) {
      avaHiddenInputs[name] = input.getAttribute('value') ?? '';
    }
  });

  const containers = Array.from(
    doc.querySelectorAll('#formAva div.topico-aula, div.topico-aula'),
  );
  return containers.map((el, index) => {
    const tituloRaw = cleanText(el.querySelector('div.titulo'));
    const periodoMatch = /\(([^)]+\d{4}[^)]*)\)\s*$/.exec(tituloRaw);
    const periodoTexto = periodoMatch ? periodoMatch[1].trim() : null;
    const titulo = periodoMatch
      ? tituloRaw.slice(0, periodoMatch.index).trim()
      : tituloRaw;

    const conteudoParagraphs = Array.from(
      el.querySelectorAll(':scope > div.conteudotopico > p, div.conteudotopico > p'),
    );
    const conteudoTexto = conteudoParagraphs
      .map((p) => cleanText(p))
      .filter((s) => s.length > 0)
      .join(' ');

    const materiais: MaterialAulaAva[] = [];
    el.querySelectorAll('div.conteudotopico div.item a[onclick]').forEach((link) => {
      const onClick = link.getAttribute('onclick') ?? '';
      const paramsMap = CasAndJsfParser.extractJsfCljsParams(onClick);
      const hashId = paramsMap['id'];
      if (!hashId) return;

      const itemContainer = link.closest('div.item');
      const linkNome = cleanText(link);
      const ext = resolveMaterialExtension(itemContainer, linkNome);

      const fullParams: Record<string, string> = {
        ...avaHiddenInputs,
        formAva: 'formAva',
        'formAva:idTopicoSelecionado': String(index),
        ...paramsMap,
      };
      if (viewState.length > 0) fullParams['javax.faces.ViewState'] = viewState;

      const material: MaterialAulaAva = {
        materialId: hashId,
        nome: linkNome,
        tipoExtensao: ext,
        topicoIndex: index,
      };
      materialRefs[hashId] = { material, formParams: fullParams };
      materiais.push(material);
    });

    return {
      index,
      titulo,
      periodoTexto,
      conteudoTexto,
      materiais,
    };
  });
}

function resolveMaterialExtension(itemContainer: Element | null, linkNome: string): string {
  const specificImg = itemContainer?.querySelector(
    'img[src*="/porta_arquivos/icones/"], img[src*="/icones/"]',
  );
  const fallbackImg = itemContainer
    ? Array.from(itemContainer.querySelectorAll('img')).find((img) => {
        const src = (img.getAttribute('src') ?? '').toLowerCase();
        return (
          src.length > 0 &&
          !src.includes('indicator') &&
          !src.includes('spacer') &&
          !src.includes('loading') &&
          !src.includes('spinner') &&
          !src.includes('ajax')
        );
      })
    : undefined;

  const iconSrc = (specificImg ?? fallbackImg)?.getAttribute('src') ?? '';
  const filePart = iconSrc.split('/').pop()?.split('?')[0]?.split('.')[0]?.trim().toLowerCase() ?? '';
  if (
    filePart.length > 0 &&
    !['indicator', 'spacer', 'loading', 'spinner', 'ajax', 'blank'].includes(filePart)
  ) {
    return filePart;
  }

  const lastDot = linkNome.lastIndexOf('.');
  if (lastDot >= 0) {
    const ext = linkNome.slice(lastDot + 1).trim().toLowerCase();
    if (ext.length >= 2 && ext.length <= 5 && /^[a-z0-9]+$/.test(ext)) {
      return ext;
    }
  }
  return '';
}

function parseFormMenuActions(
  doc: Document,
  viewState: string,
): Record<string, Record<string, string>> {
  const form = doc.querySelector('form#formMenu');
  if (!form) return {};
  const formId = (form.getAttribute('id') ?? '').trim() || 'formMenu';
  const result: Record<string, Record<string, string>> = {};

  form.querySelectorAll('a[onclick]').forEach((link) => {
    const itemDiv = link.querySelector('div.itemMenu');
    const label = itemDiv ? cleanText(itemDiv) : cleanText(link);
    if (!label) return;
    const params: Record<string, string> = {
      [formId]: formId,
      ...CasAndJsfParser.extractJsfCljsParams(link.getAttribute('onclick') ?? ''),
    };
    if (viewState.length > 0) params['javax.faces.ViewState'] = viewState;
    result[CasAndJsfParser.normalizeLabel(label)] = params;
  });

  return result;
}

function parseFormTurmaItems(doc: Document): ParsedAvaFormTurmaItem[] {
  const form = doc.querySelector('form#formTurma');
  if (!form) return [];
  const items: ParsedAvaFormTurmaItem[] = [];

  form.querySelectorAll('a.linkTurma').forEach((link) => {
    const span = link.querySelector('span');
    const text = span ? cleanText(span) : cleanText(link);
    const m = FORM_TURMA_REGEX.exec(text);
    if (!m) return;
    const params = CasAndJsfParser.extractJsfCljsParams(link.getAttribute('onclick') ?? '');
    const frontEndIdTurma = params['frontEndIdTurma'];
    if (!frontEndIdTurma) return;
    const ch = Number.parseInt(m[3], 10);
    items.push({
      frontEndIdTurma,
      codigoDisciplina: m[1].trim(),
      nomeDisciplina: m[2].trim(),
      cargaHorariaHoras: Number.isFinite(ch) ? ch : null,
      semestre: m[4].trim(),
    });
  });

  return items;
}

function extractMetricInt(bodyText: string, label: string): number | null {
  const escaped = label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const regex = new RegExp(`${escaped}\\s*:\\s*(\\d+)\\s*%?`, 'i');
  const m = regex.exec(bodyText);
  if (!m) return null;
  const val = Number.parseInt(m[1], 10);
  return Number.isFinite(val) ? val : null;
}
