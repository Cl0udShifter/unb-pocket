export type Unsubscribe = () => void;

export interface ObservableValue<T> {
  getSnapshot(): T;
  subscribe(listener: (value: T) => void): Unsubscribe;
}

export class MutableStateValue<T> implements ObservableValue<T> {
  private current: T;
  private readonly listeners = new Set<(value: T) => void>();

  constructor(initial: T) {
    this.current = initial;
  }

  getSnapshot = (): T => this.current;

  get value(): T {
    return this.current;
  }

  set value(next: T) {
    if (Object.is(this.current, next)) return;
    this.current = next;
    for (const listener of this.listeners) {
      listener(next);
    }
  }

  update(updater: (prev: T) => T): void {
    this.value = updater(this.current);
  }

  subscribe = (listener: (value: T) => void): Unsubscribe => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };
}

// --- 1. Tipos Base e Envelopes Reativos ---

export type DataOrigin = 'CACHE' | 'REMOTE' | 'FALLBACK';

export interface SyncedData<T> {
  readonly value: T;
  readonly origin: DataOrigin;
  readonly syncedAtEpochMillis: number;
  readonly staleWarning?: CoreError | null;
}

export interface PortalHomeSyncSummary {
  readonly syncedAtEpochMillis: number;
  readonly turmasCount: number;
  readonly atualizacoesCount: number;
  readonly saldoRuAtualizado: boolean;
  readonly anyDataChanged: boolean;
}

export type PreloadPhase = 'IDLE' | 'RUNNING_ESSENTIAL' | 'RUNNING_BACKGROUND' | 'COMPLETED';

export interface PreloadProgress {
  readonly phase: PreloadPhase;
  readonly completedSteps: number;
  readonly totalSteps: number;
  readonly currentTarget: string | null;
  readonly updatedAnyData: boolean;
  readonly lastError: CoreError | null;
}

export const IDLE_PRELOAD_PROGRESS: PreloadProgress = {
  phase: 'IDLE',
  completedSteps: 0,
  totalSteps: 0,
  currentTarget: null,
  updatedAnyData: false,
  lastError: null,
};

// --- 2. Erros Tratados (CoreError) ---

export type CoreError =
  | { readonly kind: 'InvalidCredentials' }
  | { readonly kind: 'SessionExpired' }
  | { readonly kind: 'NoStoredCredentials' }
  | { readonly kind: 'NetworkUnavailable' }
  | {
      readonly kind: 'HttpFailure';
      readonly statusCode: number;
      readonly endpoint: string;
      readonly message?: string | null;
    }
  | {
      readonly kind: 'ParseError';
      readonly source: string;
      readonly reason: string;
      readonly recoverableFallbackAvailable?: boolean;
    }
  | {
      readonly kind: 'ReadOnlyViolation';
      readonly blockedTarget: string;
      readonly reason: string;
    }
  | {
      readonly kind: 'PdfExtractionError';
      readonly documentType: string;
      readonly reason: string;
    }
  | {
      readonly kind: 'StorageError';
      readonly operation: string;
      readonly reason: string;
    }
  | {
      readonly kind: 'UnavailableForProfile';
      readonly feature: string;
      readonly reason: string;
    }
  | {
      readonly kind: 'Unexpected';
      readonly message: string;
    };

export type CoreResult<T> =
  | { readonly ok: true; readonly value: T }
  | { readonly ok: false; readonly error: CoreError };

export const CoreResults = {
  success<T>(value: T): CoreResult<T> {
    return { ok: true, value };
  },
  failure<T = never>(error: CoreError): CoreResult<T> {
    return { ok: false, error };
  },
};

// --- 3.1 Autenticacao ---

export interface CredentialsInput {
  readonly matricula: string;
  readonly password: string;
  readonly rememberCredentials?: boolean;
}

export type AuthState =
  | { readonly kind: 'Unauthenticated' }
  | { readonly kind: 'Reauthenticating'; readonly matricula: string }
  | {
      readonly kind: 'Authenticated';
      readonly matricula: string;
      readonly idDiscente: string | null;
      readonly sessionExpiresAtEpochMillis: number | null;
      readonly hasStoredCredentials: boolean;
    };

// --- 3.2 Restaurante Universitario ---

export type CampusRu =
  | 'DARCY_RIBEIRO'
  | 'CEILANDIA'
  | 'GAMA'
  | 'PLANALTINA'
  | 'FAZENDA_AGUA_LIMPA';

export interface CampusRuMeta {
  readonly id: CampusRu;
  readonly slug: string;
  readonly displayName: string;
  readonly headingKeyword: string;
  readonly operatingDaysSummary: string;
  readonly shortBadge: string;
}

export const CAMPUS_RU_LIST: readonly CampusRuMeta[] = [
  {
    id: 'DARCY_RIBEIRO',
    slug: 'darcy-ribeiro',
    displayName: 'Darcy Ribeiro',
    headingKeyword: 'Darcy Ribeiro',
    operatingDaysSummary: 'Segunda a Domingo',
    shortBadge: 'DARCY',
  },
  {
    id: 'CEILANDIA',
    slug: 'ceilandia',
    displayName: 'Ceilândia',
    headingKeyword: 'Ceilândia',
    operatingDaysSummary: 'Segunda a Sábado',
    shortBadge: 'FCE',
  },
  {
    id: 'GAMA',
    slug: 'gama',
    displayName: 'Gama',
    headingKeyword: 'Gama',
    operatingDaysSummary: 'Segunda a Sexta',
    shortBadge: 'FGA',
  },
  {
    id: 'PLANALTINA',
    slug: 'planaltina',
    displayName: 'Planaltina',
    headingKeyword: 'Planaltina',
    operatingDaysSummary: 'Segunda a Domingo',
    shortBadge: 'FUP',
  },
  {
    id: 'FAZENDA_AGUA_LIMPA',
    slug: 'fazenda-agua-limpa',
    displayName: 'Fazenda Água Limpa',
    headingKeyword: 'Fazenda Água Limpa',
    operatingDaysSummary: 'Segunda a Sexta',
    shortBadge: 'FAL',
  },
];

export const CAMPUS_RU_BY_ID: Readonly<Record<CampusRu, CampusRuMeta>> = {
  DARCY_RIBEIRO: CAMPUS_RU_LIST[0],
  CEILANDIA: CAMPUS_RU_LIST[1],
  GAMA: CAMPUS_RU_LIST[2],
  PLANALTINA: CAMPUS_RU_LIST[3],
  FAZENDA_AGUA_LIMPA: CAMPUS_RU_LIST[4],
};

export type TipoRefeicao = 'CAFE_DA_MANHA' | 'ALMOCO' | 'JANTAR';

export interface TipoRefeicaoMeta {
  readonly id: TipoRefeicao;
  readonly pageIndex: number;
  readonly displayName: string;
  readonly shortLabel: string;
}

export const TIPO_REFEICAO_LIST: readonly TipoRefeicaoMeta[] = [
  { id: 'CAFE_DA_MANHA', pageIndex: 0, displayName: 'Café da Manhã', shortLabel: 'Café' },
  { id: 'ALMOCO', pageIndex: 1, displayName: 'Almoço', shortLabel: 'Almoço' },
  { id: 'JANTAR', pageIndex: 2, displayName: 'Jantar', shortLabel: 'Jantar' },
];

export const TIPO_REFEICAO_BY_ID: Readonly<Record<TipoRefeicao, TipoRefeicaoMeta>> = {
  CAFE_DA_MANHA: TIPO_REFEICAO_LIST[0],
  ALMOCO: TIPO_REFEICAO_LIST[1],
  JANTAR: TIPO_REFEICAO_LIST[2],
};

export type DiaSemanaRu =
  | 'SEGUNDA'
  | 'TERCA'
  | 'QUARTA'
  | 'QUINTA'
  | 'SEXTA'
  | 'SABADO'
  | 'DOMINGO';

export interface DiaSemanaRuMeta {
  readonly id: DiaSemanaRu;
  readonly dayOfWeekIso: number;
  readonly shortLabel: string;
  readonly displayName: string;
}

export const DIA_SEMANA_RU_LIST: readonly DiaSemanaRuMeta[] = [
  { id: 'SEGUNDA', dayOfWeekIso: 1, shortLabel: 'SEG', displayName: 'Segunda-feira' },
  { id: 'TERCA', dayOfWeekIso: 2, shortLabel: 'TER', displayName: 'Terça-feira' },
  { id: 'QUARTA', dayOfWeekIso: 3, shortLabel: 'QUA', displayName: 'Quarta-feira' },
  { id: 'QUINTA', dayOfWeekIso: 4, shortLabel: 'QUI', displayName: 'Quinta-feira' },
  { id: 'SEXTA', dayOfWeekIso: 5, shortLabel: 'SEX', displayName: 'Sexta-feira' },
  { id: 'SABADO', dayOfWeekIso: 6, shortLabel: 'SÁB', displayName: 'Sábado' },
  { id: 'DOMINGO', dayOfWeekIso: 7, shortLabel: 'DOM', displayName: 'Domingo' },
];

export function diaSemanaRuFromIso(iso: number): DiaSemanaRu | null {
  return DIA_SEMANA_RU_LIST.find((d) => d.dayOfWeekIso === iso)?.id ?? null;
}

export type Alergenico =
  | 'COGUMELO'
  | 'LEITE'
  | 'MEL'
  | 'PIMENTA'
  | 'SOJA'
  | 'GLUTEN'
  | 'AMENDOIM'
  | 'OLEAGINOSA'
  | 'OVO'
  | 'SUINO';

export interface AlergenicoMeta {
  readonly id: Alergenico;
  readonly labelPtBr: string;
  readonly legendOrderIndex: number;
}

export const ALERGENICO_LIST: readonly AlergenicoMeta[] = [
  { id: 'COGUMELO', labelPtBr: 'Cogumelo', legendOrderIndex: 0 },
  { id: 'LEITE', labelPtBr: 'Leite e derivados', legendOrderIndex: 1 },
  { id: 'MEL', labelPtBr: 'Mel', legendOrderIndex: 2 },
  { id: 'PIMENTA', labelPtBr: 'Pimenta', legendOrderIndex: 3 },
  { id: 'SOJA', labelPtBr: 'Soja', legendOrderIndex: 4 },
  { id: 'GLUTEN', labelPtBr: 'Trigo/Glúten', legendOrderIndex: 5 },
  { id: 'AMENDOIM', labelPtBr: 'Amendoim', legendOrderIndex: 6 },
  { id: 'OLEAGINOSA', labelPtBr: 'Oleaginosa', legendOrderIndex: 7 },
  { id: 'OVO', labelPtBr: 'Ovo', legendOrderIndex: 8 },
  { id: 'SUINO', labelPtBr: 'Suíno', legendOrderIndex: 9 },
];

export const ALERGENICO_BY_ID: Readonly<Record<Alergenico, AlergenicoMeta>> = {
  COGUMELO: ALERGENICO_LIST[0],
  LEITE: ALERGENICO_LIST[1],
  MEL: ALERGENICO_LIST[2],
  PIMENTA: ALERGENICO_LIST[3],
  SOJA: ALERGENICO_LIST[4],
  GLUTEN: ALERGENICO_LIST[5],
  AMENDOIM: ALERGENICO_LIST[6],
  OLEAGINOSA: ALERGENICO_LIST[7],
  OVO: ALERGENICO_LIST[8],
  SUINO: ALERGENICO_LIST[9],
};

export type GrupoAcessoRu =
  | 'GRUPO_1'
  | 'GRUPO_2'
  | 'GRUPO_3'
  | 'GRUPO_4'
  | 'EXECUTIVO'
  | 'DESCONHECIDO';

export interface GrupoAcessoRuMeta {
  readonly id: GrupoAcessoRu;
  readonly codigo: string;
  readonly roman: string;
  readonly descricao: string;
}

export const GRUPO_ACESSO_RU_BY_ID: Readonly<Record<GrupoAcessoRu, GrupoAcessoRuMeta>> = {
  GRUPO_1: {
    id: 'GRUPO_1',
    codigo: 'Grupo 1',
    roman: 'I',
    descricao: 'Estudantes PNAES / CadÚnico / Cotas Sociais / Indígenas / Quilombolas',
  },
  GRUPO_2: {
    id: 'GRUPO_2',
    codigo: 'Grupo 2',
    roman: 'II',
    descricao: 'Estudantes de graduação e pós-graduação (ampla concorrência)',
  },
  GRUPO_3: {
    id: 'GRUPO_3',
    codigo: 'Grupo 3',
    roman: 'III',
    descricao: 'Servidores, terceirizados e visitantes',
  },
  GRUPO_4: {
    id: 'GRUPO_4',
    codigo: 'Grupo 4',
    roman: 'IV',
    descricao: 'Estudantes de graduação oriundos de escola pública',
  },
  EXECUTIVO: {
    id: 'EXECUTIVO',
    codigo: 'Executivo',
    roman: 'Executivo',
    descricao: 'Restaurante Executivo (almoço por quilo)',
  },
  DESCONHECIDO: {
    id: 'DESCONHECIDO',
    codigo: 'Desconhecido',
    roman: '-',
    descricao: 'Grupo ainda não identificado no extrato',
  },
};

export type CardapioParserStatus =
  | 'PARSED_STRUCTURED'
  | 'FALLBACK_IMAGE_ONLY'
  | 'FALLBACK_PDF_ONLY';

export interface ItemCardapioRu {
  readonly categoria: string;
  readonly prato: string;
  readonly alergenicos: readonly Alergenico[];
}

export interface CardapioDiaRu {
  readonly dataTexto: string;
  readonly diaSemana: string;
  readonly refeicoes: Partial<Record<TipoRefeicao, readonly ItemCardapioRu[]>>;
}

export interface CardapioSemanalRu {
  readonly campus: CampusRu;
  readonly semanaLabel: string;
  readonly pdfUrl: string;
  readonly fallbackImageUrl: string | null;
  readonly localPdfPath: string | null;
  readonly modifiedGmt: string;
  readonly parserStatus: CardapioParserStatus;
  readonly dias: readonly CardapioDiaRu[];
}

export interface RuSelecaoInicial {
  readonly dayIndex: number;
  readonly refeicao: TipoRefeicao;
}

export interface TransacaoRu {
  readonly dataHoraTexto: string;
  readonly descricao: string;
  readonly valorCentavos: number;
}

export interface RefeicoesRestantes {
  readonly cafesRestantes: number;
  readonly almocosOuJantaresRestantes: number;
  readonly trocoCafeCentavos: number;
  readonly trocoAlmocoJantarCentavos: number;
  readonly gratuito: boolean;
}

export interface SaldoExtratoRu {
  readonly saldoAtualCentavos: number;
  readonly saldoAnterior7DiasCentavos: number | null;
  readonly grupoAcesso: GrupoAcessoRu;
  readonly transacoesUltimos7Dias: readonly TransacaoRu[];
  readonly refeicoesRestantes: RefeicoesRestantes | null;
}

export interface PrecoGrupoRu {
  readonly grupo: GrupoAcessoRu;
  readonly publicoAlvo: string;
  readonly cafeCentavos: number | null;
  readonly almocoCentavos: number | null;
  readonly jantarCentavos: number | null;
  readonly porKgCentavos: number | null;
}

export interface TabelaPrecosRu {
  readonly resolucaoReferencia: string;
  readonly modifiedGmt: string;
  readonly grupos: readonly PrecoGrupoRu[];
  readonly formasPagamento: readonly string[];
}

export interface NoticiaRu {
  readonly id: number;
  readonly dateIso: string;
  readonly title: string;
  readonly excerptText: string;
  readonly contentHtml: string;
  readonly link: string;
  readonly featuredImageUrl: string | null;
  readonly anexoPdfUrls: readonly string[];
}

export interface RuNotificacaoConfig {
  readonly habilitado: boolean;
  readonly refeicoesPorDia: Partial<Record<DiaSemanaRu, readonly TipoRefeicao[]>>;
}

export const DEFAULT_RU_NOTIFICACAO_CONFIG: RuNotificacaoConfig = {
  habilitado: false,
  refeicoesPorDia: {},
};

export function isRuSlotEnabled(
  config: RuNotificacaoConfig,
  dia: DiaSemanaRu,
  refeicao: TipoRefeicao,
): boolean {
  return config.habilitado && (config.refeicoesPorDia[dia]?.includes(refeicao) ?? false);
}

export function hasAnyRuSlotSelected(config: RuNotificacaoConfig): boolean {
  if (!config.habilitado) return false;
  return Object.values(config.refeicoesPorDia).some((slots) => (slots?.length ?? 0) > 0);
}

export interface RuNotificacaoConteudo {
  readonly campus: CampusRu;
  readonly dataTexto: string;
  readonly dayIndex: number;
  readonly refeicao: TipoRefeicao;
  readonly titulo: string;
  readonly mensagem: string;
}

export interface RuDeepLinkTarget {
  readonly campus: CampusRu | null;
  readonly dataTexto: string | null;
  readonly dayIndex: number | null;
  readonly refeicao: TipoRefeicao;
}

export interface RuPreferencias {
  readonly campusSelecionado: CampusRu;
  readonly alergenicosRestritos: readonly Alergenico[];
  readonly ocultarPratosComAlergenicos: boolean;
  readonly noticiasVistasIds: readonly number[];
  readonly notificacoes: RuNotificacaoConfig;
}

export const DEFAULT_RU_PREFERENCIAS: RuPreferencias = {
  campusSelecionado: 'DARCY_RIBEIRO',
  alergenicosRestritos: [],
  ocultarPratosComAlergenicos: false,
  noticiasVistasIds: [],
  notificacoes: DEFAULT_RU_NOTIFICACAO_CONFIG,
};

export interface RuAutoSyncSummary {
  readonly checkedAtEpochMillis: number;
  readonly skippedDueToCooldown: boolean;
  readonly updatedCampi: readonly CampusRu[];
  readonly upToDateCampi: readonly CampusRu[];
  readonly failedCampi: Partial<Record<CampusRu, CoreError>>;
  readonly tabelaPrecosAtualizada: boolean;
  readonly noticiasAtualizadas: boolean;
  readonly anyUpdated: boolean;
}

// --- 3.3 Turmas e Turma Virtual ---

export type DiaSemanaSigaa =
  | 'SEGUNDA'
  | 'TERCA'
  | 'QUARTA'
  | 'QUINTA'
  | 'SEXTA'
  | 'SABADO';

export interface DiaSemanaSigaaMeta {
  readonly id: DiaSemanaSigaa;
  readonly digito: number;
  readonly sigla: string;
  readonly nomeCompleto: string;
}

export const DIA_SEMANA_SIGAA_LIST: readonly DiaSemanaSigaaMeta[] = [
  { id: 'SEGUNDA', digito: 2, sigla: 'SEG', nomeCompleto: 'Segunda-feira' },
  { id: 'TERCA', digito: 3, sigla: 'TER', nomeCompleto: 'Terça-feira' },
  { id: 'QUARTA', digito: 4, sigla: 'QUA', nomeCompleto: 'Quarta-feira' },
  { id: 'QUINTA', digito: 5, sigla: 'QUI', nomeCompleto: 'Quinta-feira' },
  { id: 'SEXTA', digito: 6, sigla: 'SEX', nomeCompleto: 'Sexta-feira' },
  { id: 'SABADO', digito: 7, sigla: 'SAB', nomeCompleto: 'Sábado' },
];

export const DIA_SEMANA_SIGAA_BY_ID: Readonly<Record<DiaSemanaSigaa, DiaSemanaSigaaMeta>> = {
  SEGUNDA: DIA_SEMANA_SIGAA_LIST[0],
  TERCA: DIA_SEMANA_SIGAA_LIST[1],
  QUARTA: DIA_SEMANA_SIGAA_LIST[2],
  QUINTA: DIA_SEMANA_SIGAA_LIST[3],
  SEXTA: DIA_SEMANA_SIGAA_LIST[4],
  SABADO: DIA_SEMANA_SIGAA_LIST[5],
};

export type TurnoSigaa = 'MANHA' | 'TARDE' | 'NOITE';

export const TURNO_SIGAA_BY_CHAR: Readonly<Record<string, TurnoSigaa>> = {
  M: 'MANHA',
  T: 'TARDE',
  N: 'NOITE',
};

export interface HorarioSlot {
  readonly diaSemana: DiaSemanaSigaa;
  readonly turno: TurnoSigaa;
  readonly tempoNumero: number;
  readonly inicio: string;
  readonly fim: string;
  readonly codigoOriginal: string;
}

export interface TurmaResumo {
  readonly idTurma: string;
  readonly frontEndIdTurma: string;
  readonly semestre: string;
  readonly codigoDisciplina: string | null;
  readonly nomeDisciplina: string;
  readonly numeroTurma: string | null;
  readonly cargaHorariaHoras: number | null;
  readonly docenteNome: string | null;
  readonly local: string;
  readonly codigoHorarioRaw: string;
  readonly horarioSlots: readonly HorarioSlot[];
  readonly horarioExtenso: readonly string[];
}

export interface AtualizacaoTurmaFeed {
  readonly dataTexto: string;
  readonly disciplinaSemestre: string;
  readonly idTurma: string;
  readonly tituloNoticia: string;
}

export interface AnexoArquivoSigaa {
  readonly id: string;
  readonly nomeArquivo: string;
}

export interface NoticiaTurmaResumoAva {
  readonly idNoticia: string;
  readonly dataHoraTexto: string;
  readonly titulo: string;
}

export interface NoticiaTurmaAva {
  readonly idNoticia: string | null;
  readonly titulo: string;
  readonly dataHoraTexto: string;
  readonly conteudoHtml: string;
  readonly conteudoTexto: string;
  readonly autor: string | null;
  readonly anexos: readonly AnexoArquivoSigaa[];
}

export interface MaterialAulaAva {
  readonly materialId: string;
  readonly nome: string;
  readonly tipoExtensao: string;
  readonly topicoIndex: number;
}

export interface TopicoAulaAva {
  readonly index: number;
  readonly titulo: string;
  readonly periodoTexto: string | null;
  readonly conteudoTexto: string;
  readonly materiais: readonly MaterialAulaAva[];
}

export interface TurmaDetalheAva {
  readonly idTurma: string;
  readonly frontEndIdTurma: string;
  readonly codigoDisciplina: string;
  readonly nomeDisciplina: string;
  readonly periodoTurmaTexto: string;
  readonly aulasMinistradas: number;
  readonly aulasTotal: number;
  readonly percentualAndamento: number;
  readonly ultimaNoticia: NoticiaTurmaAva | null;
  readonly historicoNoticiasResumo: readonly NoticiaTurmaResumoAva[];
  readonly topicosAula: readonly TopicoAulaAva[];
}

export interface RegistroFrequenciaDia {
  readonly dataTexto: string;
  readonly situacaoTexto: string;
  readonly faltasNoDia: number;
  readonly presente: boolean;
}

export interface MapaFrequenciaTurma {
  readonly presencasRegistradas: number;
  readonly aulasComRegistro: number;
  readonly percentualSobreRegistradas: number;
  readonly aulasDefinidasCh: number;
  readonly percentualSobreCh: number;
  readonly faltasComputadas: number;
  readonly limiteMaximoFaltas: number;
  readonly faltasRestantes: number;
  readonly emRiscoReprovacaoPorFalta: boolean;
  readonly registrosDiarios: readonly RegistroFrequenciaDia[];
}

export interface UnidadeNotaTurma {
  readonly nome: string;
  readonly valor: string | null;
}

export interface NotasParciaisTurma {
  readonly notasDisponiveis: boolean;
  readonly mensagemAviso: string | null;
  readonly unidades: readonly UnidadeNotaTurma[];
  readonly mediaFinal: string | null;
  readonly faltas: number | null;
  readonly situacao: string | null;
}

export interface AvaliacaoTurma {
  readonly dataTexto: string;
  readonly horaTexto: string | null;
  readonly descricao: string;
}

export interface TarefaTurma {
  readonly titulo: string;
  readonly periodoEntrega: string;
  readonly notaMaxima: string | null;
  readonly possuiArquivo: boolean;
  readonly emGrupo: boolean;
}

export interface ArquivoBaixado {
  readonly nomeArquivo: string;
  readonly mimeType: string;
  readonly localFilePath: string;
  readonly tamanhoBytes: number;
  readonly downloadPublicoInfo?: string | null;
}

// --- 3.4 Discente, Carteirinha e Documentos ---

export interface PerfilDiscente {
  readonly idDiscente: string;
  readonly matricula: string;
  readonly nomeCompleto: string;
  readonly nomeAbreviado: string;
  readonly bioPerfil: string | null;
  readonly fotoUrl: string | null;
  readonly semestreAtual: string;
  readonly semestreEntrada: string;
  readonly unidadeDepartamento: string;
  readonly curso: string;
  readonly nivel: string;
  readonly status: string;
  readonly emailTruncado: string | null;
  readonly idCursoPortalPublico: string | null;
  readonly fotoLocalPath: string | null;
}

export interface IndicesIntegralizacao {
  readonly ira: number | null;
  readonly mp: number | null;
  readonly chObrigatoriaPendente: number | null;
  readonly chOptativaPendente: number | null;
  readonly chComplementarPendente: number | null;
  readonly chTotalCurriculo: number | null;
  readonly percentualIntegralizadoExato: number | null;
  readonly percentualIntegralizadoTexto: string | null;
}

export interface DiscenteResumo {
  readonly perfil: PerfilDiscente;
  readonly indices: IndicesIntegralizacao;
}

export interface CarteirinhaEstudantil {
  readonly nomeCompleto: string;
  readonly matricula: string;
  readonly cpfTexto: string | null;
  readonly curso: string;
  readonly nivel: string;
  readonly validadeTexto: string;
  readonly qrPayload16Digitos: string;
  readonly qrImageLocalPath: string;
  readonly foto3x4LocalPath: string | null;
  readonly pdfLocalPath: string;
}

export interface NotaDisciplinaBoletim {
  readonly codigo: string;
  readonly disciplina: string;
  readonly nota1: string | null;
  readonly resultadoMencao: string | null;
  readonly faltas: number | null;
  readonly situacao: string;
}

export interface BoletimSemestre {
  readonly semestre: string;
  readonly disciplinas: readonly NotaDisciplinaBoletim[];
}

export type TipoDocumentoOficial =
  | 'ATESTADO_MATRICULA'
  | 'DECLARACAO_VINCULO'
  | 'HISTORICO_ESCOLAR'
  | 'PASSE_LIVRE_ESTUDANTIL'
  | 'CARTEIRINHA_ESTUDANTIL';

export interface TipoDocumentoOficialMeta {
  readonly id: TipoDocumentoOficial;
  readonly displayName: string;
  readonly subtitle: string;
  readonly mimeType: string;
}

export const TIPO_DOCUMENTO_OFICIAL_LIST: readonly TipoDocumentoOficialMeta[] = [
  {
    id: 'ATESTADO_MATRICULA',
    displayName: 'Atestado de Matrícula',
    subtitle: 'Comprovante das disciplinas matriculadas no semestre',
    mimeType: 'text/html',
  },
  {
    id: 'DECLARACAO_VINCULO',
    displayName: 'Declaração de Vínculo',
    subtitle: 'Declaração regular de vínculo estudantil com a UnB',
    mimeType: 'application/pdf',
  },
  {
    id: 'HISTORICO_ESCOLAR',
    displayName: 'Histórico Escolar Oficial',
    subtitle: 'Histórico acadêmico oficial completo',
    mimeType: 'application/pdf',
  },
  {
    id: 'PASSE_LIVRE_ESTUDANTIL',
    displayName: 'Declaração Passe Livre Estudantil',
    subtitle: 'Comprovante para cadastro ou renovação de passe livre',
    mimeType: 'text/html',
  },
  {
    id: 'CARTEIRINHA_ESTUDANTIL',
    displayName: 'Carteirinha Estudantil',
    subtitle: '',
    mimeType: 'application/pdf',
  },
];

export const TIPO_DOCUMENTO_BY_ID: Readonly<Record<TipoDocumentoOficial, TipoDocumentoOficialMeta>> = {
  ATESTADO_MATRICULA: TIPO_DOCUMENTO_OFICIAL_LIST[0],
  DECLARACAO_VINCULO: TIPO_DOCUMENTO_OFICIAL_LIST[1],
  HISTORICO_ESCOLAR: TIPO_DOCUMENTO_OFICIAL_LIST[2],
  PASSE_LIVRE_ESTUDANTIL: TIPO_DOCUMENTO_OFICIAL_LIST[3],
  CARTEIRINHA_ESTUDANTIL: TIPO_DOCUMENTO_OFICIAL_LIST[4],
};

export interface DocumentoOficialEmitido {
  readonly tipo: TipoDocumentoOficial;
  readonly titulo: string;
  readonly codigoVerificacao: string | null;
  readonly mimeType: string;
  readonly localFilePath: string;
  readonly emitidoEmEpochMillis: number;
}

// --- 3.5 Notificacoes SIGAA e Painel Discente ---

export type SigaaSyncIntervalHours = 'H2' | 'H4' | 'H6' | 'H12';

export const SIGAA_SYNC_INTERVAL_OPTIONS: readonly {
  readonly id: SigaaSyncIntervalHours;
  readonly hours: number;
  readonly label: string;
}[] = [
  { id: 'H2', hours: 2, label: 'A cada 2h' },
  { id: 'H4', hours: 4, label: 'A cada 4h' },
  { id: 'H6', hours: 6, label: 'A cada 6h' },
  { id: 'H12', hours: 12, label: 'A cada 12h' },
];

export const AULA_LEAD_MINUTES_OPTIONS: readonly {
  readonly minutes: number;
  readonly label: string;
}[] = [
  { minutes: 15, label: '15 min antes' },
  { minutes: 30, label: '30 min antes' },
  { minutes: 60, label: '1h antes' },
];

export type SigaaNotificacaoTarget = 'TURMAS' | 'DISCENTE';

export type SigaaNotificacaoTipo =
  | 'AULA_PROXIMA'
  | 'TURMA_AVISO_OU_MATERIAL'
  | 'AVALIACAO_OU_TAREFA'
  | 'NOTA_PARCIAL_ALTERADA'
  | 'RISCO_FALTAS'
  | 'RU_SALDO_BAIXO'
  | 'RU_EXTRATO_MOVIMENTACAO'
  | 'BOLETIM_ATUALIZADO'
  | 'INDICES_ATUALIZADOS';

export const LIMITE_REFEICOES_OPCOES: readonly number[] = [1, 2, 3, 5];

export interface SigaaNotificacaoConfig {
  readonly habilitado: boolean;
  readonly turmasHabilitado: boolean;
  readonly aulasLembrete: boolean;
  readonly antecedenciaAulaMinutos: number;
  readonly avisosEMateriaisNovos: boolean;
  readonly provasETarefasLembrete: boolean;
  readonly notasParciaisAlteradas: boolean;
  readonly riscoFaltaAlerta: boolean;
  readonly turmasSilenciadas: readonly string[];
  readonly discenteHabilitado: boolean;
  readonly saldoRuBaixoAlerta: boolean;
  readonly limiteRefeicoesRuBaixo: number;
  readonly extratoRuMovimentacaoAlerta: boolean;
  readonly boletimAlteradoAlerta: boolean;
  readonly indicesAlteradosAlerta: boolean;
  readonly syncIntervalHours: SigaaSyncIntervalHours;
  readonly somenteWifi: boolean;
  readonly userConfigured: boolean;
}

export const DEFAULT_SIGAA_NOTIFICACAO_CONFIG: SigaaNotificacaoConfig = {
  habilitado: false,
  turmasHabilitado: true,
  aulasLembrete: true,
  antecedenciaAulaMinutos: 30,
  avisosEMateriaisNovos: true,
  provasETarefasLembrete: true,
  notasParciaisAlteradas: true,
  riscoFaltaAlerta: true,
  turmasSilenciadas: [],
  discenteHabilitado: true,
  saldoRuBaixoAlerta: true,
  limiteRefeicoesRuBaixo: 2,
  extratoRuMovimentacaoAlerta: true,
  boletimAlteradoAlerta: true,
  indicesAlteradosAlerta: true,
  syncIntervalHours: 'H4',
  somenteWifi: false,
  userConfigured: false,
};

export const SigaaNotificacaoHelpers = {
  isTurmasAtivo(c: SigaaNotificacaoConfig): boolean {
    return (
      c.habilitado &&
      c.turmasHabilitado &&
      (c.aulasLembrete ||
        c.avisosEMateriaisNovos ||
        c.provasETarefasLembrete ||
        c.notasParciaisAlteradas ||
        c.riscoFaltaAlerta)
    );
  },
  isDiscenteAtivo(c: SigaaNotificacaoConfig): boolean {
    return (
      c.habilitado &&
      c.discenteHabilitado &&
      (c.saldoRuBaixoAlerta ||
        c.extratoRuMovimentacaoAlerta ||
        c.boletimAlteradoAlerta ||
        c.indicesAlteradosAlerta)
    );
  },
  hasAnyEnabled(c: SigaaNotificacaoConfig): boolean {
    return this.isTurmasAtivo(c) || this.isDiscenteAtivo(c);
  },
  hasAnyLocalScheduleEnabled(c: SigaaNotificacaoConfig): boolean {
    return c.habilitado && c.turmasHabilitado && (c.aulasLembrete || c.provasETarefasLembrete);
  },
  needsTurmaDetailsSync(c: SigaaNotificacaoConfig): boolean {
    return (
      c.habilitado &&
      c.turmasHabilitado &&
      (c.avisosEMateriaisNovos ||
        c.provasETarefasLembrete ||
        c.notasParciaisAlteradas ||
        c.riscoFaltaAlerta)
    );
  },
  needsBoletimSync(c: SigaaNotificacaoConfig): boolean {
    return c.habilitado && c.discenteHabilitado && c.boletimAlteradoAlerta;
  },
  needsDiscenteSaldoSync(c: SigaaNotificacaoConfig): boolean {
    return (
      c.habilitado &&
      c.discenteHabilitado &&
      (c.saldoRuBaixoAlerta || c.extratoRuMovimentacaoAlerta || c.indicesAlteradosAlerta)
    );
  },
};

export interface SigaaNotificacaoConteudo {
  readonly dedupKey: string;
  readonly notificationId: number;
  readonly tipo: SigaaNotificacaoTipo;
  readonly titulo: string;
  readonly resumo: string;
  readonly detalhesExpandido?: string | null;
  readonly target: SigaaNotificacaoTarget;
  readonly triggerAtEpochMillis: number;
}

export interface SigaaDeepLinkTarget {
  readonly target: SigaaNotificacaoTarget;
}

// --- 3.6 Atualizacao PWA ---

export interface PwaUpdateState {
  readonly currentVersionName: string;
  readonly updateReady: boolean;
  readonly checking: boolean;
  readonly lastCheckedAtEpochMillis: number | null;
}

// --- 4. Contratos Publicos (UnbPocketCore) ---

export interface AuthRepository {
  readonly authState: ObservableValue<AuthState>;
  login(credentials: CredentialsInput): Promise<CoreResult<Extract<AuthState, { kind: 'Authenticated' }>>>;
  ensureValidSession(): Promise<CoreResult<Extract<AuthState, { kind: 'Authenticated' }>>>;
  logout(clearCachedData?: boolean): Promise<CoreResult<void>>;
  hasSavedCredentials(): Promise<boolean>;
}

export interface RuRepository {
  observePreferencias(): ObservableValue<RuPreferencias>;
  setCampusSelecionado(campus: CampusRu): Promise<CoreResult<void>>;
  setAlergenicosRestritos(alergenicos: readonly Alergenico[], ocultarPratos: boolean): Promise<CoreResult<void>>;
  setNotificacoesConfig(config: RuNotificacaoConfig): Promise<CoreResult<void>>;
  prepareNotificacaoRefeicao(refeicao: TipoRefeicao, nowEpochMillis?: number): Promise<CoreResult<RuNotificacaoConteudo | null>>;
  resolveSelecaoInicial(cardapio: CardapioSemanalRu | null, nowEpochMillis?: number): RuSelecaoInicial;
  observeCardapio(campus: CampusRu): ObservableValue<SyncedData<CardapioSemanalRu> | null>;
  refreshCardapio(campus: CampusRu, force?: boolean): Promise<CoreResult<CardapioSemanalRu>>;
  syncCardapiosIfNeeded(forceCheck?: boolean, includeExtras?: boolean): Promise<CoreResult<RuAutoSyncSummary>>;
  observeSaldoExtrato(): ObservableValue<SyncedData<SaldoExtratoRu> | null>;
  refreshSaldoExtrato(): Promise<CoreResult<SaldoExtratoRu>>;
  observeTabelaPrecos(): ObservableValue<SyncedData<TabelaPrecosRu> | null>;
  refreshTabelaPrecos(force?: boolean): Promise<CoreResult<TabelaPrecosRu>>;
  observeNoticias(): ObservableValue<SyncedData<readonly NoticiaRu[]> | null>;
  refreshNoticias(perPage?: number): Promise<CoreResult<readonly NoticiaRu[]>>;
  markNoticiasVistas(ids: readonly number[]): Promise<CoreResult<void>>;
  downloadCardapioPdfLocal(campus: CampusRu): Promise<CoreResult<string>>;
}

export interface TurmasRepository {
  resolveDiaAtualSigaa(nowEpochMillis?: number): DiaSemanaSigaa;
  observeTurmasSemestre(): ObservableValue<SyncedData<readonly TurmaResumo[]> | null>;
  observeAtualizacoesFeed(): ObservableValue<SyncedData<readonly AtualizacaoTurmaFeed[]> | null>;
  refreshTurmasSemestre(enriquecerViaAtestado?: boolean): Promise<CoreResult<readonly TurmaResumo[]>>;
  observeTurmaDetalhe(frontEndIdTurma: string): ObservableValue<SyncedData<TurmaDetalheAva> | null>;
  refreshTurmaDetalhe(frontEndIdTurma: string): Promise<CoreResult<TurmaDetalheAva>>;
  getNoticiaCompleta(frontEndIdTurma: string, idNoticia: string): Promise<CoreResult<NoticiaTurmaAva>>;
  observeFrequencia(frontEndIdTurma: string): ObservableValue<SyncedData<MapaFrequenciaTurma> | null>;
  refreshFrequencia(frontEndIdTurma: string): Promise<CoreResult<MapaFrequenciaTurma>>;
  observeNotasParciais(frontEndIdTurma: string): ObservableValue<SyncedData<NotasParciaisTurma> | null>;
  refreshNotasParciais(frontEndIdTurma: string): Promise<CoreResult<NotasParciaisTurma>>;
  observeAvaliacoes(frontEndIdTurma: string): ObservableValue<SyncedData<readonly AvaliacaoTurma[]> | null>;
  refreshAvaliacoes(frontEndIdTurma: string): Promise<CoreResult<readonly AvaliacaoTurma[]>>;
  observeTarefas(frontEndIdTurma: string): ObservableValue<SyncedData<readonly TarefaTurma[]> | null>;
  refreshTarefas(frontEndIdTurma: string): Promise<CoreResult<readonly TarefaTurma[]>>;
  downloadMaterial(frontEndIdTurma: string, materialId: string): Promise<CoreResult<ArquivoBaixado>>;
  downloadAnexoNoticia(frontEndIdTurma: string, anexoId: string): Promise<CoreResult<ArquivoBaixado>>;
}

export interface DiscenteRepository {
  observeDiscenteResumo(): ObservableValue<SyncedData<DiscenteResumo> | null>;
  refreshDiscenteResumo(): Promise<CoreResult<DiscenteResumo>>;
  observeCarteirinha(): ObservableValue<SyncedData<CarteirinhaEstudantil> | null>;
  refreshCarteirinha(force?: boolean): Promise<CoreResult<CarteirinhaEstudantil>>;
  observeBoletimGeral(): ObservableValue<SyncedData<readonly BoletimSemestre[]> | null>;
  refreshBoletimGeral(): Promise<CoreResult<readonly BoletimSemestre[]>>;
  observeDocumentosEmitidos(): ObservableValue<readonly DocumentoOficialEmitido[]>;
  emitirDocumentoOficial(tipo: TipoDocumentoOficial): Promise<CoreResult<DocumentoOficialEmitido>>;
}

export interface PwaUpdateRepository {
  readonly state: ObservableValue<PwaUpdateState>;
  checkForUpdates(): Promise<CoreResult<boolean>>;
  applyUpdate(): Promise<void>;
}

export interface UnbPocketCore {
  readonly auth: AuthRepository;
  readonly ru: RuRepository;
  readonly turmas: TurmasRepository;
  readonly discente: DiscenteRepository;
  readonly updates: PwaUpdateRepository;
  readonly preloadProgress: ObservableValue<PreloadProgress>;

  readCachedBlob(localFilePath: string): Promise<Blob | null>;
  syncPortalHome(includeSaldoRu?: boolean): Promise<CoreResult<PortalHomeSyncSummary>>;
  refreshDiscentePerfilESaldo(forceDownloadFoto?: boolean): Promise<CoreResult<DiscenteResumo>>;
  startBackgroundPreload(includeTurmaDetails?: boolean): void;
  silentRefreshAll(includeTurmaDetails?: boolean): Promise<CoreResult<PortalHomeSyncSummary>>;
  observeSigaaNotificacoesConfig(): ObservableValue<SigaaNotificacaoConfig>;
  setSigaaNotificacoesConfig(config: SigaaNotificacaoConfig): Promise<CoreResult<void>>;
  autoEnableSigaaNotificacoesIfEligible(): Promise<CoreResult<boolean>>;
}
