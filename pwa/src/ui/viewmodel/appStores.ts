import { useSyncExternalStore } from 'react';
import {
  DEFAULT_RU_PREFERENCIAS,
  DEFAULT_SIGAA_NOTIFICACAO_CONFIG,
  IDLE_PRELOAD_PROGRESS,
  MutableStateValue,
  SigaaNotificacaoHelpers,
  TIPO_REFEICAO_LIST,
  type Alergenico,
  type ArquivoBaixado,
  type AtualizacaoTurmaFeed,
  type AuthState,
  type AvaliacaoTurma,
  type BoletimSemestre,
  type CampusRu,
  type CardapioSemanalRu,
  type CarteirinhaEstudantil,
  type DiaSemanaRu,
  type DiaSemanaSigaa,
  type DiscenteResumo,
  type DocumentoOficialEmitido,
  type MapaFrequenciaTurma,
  type NotasParciaisTurma,
  type NoticiaRu,
  type NoticiaTurmaAva,
  type ObservableValue,
  type PreloadProgress,
  type PwaUpdateState,
  type RuDeepLinkTarget,
  type RuPreferencias,
  type SaldoExtratoRu,
  type SigaaNotificacaoConfig,
  type SigaaSyncIntervalHours,
  type SyncedData,
  type TabelaPrecosRu,
  type TarefaTurma,
  type TipoDocumentoOficial,
  type TipoRefeicao,
  type TurmaDetalheAva,
  type TurmaResumo,
  type UnbPocketCore,
  type Unsubscribe,
} from '../../core/model/models';
import { getBrasiliaParts } from '../../core/parser/brasiliaTime';
import { CoreErrorMapper, withoutStaleAuth, type UiErrorBanner } from '../common/coreErrorMapper';
import { Formatters } from '../common/formatters';

export function useObservable<T>(obs: ObservableValue<T>): T {
  return useSyncExternalStore(obs.subscribe, obs.getSnapshot, obs.getSnapshot);
}

// ============================================================================
// 1. AuthStore (port 1:1 de AuthViewModel.kt)
// ============================================================================

export interface AuthUiState {
  readonly authState: AuthState;
  readonly hasSavedCredentials: boolean;
  readonly isBootstrapping: boolean;
  readonly matriculaInput: string;
  readonly senhaInput: string;
  readonly isSubmittingLogin: boolean;
  readonly isSyncingAll: boolean;
  readonly isLoggingOut: boolean;
  readonly preloadProgress: PreloadProgress;
  readonly loginError: UiErrorBanner | null;
  readonly syncError: UiErrorBanner | null;
  readonly syncStatusMessage: string | null;
  readonly sigaaNotificacoes: SigaaNotificacaoConfig;
  readonly turmasParaNotificacao: readonly TurmaResumo[];
}

export class AuthStore {
  private readonly core: UnbPocketCore;
  private readonly unsubs: Unsubscribe[] = [];

  private hasSavedCredentials = false;
  private isBootstrapping = false;
  private matriculaInput = '';
  private senhaInput = '';
  private isSubmittingLogin = false;
  private isSyncingAll = false;
  private isLoggingOut = false;
  private loginError: UiErrorBanner | null = null;
  private syncError: UiErrorBanner | null = null;
  private syncStatusMessage: string | null = null;

  private readonly stateBox: MutableStateValue<AuthUiState>;

  constructor(core: UnbPocketCore, autoBootstrap: boolean = true) {
    this.core = core;
    this.stateBox = new MutableStateValue<AuthUiState>(this.computeState());

    const recompute = (): void => {
      const auth = this.core.auth.authState.getSnapshot();
      if (auth.kind === 'Authenticated') {
        this.loginError = withoutStaleAuth(this.loginError, auth);
        this.syncError = withoutStaleAuth(this.syncError, auth);
      }
      this.stateBox.value = this.computeState();
    };

    this.unsubs.push(
      core.auth.authState.subscribe(recompute),
      core.preloadProgress.subscribe(recompute),
      core.observeSigaaNotificacoesConfig().subscribe(recompute),
      core.turmas.observeTurmasSemestre().subscribe(recompute),
    );

    if (autoBootstrap) {
      void this.bootstrapSessionIfSaved();
    }
  }

  get uiState(): ObservableValue<AuthUiState> {
    return this.stateBox;
  }

  dispose(): void {
    for (const u of this.unsubs) u();
    this.unsubs.length = 0;
  }

  private computeState(): AuthUiState {
    const auth = this.core.auth.authState.getSnapshot();
    const preload = this.core.preloadProgress.getSnapshot() ?? IDLE_PRELOAD_PROGRESS;
    const sigaaNotifs =
      this.core.observeSigaaNotificacoesConfig().getSnapshot() ??
      DEFAULT_SIGAA_NOTIFICACAO_CONFIG;
    const turmas = this.core.turmas.observeTurmasSemestre().getSnapshot()?.value ?? [];

    return {
      authState: auth,
      hasSavedCredentials: this.hasSavedCredentials,
      isBootstrapping: this.isBootstrapping,
      matriculaInput: this.matriculaInput,
      senhaInput: this.senhaInput,
      isSubmittingLogin: this.isSubmittingLogin,
      isSyncingAll: this.isSyncingAll,
      isLoggingOut: this.isLoggingOut,
      preloadProgress: preload,
      loginError: withoutStaleAuth(this.loginError, auth),
      syncError: withoutStaleAuth(this.syncError, auth),
      syncStatusMessage: this.syncStatusMessage,
      sigaaNotificacoes: sigaaNotifs,
      turmasParaNotificacao: turmas,
    };
  }

  private emit(): void {
    this.stateBox.value = this.computeState();
  }

  async bootstrapSessionIfSaved(): Promise<void> {
    const saved = await this.core.auth.hasSavedCredentials();
    this.hasSavedCredentials = saved;
    if (!saved) {
      this.emit();
      return;
    }

    this.isBootstrapping = true;
    this.emit();
    const res = await this.core.silentRefreshAll(false);
    if (res.ok) {
      this.isBootstrapping = false;
      this.hasSavedCredentials = true;
      this.syncError = null;
    } else {
      const stillHasSaved = await this.core.auth.hasSavedCredentials();
      this.isBootstrapping = false;
      this.hasSavedCredentials = stillHasSaved;
      this.syncError =
        res.error.kind === 'InvalidCredentials'
          ? CoreErrorMapper.toUiError(res.error)
          : null;
    }
    this.emit();
  }

  onMatriculaChange = (value: string): void => {
    this.matriculaInput = value;
    this.loginError = null;
    this.emit();
  };

  onSenhaChange = (value: string): void => {
    this.senhaInput = value;
    this.loginError = null;
    this.emit();
  };

  submitLogin = async (onSuccess?: () => void): Promise<void> => {
    if (this.isSubmittingLogin) return;

    const matricula = this.matriculaInput.trim();
    const senha = this.senhaInput;
    if (!matricula || !senha) {
      this.loginError = {
        title: 'Campos obrigatórios',
        message: 'Informe sua matrícula da UnB e a senha do SIGAA/CAS.',
      };
      this.emit();
      return;
    }

    this.isSubmittingLogin = true;
    this.loginError = null;
    this.syncStatusMessage = 'Conectando ao SIGAA...';
    this.emit();

    const loginRes = await this.core.auth.login({ matricula, password: senha });
    if (!loginRes.ok) {
      this.isSubmittingLogin = false;
      this.loginError = CoreErrorMapper.toUiError(loginRes.error);
      this.syncStatusMessage = null;
      this.emit();
      return;
    }

    this.hasSavedCredentials = true;
    this.senhaInput = '';
    this.loginError = null;
    this.syncError = null;
    this.syncStatusMessage = 'Carregando turmas e dados acadêmicos...';
    this.emit();

    const essentialRes = await this.core.syncPortalHome(true);
    if (essentialRes.ok) {
      const summary = essentialRes.value;
      this.core.startBackgroundPreload(true);
      this.isSubmittingLogin = false;
      this.syncError = null;
      this.syncStatusMessage =
        summary.turmasCount === 0
          ? 'Dados acadêmicos carregados.'
          : `Sincronizado (${summary.turmasCount} turmas).`;
      this.emit();
      onSuccess?.();
    } else if (essentialRes.error.kind === 'InvalidCredentials') {
      this.isSubmittingLogin = false;
      this.loginError = CoreErrorMapper.toUiError(essentialRes.error);
      this.syncStatusMessage = null;
      this.emit();
    } else {
      this.isSubmittingLogin = false;
      this.syncError = CoreErrorMapper.toUiError(essentialRes.error);
      this.syncStatusMessage = null;
      this.emit();
      onSuccess?.();
    }
  };

  syncAll = async (): Promise<void> => {
    if (this.isSyncingAll) return;
    this.isSyncingAll = true;
    this.syncError = null;
    this.syncStatusMessage = null;
    this.emit();

    const result = await this.core.syncPortalHome(true);
    if (result.ok) {
      this.core.startBackgroundPreload(true);
      this.isSyncingAll = false;
      this.syncError = null;
      this.syncStatusMessage =
        result.value.turmasCount === 0
          ? 'Dados acadêmicos sincronizados.'
          : `Sincronizado (${result.value.turmasCount} turmas).`;
    } else {
      this.isSyncingAll = false;
      this.syncError = CoreErrorMapper.toUiError(result.error);
    }
    this.emit();
  };

  logout = async (clearCachedData: boolean = true, onDone?: () => void): Promise<void> => {
    if (this.isLoggingOut) return;
    this.isLoggingOut = true;
    this.emit();

    const res = await this.core.auth.logout(clearCachedData);
    const saved = await this.core.auth.hasSavedCredentials();
    this.isLoggingOut = false;
    this.hasSavedCredentials = saved;
    this.senhaInput = '';
    this.loginError = res.ok ? null : CoreErrorMapper.toUiError(res.error);
    this.syncError = null;
    this.syncStatusMessage = null;
    this.emit();
    onDone?.();
  };

  dismissLoginError = (): void => {
    this.loginError = null;
    this.emit();
  };

  dismissSyncError = (): void => {
    this.syncError = null;
    this.emit();
  };

  clearSyncStatusMessage = (): void => {
    this.syncStatusMessage = null;
    this.emit();
  };

  autoEnableSigaaNotificationsIfPermitted = (): void => {
    void this.core.autoEnableSigaaNotificacoesIfEligible();
  };

  toggleSigaaTurmasSecao = (enabled: boolean): void => {
    this.updateSigaaNotificacoes((c) =>
      enabled
        ? { ...c, habilitado: true, turmasHabilitado: true }
        : { ...c, turmasHabilitado: false },
    );
  };

  toggleSigaaDiscenteSecao = (enabled: boolean): void => {
    this.updateSigaaNotificacoes((c) =>
      enabled
        ? { ...c, habilitado: true, discenteHabilitado: true }
        : { ...c, discenteHabilitado: false },
    );
  };

  setAulasLembrete = (enabled: boolean): void => {
    this.updateSigaaNotificacoes((c) => ({
      ...c,
      habilitado: c.habilitado || enabled,
      turmasHabilitado: c.turmasHabilitado || enabled,
      aulasLembrete: enabled,
    }));
  };

  setAntecedenciaAulaMinutos = (minutes: number): void => {
    this.updateSigaaNotificacoes((c) => ({ ...c, antecedenciaAulaMinutos: minutes }));
  };

  setAvisosEMateriaisNovos = (enabled: boolean): void => {
    this.updateSigaaNotificacoes((c) => ({
      ...c,
      habilitado: c.habilitado || enabled,
      turmasHabilitado: c.turmasHabilitado || enabled,
      avisosEMateriaisNovos: enabled,
    }));
  };

  setProvasETarefasLembrete = (enabled: boolean): void => {
    this.updateSigaaNotificacoes((c) => ({
      ...c,
      habilitado: c.habilitado || enabled,
      turmasHabilitado: c.turmasHabilitado || enabled,
      provasETarefasLembrete: enabled,
    }));
  };

  setNotasParciaisAlteradas = (enabled: boolean): void => {
    this.updateSigaaNotificacoes((c) => ({
      ...c,
      habilitado: c.habilitado || enabled,
      turmasHabilitado: c.turmasHabilitado || enabled,
      notasParciaisAlteradas: enabled,
    }));
  };

  setRiscoFaltaAlerta = (enabled: boolean): void => {
    this.updateSigaaNotificacoes((c) => ({
      ...c,
      habilitado: c.habilitado || enabled,
      turmasHabilitado: c.turmasHabilitado || enabled,
      riscoFaltaAlerta: enabled,
    }));
  };

  toggleTurmaSilenciada = (turma: TurmaResumo): void => {
    this.updateSigaaNotificacoes((c) => {
      const muted = new Set(c.turmasSilenciadas);
      const isMuted = muted.has(turma.frontEndIdTurma) || muted.has(turma.idTurma);
      if (isMuted) {
        muted.delete(turma.frontEndIdTurma);
        muted.delete(turma.idTurma);
      } else {
        muted.add(turma.frontEndIdTurma);
      }
      return { ...c, turmasSilenciadas: Array.from(muted) };
    });
  };

  setSaldoRuBaixoAlerta = (enabled: boolean): void => {
    this.updateSigaaNotificacoes((c) => ({
      ...c,
      habilitado: c.habilitado || enabled,
      discenteHabilitado: c.discenteHabilitado || enabled,
      saldoRuBaixoAlerta: enabled,
    }));
  };

  setLimiteRefeicoesRuBaixo = (meals: number): void => {
    this.updateSigaaNotificacoes((c) => ({
      ...c,
      limiteRefeicoesRuBaixo: Math.max(1, meals),
    }));
  };

  setExtratoRuMovimentacaoAlerta = (enabled: boolean): void => {
    this.updateSigaaNotificacoes((c) => ({
      ...c,
      habilitado: c.habilitado || enabled,
      discenteHabilitado: c.discenteHabilitado || enabled,
      extratoRuMovimentacaoAlerta: enabled,
    }));
  };

  setBoletimAlteradoAlerta = (enabled: boolean): void => {
    this.updateSigaaNotificacoes((c) => ({
      ...c,
      habilitado: c.habilitado || enabled,
      discenteHabilitado: c.discenteHabilitado || enabled,
      boletimAlteradoAlerta: enabled,
    }));
  };

  setIndicesAlteradosAlerta = (enabled: boolean): void => {
    this.updateSigaaNotificacoes((c) => ({
      ...c,
      habilitado: c.habilitado || enabled,
      discenteHabilitado: c.discenteHabilitado || enabled,
      indicesAlteradosAlerta: enabled,
    }));
  };

  setSigaaSyncInterval = (interval: SigaaSyncIntervalHours): void => {
    this.updateSigaaNotificacoes((c) => ({ ...c, syncIntervalHours: interval }));
  };

  setSigaaSomenteWifi = (onlyWifi: boolean): void => {
    this.updateSigaaNotificacoes((c) => ({ ...c, somenteWifi: onlyWifi }));
  };

  resyncSigaaNotificacoesSchedule = (): void => {
    const current = this.stateBox.value.sigaaNotificacoes;
    if (!SigaaNotificacaoHelpers.hasAnyEnabled(current)) return;
    void this.core.setSigaaNotificacoesConfig(current);
  };

  private updateSigaaNotificacoes(
    transform: (current: SigaaNotificacaoConfig) => SigaaNotificacaoConfig,
  ): void {
    const current = this.stateBox.value.sigaaNotificacoes;
    const updated: SigaaNotificacaoConfig = {
      ...transform(current),
      userConfigured: true,
    };
    void this.core.setSigaaNotificacoesConfig(updated);
  }
}

// ============================================================================
// 2. RuStore (port 1:1 de RuViewModel.kt)
// ============================================================================

export interface RuUiState {
  readonly preferencias: RuPreferencias;
  readonly cardapio: SyncedData<CardapioSemanalRu> | null;
  readonly hasResolvedInitialMenu: boolean;
  readonly isMenuLoading: boolean;
  readonly tabelaPrecos: SyncedData<TabelaPrecosRu> | null;
  readonly noticias: SyncedData<readonly NoticiaRu[]> | null;
  readonly selectedDayIndex: number;
  readonly selectedMeal: TipoRefeicao;
  readonly isRefreshingMenu: boolean;
  readonly isRefreshingExtras: boolean;
  readonly menuError: UiErrorBanner | null;
  readonly extrasError: UiErrorBanner | null;
  readonly isCampusSheetOpen: boolean;
  readonly isAllergenSheetOpen: boolean;
  readonly isNoticesAndPricesSheetOpen: boolean;
  readonly hasUnreadNoticias: boolean;
}

export class RuStore {
  private readonly core: UnbPocketCore;
  private readonly unsubs: Unsubscribe[] = [];
  private cardapioUnsub: Unsubscribe | null = null;

  private observedCampus: CampusRu;
  private lastLoadedCampus: CampusRu | null = null;
  private hasResolvedInitialMenu = false;
  private selectedDayIndex: number | null = null;
  private targetDayDataTexto: string | null = null;
  private selectedMeal: TipoRefeicao | null = null;
  private lastResumeIsoDate: string = getBrasiliaParts(Date.now()).isoDate;
  private isRefreshingMenu = false;
  private isRefreshingExtras = false;
  private isSilentSyncActive = false;
  private menuError: UiErrorBanner | null = null;
  private extrasError: UiErrorBanner | null = null;
  private isCampusSheetOpen = false;
  private isAllergenSheetOpen = false;
  private isNoticesAndPricesSheetOpen = false;

  private readonly stateBox: MutableStateValue<RuUiState>;

  constructor(core: UnbPocketCore, autoFetch: boolean = true) {
    this.core = core;
    this.observedCampus =
      core.ru.observePreferencias().getSnapshot().campusSelecionado ??
      DEFAULT_RU_PREFERENCIAS.campusSelecionado;

    this.bindCampusChannel(this.observedCampus);
    this.stateBox = new MutableStateValue<RuUiState>(this.computeState());

    this.unsubs.push(
      core.ru.observePreferencias().subscribe((prefs) => {
        if (prefs.campusSelecionado !== this.observedCampus) {
          this.observedCampus = prefs.campusSelecionado;
          this.hasResolvedInitialMenu = false;
          this.bindCampusChannel(prefs.campusSelecionado);
        }
        if (autoFetch && this.lastLoadedCampus !== prefs.campusSelecionado) {
          this.lastLoadedCampus = prefs.campusSelecionado;
          void this.refreshMenuInternal(prefs.campusSelecionado, false);
        }
        this.emit();
      }),
      core.ru.observeTabelaPrecos().subscribe(() => this.emit()),
      core.ru.observeNoticias().subscribe(() => this.emit()),
    );

    if (autoFetch) {
      this.lastLoadedCampus = this.observedCampus;
      void this.refreshMenuInternal(this.observedCampus, false);
      void this.refreshPricesAndNotices(false);
    }
  }

  get uiState(): ObservableValue<RuUiState> {
    return this.stateBox;
  }

  dispose(): void {
    this.cardapioUnsub?.();
    this.cardapioUnsub = null;
    for (const u of this.unsubs) u();
    this.unsubs.length = 0;
  }

  private bindCampusChannel(campus: CampusRu): void {
    this.cardapioUnsub?.();
    const channel = this.core.ru.observeCardapio(campus);
    if (channel.getSnapshot() !== null) {
      this.hasResolvedInitialMenu = true;
    }
    this.cardapioUnsub = channel.subscribe(() => {
      this.hasResolvedInitialMenu = true;
      this.emit();
    });
  }

  private computeState(): RuUiState {
    const prefs = this.core.ru.observePreferencias().getSnapshot();
    const cardapio = this.core.ru.observeCardapio(prefs.campusSelecionado).getSnapshot();
    const precos = this.core.ru.observeTabelaPrecos().getSnapshot();
    const noticias = this.core.ru.observeNoticias().getSnapshot();

    const dias = cardapio?.value.dias ?? [];
    const autoSelection = this.core.ru.resolveSelecaoInicial(cardapio?.value ?? null);
    const matchedDeepLinkIdx =
      this.targetDayDataTexto !== null
        ? dias.findIndex((d) => d.dataTexto === this.targetDayDataTexto)
        : -1;

    let resolvedDayIndex = 0;
    if (dias.length > 0) {
      if (matchedDeepLinkIdx >= 0) {
        resolvedDayIndex = matchedDeepLinkIdx;
      } else if (this.selectedDayIndex !== null) {
        resolvedDayIndex = Math.min(dias.length - 1, Math.max(0, this.selectedDayIndex));
      } else {
        resolvedDayIndex = Math.min(dias.length - 1, Math.max(0, autoSelection.dayIndex));
      }
    }

    const resolvedMeal = this.selectedMeal ?? autoSelection.refeicao;
    const seenSet = new Set(prefs.noticiasVistasIds);
    const hasUnreadNoticias = (noticias?.value ?? []).some((n) => !seenSet.has(n.id));
    const isMenuLoading =
      cardapio === null && (this.isRefreshingMenu || !this.hasResolvedInitialMenu);

    return {
      preferencias: prefs,
      cardapio,
      hasResolvedInitialMenu: this.hasResolvedInitialMenu,
      isMenuLoading,
      tabelaPrecos: precos,
      noticias,
      selectedDayIndex: resolvedDayIndex,
      selectedMeal: resolvedMeal,
      isRefreshingMenu: this.isRefreshingMenu,
      isRefreshingExtras: this.isRefreshingExtras,
      menuError: this.menuError,
      extrasError: this.extrasError,
      isCampusSheetOpen: this.isCampusSheetOpen,
      isAllergenSheetOpen: this.isAllergenSheetOpen,
      isNoticesAndPricesSheetOpen: this.isNoticesAndPricesSheetOpen,
      hasUnreadNoticias,
    };
  }

  private emit(): void {
    this.stateBox.value = this.computeState();
  }

  selectCampus = (campus: CampusRu): void => {
    this.selectedDayIndex = null;
    this.targetDayDataTexto = null;
    this.isCampusSheetOpen = false;
    this.menuError = null;
    this.emit();
    void this.core.ru.setCampusSelecionado(campus);
  };

  selectDay = (index: number): void => {
    this.selectedDayIndex = index;
    this.targetDayDataTexto = null;
    this.emit();
  };

  selectMeal = (meal: TipoRefeicao): void => {
    this.selectedMeal = meal;
    this.emit();
  };

  applyDeepLink = (target: RuDeepLinkTarget): void => {
    this.selectedMeal = target.refeicao;
    this.selectedDayIndex = target.dayIndex;
    this.targetDayDataTexto = target.dataTexto;
    this.emit();
    if (
      target.campus !== null &&
      target.campus !== this.stateBox.value.preferencias.campusSelecionado
    ) {
      void this.core.ru.setCampusSelecionado(target.campus);
    }
  };

  toggleAllergen = (alergenico: Alergenico): void => {
    const current = this.stateBox.value.preferencias;
    const set = new Set(current.alergenicosRestritos);
    if (set.has(alergenico)) set.delete(alergenico);
    else set.add(alergenico);
    void this.core.ru.setAlergenicosRestritos(
      Array.from(set),
      current.ocultarPratosComAlergenicos,
    );
  };

  setHideRestricted = (hide: boolean): void => {
    const current = this.stateBox.value.preferencias;
    void this.core.ru.setAlergenicosRestritos(current.alergenicosRestritos, hide);
  };

  clearAllergens = (): void => {
    void this.core.ru.setAlergenicosRestritos([], false);
  };

  setNotificacoesHabilitadas = (enabled: boolean): void => {
    const current = this.stateBox.value.preferencias.notificacoes;
    void this.core.ru.setNotificacoesConfig({ ...current, habilitado: enabled });
  };

  toggleNotificacaoSlot = (dia: DiaSemanaRu, refeicao: TipoRefeicao): void => {
    const current = this.stateBox.value.preferencias.notificacoes;
    const meals = new Set(current.refeicoesPorDia[dia] ?? []);
    if (meals.has(refeicao)) meals.delete(refeicao);
    else meals.add(refeicao);

    const nextMap = { ...current.refeicoesPorDia };
    if (meals.size === 0) delete nextMap[dia];
    else nextMap[dia] = Array.from(meals);

    void this.core.ru.setNotificacoesConfig({
      habilitado: current.habilitado || meals.has(refeicao),
      refeicoesPorDia: nextMap,
    });
  };

  toggleNotificacaoDia = (dia: DiaSemanaRu): void => {
    const current = this.stateBox.value.preferencias.notificacoes;
    const allMeals = TIPO_REFEICAO_LIST.map((m) => m.id);
    const currentMeals = current.refeicoesPorDia[dia] ?? [];
    const enablingDay = currentMeals.length !== allMeals.length;
    const nextMap = { ...current.refeicoesPorDia };
    if (enablingDay) nextMap[dia] = allMeals;
    else delete nextMap[dia];

    void this.core.ru.setNotificacoesConfig({
      habilitado: current.habilitado || enablingDay,
      refeicoesPorDia: nextMap,
    });
  };

  refreshMenu = async (force: boolean = true): Promise<void> => {
    const campus = this.stateBox.value.preferencias.campusSelecionado;
    await this.refreshMenuInternal(campus, force);
  };

  refreshIfStaleSilently = (): void => {
    if (this.isSilentSyncActive || this.isRefreshingMenu) return;
    this.isSilentSyncActive = true;
    void this.core.ru.syncCardapiosIfNeeded(false, false).finally(() => {
      this.isSilentSyncActive = false;
    });
  };

  private async refreshMenuInternal(campus: CampusRu, force: boolean): Promise<void> {
    this.isRefreshingMenu = true;
    this.menuError = null;
    this.emit();

    const result = await this.core.ru.refreshCardapio(campus, force);
    this.hasResolvedInitialMenu = true;
    this.isRefreshingMenu = false;
    this.menuError = result.ok ? null : CoreErrorMapper.toUiError(result.error);
    this.emit();
  }

  refreshPricesAndNotices = async (force: boolean = true): Promise<void> => {
    if (this.isRefreshingExtras) return;
    this.isRefreshingExtras = true;
    this.extrasError = null;
    this.emit();

    const [precosRes, noticiasRes] = await Promise.all([
      this.core.ru.refreshTabelaPrecos(force),
      this.core.ru.refreshNoticias(10),
    ]);

    if (this.isNoticesAndPricesSheetOpen && noticiasRes.ok) {
      const ids = noticiasRes.value.map((n) => n.id);
      if (ids.length > 0) {
        await this.core.ru.markNoticiasVistas(ids);
      }
    }

    const error = !noticiasRes.ok
      ? noticiasRes.error
      : !precosRes.ok
        ? precosRes.error
        : null;
    this.isRefreshingExtras = false;
    this.extrasError = error ? CoreErrorMapper.toUiError(error) : null;
    this.emit();
  };

  setCampusSheetOpen = (open: boolean): void => {
    this.isCampusSheetOpen = open;
    this.emit();
  };

  setAllergenSheetOpen = (open: boolean): void => {
    this.isAllergenSheetOpen = open;
    this.emit();
  };

  setNoticesAndPricesSheetOpen = (open: boolean): void => {
    this.isNoticesAndPricesSheetOpen = open;
    this.emit();
    if (open) {
      const ids = (this.core.ru.observeNoticias().getSnapshot()?.value ?? []).map((n) => n.id);
      if (ids.length > 0) {
        void this.core.ru.markNoticiasVistas(ids);
      }
      void this.refreshPricesAndNotices(false);
    }
  };

  syncAutomaticSelectionOnResume = (nowEpochMillis: number = Date.now()): void => {
    const todayIso = getBrasiliaParts(nowEpochMillis).isoDate;
    const dayChanged = todayIso !== this.lastResumeIsoDate;
    this.lastResumeIsoDate = todayIso;
    if (dayChanged) {
      this.selectedDayIndex = null;
      this.targetDayDataTexto = null;
      this.selectedMeal = null;
    }
    this.emit();
  };

  dismissMenuError = (): void => {
    this.menuError = null;
    this.emit();
  };

  dismissExtrasError = (): void => {
    this.extrasError = null;
    this.emit();
  };
}

// ============================================================================
// 3. TurmasStore (port 1:1 de TurmasViewModel.kt)
// ============================================================================

export type TurmasListTab = 'AULAS_DO_DIA' | 'TURMAS_SEMESTRE' | 'ATUALIZACOES';
export type TurmaDetailTab = 'TOPICOS' | 'NOTICIAS' | 'FREQUENCIA' | 'NOTAS_AVALIACOES';

export interface TurmasUiState {
  readonly authState: AuthState;
  readonly selectedListTab: TurmasListTab;
  readonly selectedDay: DiaSemanaSigaa;
  readonly todayDay: DiaSemanaSigaa;
  readonly expandedClassTurmaId: string | null;
  readonly turmas: SyncedData<readonly TurmaResumo[]> | null;
  readonly atualizacoes: SyncedData<readonly AtualizacaoTurmaFeed[]> | null;
  readonly hasResolvedInitialCache: boolean;
  readonly isRefreshingList: boolean;
  readonly listError: UiErrorBanner | null;
}

export interface TurmaVirtualUiState {
  readonly activeTurmaId: string | null;
  readonly summary: TurmaResumo | null;
  readonly selectedDetailTab: TurmaDetailTab;
  readonly detalhe: SyncedData<TurmaDetalheAva> | null;
  readonly frequencia: SyncedData<MapaFrequenciaTurma> | null;
  readonly notasParciais: SyncedData<NotasParciaisTurma> | null;
  readonly avaliacoes: SyncedData<readonly AvaliacaoTurma[]> | null;
  readonly tarefas: SyncedData<readonly TarefaTurma[]> | null;
  readonly expandedNoticiaId: string | null;
  readonly fullNoticiaById: Readonly<Record<string, NoticiaTurmaAva>>;
  readonly loadingNoticiaIds: ReadonlySet<string>;
  readonly downloadingItemId: string | null;
  readonly downloadedFileEvent: ArquivoBaixado | null;
  readonly isResolvingCache: boolean;
  readonly isRefreshingDetail: boolean;
  readonly isLoadingDetail: boolean;
  readonly detailError: UiErrorBanner | null;
}

export class TurmasStore {
  private readonly core: UnbPocketCore;
  private readonly unsubs: Unsubscribe[] = [];
  private detailSubs: Unsubscribe[] = [];

  private lastResumeIsoDate: string = getBrasiliaParts(Date.now()).isoDate;
  private hasResolvedInitialCache = false;
  private selectedListTab: TurmasListTab = 'AULAS_DO_DIA';
  private selectedDay: DiaSemanaSigaa | null = null;
  private expandedClassTurmaId: string | null = null;
  private isRefreshingList = false;
  private listError: UiErrorBanner | null = null;

  private activeTurmaId: string | null = null;
  private selectedDetailTab: TurmaDetailTab = 'TOPICOS';
  private expandedNoticiaId: string | null = null;
  private fullNoticiaById: Record<string, NoticiaTurmaAva> = {};
  private loadingNoticiaIds = new Set<string>();
  private downloadingItemId: string | null = null;
  private downloadedFileEvent: ArquivoBaixado | null = null;
  private isResolvingCache = false;
  private isRefreshingDetail = false;
  private detailError: UiErrorBanner | null = null;

  private readonly listStateBox: MutableStateValue<TurmasUiState>;
  private readonly detailStateBox: MutableStateValue<TurmaVirtualUiState>;

  constructor(core: UnbPocketCore) {
    this.core = core;
    if (core.turmas.observeTurmasSemestre().getSnapshot() !== null) {
      this.hasResolvedInitialCache = true;
    }

    this.listStateBox = new MutableStateValue<TurmasUiState>(this.computeListState());
    this.detailStateBox = new MutableStateValue<TurmaVirtualUiState>(this.computeDetailState());

    this.unsubs.push(
      core.auth.authState.subscribe((auth) => {
        if (auth.kind === 'Authenticated') {
          this.listError = withoutStaleAuth(this.listError, auth);
          this.detailError = withoutStaleAuth(this.detailError, auth);
        }
        this.emitAll();
      }),
      core.turmas.observeTurmasSemestre().subscribe(() => {
        this.hasResolvedInitialCache = true;
        this.emitAll();
      }),
      core.turmas.observeAtualizacoesFeed().subscribe(() => this.emitList()),
    );
  }

  get listUiState(): ObservableValue<TurmasUiState> {
    return this.listStateBox;
  }

  get detailUiState(): ObservableValue<TurmaVirtualUiState> {
    return this.detailStateBox;
  }

  dispose(): void {
    this.clearDetailSubs();
    for (const u of this.unsubs) u();
    this.unsubs.length = 0;
  }

  private clearDetailSubs(): void {
    for (const u of this.detailSubs) u();
    this.detailSubs = [];
  }

  private computeListState(): TurmasUiState {
    const auth = this.core.auth.authState.getSnapshot();
    const today = this.core.turmas.resolveDiaAtualSigaa() ?? Formatters.currentSigaaDay();
    const turmas = this.core.turmas.observeTurmasSemestre().getSnapshot();
    const atualizacoes = this.core.turmas.observeAtualizacoesFeed().getSnapshot();

    return {
      authState: auth,
      selectedListTab: this.selectedListTab,
      selectedDay: this.selectedDay ?? today,
      todayDay: today,
      expandedClassTurmaId: this.expandedClassTurmaId,
      turmas,
      atualizacoes,
      hasResolvedInitialCache: this.hasResolvedInitialCache,
      isRefreshingList: this.isRefreshingList,
      listError: withoutStaleAuth(this.listError, auth),
    };
  }

  private computeDetailState(): TurmaVirtualUiState {
    const auth = this.core.auth.authState.getSnapshot();
    const id = this.activeTurmaId;
    const turmas = this.core.turmas.observeTurmasSemestre().getSnapshot()?.value ?? [];
    const summary = id ? (turmas.find((t) => t.frontEndIdTurma === id) ?? null) : null;

    const detalhe = id ? this.core.turmas.observeTurmaDetalhe(id).getSnapshot() : null;
    const frequencia = id ? this.core.turmas.observeFrequencia(id).getSnapshot() : null;
    const notasParciais = id ? this.core.turmas.observeNotasParciais(id).getSnapshot() : null;
    const avaliacoes = id ? this.core.turmas.observeAvaliacoes(id).getSnapshot() : null;
    const tarefas = id ? this.core.turmas.observeTarefas(id).getSnapshot() : null;

    return {
      activeTurmaId: id,
      summary,
      selectedDetailTab: this.selectedDetailTab,
      detalhe,
      frequencia,
      notasParciais,
      avaliacoes,
      tarefas,
      expandedNoticiaId: this.expandedNoticiaId,
      fullNoticiaById: this.fullNoticiaById,
      loadingNoticiaIds: new Set(this.loadingNoticiaIds),
      downloadingItemId: this.downloadingItemId,
      downloadedFileEvent: this.downloadedFileEvent,
      isResolvingCache: this.isResolvingCache,
      isRefreshingDetail: this.isRefreshingDetail,
      isLoadingDetail: this.isRefreshingDetail || this.isResolvingCache,
      detailError: withoutStaleAuth(this.detailError, auth),
    };
  }

  private emitList(): void {
    this.listStateBox.value = this.computeListState();
  }

  private emitDetail(): void {
    this.detailStateBox.value = this.computeDetailState();
  }

  private emitAll(): void {
    this.emitList();
    this.emitDetail();
  }

  selectListTab = (tab: TurmasListTab): void => {
    this.selectedListTab = tab;
    this.emitList();
  };

  selectDay = (day: DiaSemanaSigaa): void => {
    this.selectedDay = day;
    this.expandedClassTurmaId = null;
    this.emitList();
  };

  syncAutomaticDayOnResume = (nowEpochMillis: number = Date.now()): void => {
    const todayIso = getBrasiliaParts(nowEpochMillis).isoDate;
    const dayChanged = todayIso !== this.lastResumeIsoDate;
    this.lastResumeIsoDate = todayIso;
    if (dayChanged) {
      this.selectedDay = null;
      this.expandedClassTurmaId = null;
    }
    this.emitList();
  };

  toggleExpandedClass = (frontEndIdTurma: string): void => {
    this.expandedClassTurmaId =
      this.expandedClassTurmaId === frontEndIdTurma ? null : frontEndIdTurma;
    this.emitList();
  };

  refreshSemestre = async (
    enriquecerViaAtestado: boolean = this.selectedListTab !== 'ATUALIZACOES',
  ): Promise<void> => {
    if (this.isRefreshingList) return;
    this.isRefreshingList = true;
    this.listError = null;
    this.emitList();

    const res = await this.core.turmas.refreshTurmasSemestre(enriquecerViaAtestado);
    this.hasResolvedInitialCache = true;
    this.isRefreshingList = false;
    this.listError = res.ok ? null : CoreErrorMapper.toUiError(res.error);
    this.emitList();
  };

  openTurma = async (frontEndIdTurma: string): Promise<void> => {
    this.clearDetailSubs();
    this.activeTurmaId = frontEndIdTurma;
    this.selectedDetailTab = 'TOPICOS';
    this.expandedNoticiaId = null;
    this.fullNoticiaById = {};
    this.loadingNoticiaIds = new Set();
    this.downloadingItemId = null;
    this.downloadedFileEvent = null;
    this.detailError = null;
    this.isResolvingCache = true;
    this.isRefreshingDetail = false;

    const onSubUpdate = (): void => this.emitDetail();
    this.detailSubs = [
      this.core.turmas.observeTurmaDetalhe(frontEndIdTurma).subscribe(onSubUpdate),
      this.core.turmas.observeFrequencia(frontEndIdTurma).subscribe(onSubUpdate),
      this.core.turmas.observeNotasParciais(frontEndIdTurma).subscribe(onSubUpdate),
      this.core.turmas.observeAvaliacoes(frontEndIdTurma).subscribe(onSubUpdate),
      this.core.turmas.observeTarefas(frontEndIdTurma).subscribe(onSubUpdate),
    ];

    this.emitDetail();

    const hasCached =
      this.core.turmas.observeTurmaDetalhe(frontEndIdTurma).getSnapshot() !== null;
    this.isResolvingCache = false;
    this.emitDetail();
    await this.refreshTurmaDetalheInternal(frontEndIdTurma, hasCached);
  };

  closeTurma = (): void => {
    this.clearDetailSubs();
    this.activeTurmaId = null;
    this.selectedDetailTab = 'TOPICOS';
    this.expandedNoticiaId = null;
    this.fullNoticiaById = {};
    this.loadingNoticiaIds = new Set();
    this.downloadingItemId = null;
    this.downloadedFileEvent = null;
    this.isResolvingCache = false;
    this.isRefreshingDetail = false;
    this.detailError = null;
    this.emitDetail();
  };

  selectDetailTab = (tab: TurmaDetailTab): void => {
    const id = this.activeTurmaId;
    if (!id) return;
    this.selectedDetailTab = tab;
    this.detailError = null;
    this.emitDetail();

    const snap = this.detailStateBox.value;
    switch (tab) {
      case 'TOPICOS':
      case 'NOTICIAS':
        if (!snap.detalhe) void this.refreshTurmaDetalheInternal(id, false);
        break;
      case 'FREQUENCIA':
        if (!snap.frequencia) void this.refreshFrequenciaInternal(id);
        break;
      case 'NOTAS_AVALIACOES':
        if (!snap.notasParciais || !snap.avaliacoes || !snap.tarefas) {
          void this.refreshNotasAvaliacoesTarefasInternal(id);
        }
        break;
    }
  };

  refreshActiveDetailTab = async (): Promise<void> => {
    const id = this.activeTurmaId;
    if (!id || this.isRefreshingDetail) return;
    switch (this.selectedDetailTab) {
      case 'TOPICOS':
      case 'NOTICIAS':
        await this.refreshTurmaDetalheInternal(id, false);
        break;
      case 'FREQUENCIA':
        await this.refreshFrequenciaInternal(id);
        break;
      case 'NOTAS_AVALIACOES':
        await this.refreshNotasAvaliacoesTarefasInternal(id);
        break;
    }
  };

  private async refreshTurmaDetalheInternal(id: string, silent: boolean): Promise<void> {
    if (!silent) {
      this.isRefreshingDetail = true;
      this.detailError = null;
      this.emitDetail();
    }
    const res = await this.core.turmas.refreshTurmaDetalhe(id);
    if (this.activeTurmaId !== id) return;
    if (!silent) {
      this.isRefreshingDetail = false;
      this.detailError = res.ok ? null : CoreErrorMapper.toUiError(res.error);
      this.emitDetail();
    }
  }

  private async refreshFrequenciaInternal(id: string): Promise<void> {
    this.isRefreshingDetail = true;
    this.detailError = null;
    this.emitDetail();
    const res = await this.core.turmas.refreshFrequencia(id);
    if (this.activeTurmaId !== id) return;
    this.isRefreshingDetail = false;
    this.detailError = res.ok ? null : CoreErrorMapper.toUiError(res.error);
    this.emitDetail();
  }

  private async refreshNotasAvaliacoesTarefasInternal(id: string): Promise<void> {
    this.isRefreshingDetail = true;
    this.detailError = null;
    this.emitDetail();

    const notasRes = await this.core.turmas.refreshNotasParciais(id);
    const avalRes = await this.core.turmas.refreshAvaliacoes(id);
    const tarRes = await this.core.turmas.refreshTarefas(id);
    if (this.activeTurmaId !== id) return;

    const firstError = !notasRes.ok
      ? notasRes.error
      : !avalRes.ok
        ? avalRes.error
        : !tarRes.ok
          ? tarRes.error
          : null;
    this.isRefreshingDetail = false;
    this.detailError = firstError ? CoreErrorMapper.toUiError(firstError) : null;
    this.emitDetail();
  }

  toggleNoticia = async (idNoticia: string): Promise<void> => {
    const idTurma = this.activeTurmaId;
    if (!idTurma) return;
    if (this.expandedNoticiaId === idNoticia) {
      this.expandedNoticiaId = null;
      this.emitDetail();
      return;
    }
    if (this.fullNoticiaById[idNoticia]) {
      this.expandedNoticiaId = idNoticia;
      this.emitDetail();
      return;
    }

    const currentDetalhe = this.detailStateBox.value.detalhe?.value;
    const latest = currentDetalhe?.ultimaNoticia;
    const resumo = currentDetalhe?.historicoNoticiasResumo.find(
      (r) => r.idNoticia === idNoticia,
    );
    if (
      latest &&
      resumo &&
      latest.titulo.trim().length > 0 &&
      latest.titulo.toLowerCase() === resumo.titulo.toLowerCase() &&
      latest.dataHoraTexto.trim().length > 0 &&
      latest.dataHoraTexto === resumo.dataHoraTexto
    ) {
      this.expandedNoticiaId = idNoticia;
      this.fullNoticiaById = {
        ...this.fullNoticiaById,
        [idNoticia]: { ...latest, idNoticia },
      };
      this.emitDetail();
      return;
    }

    if (this.loadingNoticiaIds.has(idNoticia)) {
      this.expandedNoticiaId = idNoticia;
      this.emitDetail();
      return;
    }

    this.expandedNoticiaId = idNoticia;
    this.loadingNoticiaIds.add(idNoticia);
    this.detailError = null;
    this.emitDetail();

    const res = await this.core.turmas.getNoticiaCompleta(idTurma, idNoticia);
    if (this.activeTurmaId !== idTurma) return;

    this.loadingNoticiaIds.delete(idNoticia);
    if (res.ok) {
      this.fullNoticiaById = { ...this.fullNoticiaById, [idNoticia]: res.value };
    } else {
      if (this.expandedNoticiaId === idNoticia) {
        this.expandedNoticiaId = null;
      }
      this.detailError = CoreErrorMapper.toUiError(res.error);
    }
    this.emitDetail();
  };

  downloadMaterial = async (materialId: string): Promise<void> => {
    const idTurma = this.activeTurmaId;
    if (!idTurma || this.downloadingItemId !== null) return;
    this.downloadingItemId = materialId;
    this.detailError = null;
    this.emitDetail();

    const res = await this.core.turmas.downloadMaterial(idTurma, materialId);
    if (this.activeTurmaId !== idTurma) return;
    this.downloadingItemId = null;
    if (res.ok) {
      this.downloadedFileEvent = res.value;
    } else {
      this.detailError = CoreErrorMapper.toUiError(res.error);
    }
    this.emitDetail();
  };

  downloadAnexoNoticia = async (anexoId: string): Promise<void> => {
    const idTurma = this.activeTurmaId;
    if (!idTurma || this.downloadingItemId !== null) return;
    this.downloadingItemId = anexoId;
    this.detailError = null;
    this.emitDetail();

    const res = await this.core.turmas.downloadAnexoNoticia(idTurma, anexoId);
    if (this.activeTurmaId !== idTurma) return;
    this.downloadingItemId = null;
    if (res.ok) {
      this.downloadedFileEvent = res.value;
    } else {
      this.detailError = CoreErrorMapper.toUiError(res.error);
    }
    this.emitDetail();
  };

  consumeDownloadedFile = (): void => {
    this.downloadedFileEvent = null;
    this.emitDetail();
  };

  dismissListError = (): void => {
    this.listError = null;
    this.emitList();
  };

  dismissDetailError = (): void => {
    this.detailError = null;
    this.emitDetail();
  };
}

// ============================================================================
// 4. DiscenteStore (port 1:1 de DiscenteViewModel.kt + PWA update state)
// ============================================================================

export interface DiscenteUiState {
  readonly authState: AuthState;
  readonly discenteResumo: SyncedData<DiscenteResumo> | null;
  readonly saldoExtrato: SyncedData<SaldoExtratoRu> | null;
  readonly carteirinha: SyncedData<CarteirinhaEstudantil> | null;
  readonly boletim: SyncedData<readonly BoletimSemestre[]> | null;
  readonly documentosEmitidos: readonly DocumentoOficialEmitido[];
  readonly hasResolvedInitialCache: boolean;
  readonly hasResolvedCarteirinhaCache: boolean;
  readonly hasResolvedBoletimCache: boolean;
  readonly isFlippedToQr: boolean;
  readonly boostQrBrightness: boolean;
  readonly isRefreshingProfile: boolean;
  readonly isRefreshingCarteirinha: boolean;
  readonly isRefreshingBoletim: boolean;
  readonly emittingDocumentoTipo: TipoDocumentoOficial | null;
  readonly lastEmittedDocumentoEvent: DocumentoOficialEmitido | null;
  readonly profileError: UiErrorBanner | null;
  readonly carteirinhaError: UiErrorBanner | null;
  readonly boletimError: UiErrorBanner | null;
  readonly documentosError: UiErrorBanner | null;
  readonly pwaUpdate: PwaUpdateState;
  readonly updateError: UiErrorBanner | null;
}

export class DiscenteStore {
  private readonly core: UnbPocketCore;
  private readonly unsubs: Unsubscribe[] = [];

  private hasResolvedInitialCache = false;
  private hasResolvedCarteirinhaCache = false;
  private hasResolvedBoletimCache = false;
  private isFlippedToQr = false;
  private boostQrBrightness = true;
  private isRefreshingProfile = false;
  private isRefreshingCarteirinha = false;
  private isRefreshingBoletim = false;
  private emittingDocumentoTipo: TipoDocumentoOficial | null = null;
  private lastEmittedDocumentoEvent: DocumentoOficialEmitido | null = null;
  private profileError: UiErrorBanner | null = null;
  private carteirinhaError: UiErrorBanner | null = null;
  private boletimError: UiErrorBanner | null = null;
  private documentosError: UiErrorBanner | null = null;
  private updateError: UiErrorBanner | null = null;

  private readonly stateBox: MutableStateValue<DiscenteUiState>;

  constructor(core: UnbPocketCore) {
    this.core = core;
    if (core.discente.observeDiscenteResumo().getSnapshot() !== null) {
      this.hasResolvedInitialCache = true;
    }
    if (core.discente.observeCarteirinha().getSnapshot() !== null) {
      this.hasResolvedCarteirinhaCache = true;
    }
    if (core.discente.observeBoletimGeral().getSnapshot() !== null) {
      this.hasResolvedBoletimCache = true;
    }

    this.stateBox = new MutableStateValue<DiscenteUiState>(this.computeState());

    this.unsubs.push(
      core.auth.authState.subscribe((auth) => {
        if (auth.kind === 'Authenticated') {
          this.profileError = withoutStaleAuth(this.profileError, auth);
          this.carteirinhaError = withoutStaleAuth(this.carteirinhaError, auth);
          this.boletimError = withoutStaleAuth(this.boletimError, auth);
          this.documentosError = withoutStaleAuth(this.documentosError, auth);
        }
        this.emit();
      }),
      core.discente.observeDiscenteResumo().subscribe(() => {
        this.hasResolvedInitialCache = true;
        this.emit();
      }),
      core.ru.observeSaldoExtrato().subscribe(() => this.emit()),
      core.discente.observeCarteirinha().subscribe(() => {
        this.hasResolvedCarteirinhaCache = true;
        this.emit();
      }),
      core.discente.observeBoletimGeral().subscribe(() => {
        this.hasResolvedBoletimCache = true;
        this.emit();
      }),
      core.discente.observeDocumentosEmitidos().subscribe(() => this.emit()),
      core.updates.state.subscribe(() => this.emit()),
    );
  }

  get uiState(): ObservableValue<DiscenteUiState> {
    return this.stateBox;
  }

  dispose(): void {
    for (const u of this.unsubs) u();
    this.unsubs.length = 0;
  }

  private computeState(): DiscenteUiState {
    const auth = this.core.auth.authState.getSnapshot();
    return {
      authState: auth,
      discenteResumo: this.core.discente.observeDiscenteResumo().getSnapshot(),
      saldoExtrato: this.core.ru.observeSaldoExtrato().getSnapshot(),
      carteirinha: this.core.discente.observeCarteirinha().getSnapshot(),
      boletim: this.core.discente.observeBoletimGeral().getSnapshot(),
      documentosEmitidos: this.core.discente.observeDocumentosEmitidos().getSnapshot(),
      hasResolvedInitialCache: this.hasResolvedInitialCache,
      hasResolvedCarteirinhaCache: this.hasResolvedCarteirinhaCache,
      hasResolvedBoletimCache: this.hasResolvedBoletimCache,
      isFlippedToQr: this.isFlippedToQr,
      boostQrBrightness: this.boostQrBrightness,
      isRefreshingProfile: this.isRefreshingProfile,
      isRefreshingCarteirinha: this.isRefreshingCarteirinha,
      isRefreshingBoletim: this.isRefreshingBoletim,
      emittingDocumentoTipo: this.emittingDocumentoTipo,
      lastEmittedDocumentoEvent: this.lastEmittedDocumentoEvent,
      profileError: withoutStaleAuth(this.profileError, auth),
      carteirinhaError: withoutStaleAuth(this.carteirinhaError, auth),
      boletimError: withoutStaleAuth(this.boletimError, auth),
      documentosError: withoutStaleAuth(this.documentosError, auth),
      pwaUpdate: this.core.updates.state.getSnapshot(),
      updateError: this.updateError,
    };
  }

  private emit(): void {
    this.stateBox.value = this.computeState();
  }

  toggleQrCardFace = (): void => {
    const nextFlipped = !this.isFlippedToQr;
    this.isFlippedToQr = nextFlipped;
    this.carteirinhaError = null;
    this.emit();
    if (nextFlipped && this.stateBox.value.carteirinha === null) {
      void this.refreshCarteirinha(false);
    }
  };

  resetQrCardToBalance = (): void => {
    this.isFlippedToQr = false;
    this.emit();
  };

  toggleBrightnessBoost = (): void => {
    this.boostQrBrightness = !this.boostQrBrightness;
    this.emit();
  };

  refreshProfileAndSaldo = async (): Promise<void> => {
    if (this.isRefreshingProfile) return;
    this.isRefreshingProfile = true;
    this.profileError = null;
    this.emit();

    const res = await this.core.refreshDiscentePerfilESaldo(false);
    this.hasResolvedInitialCache = true;
    this.isRefreshingProfile = false;
    this.profileError = res.ok ? null : CoreErrorMapper.toUiError(res.error);
    this.emit();
  };

  refreshCarteirinha = async (force: boolean = true): Promise<void> => {
    if (this.isRefreshingCarteirinha) return;
    this.isRefreshingCarteirinha = true;
    this.carteirinhaError = null;
    this.emit();

    const res = await this.core.discente.refreshCarteirinha(force);
    this.hasResolvedCarteirinhaCache = true;
    this.isRefreshingCarteirinha = false;
    this.carteirinhaError = res.ok ? null : CoreErrorMapper.toUiError(res.error);
    this.emit();
  };

  refreshBoletim = async (): Promise<void> => {
    if (this.isRefreshingBoletim) return;
    this.isRefreshingBoletim = true;
    this.boletimError = null;
    this.emit();

    const res = await this.core.discente.refreshBoletimGeral();
    this.hasResolvedBoletimCache = true;
    this.isRefreshingBoletim = false;
    this.boletimError = res.ok ? null : CoreErrorMapper.toUiError(res.error);
    this.emit();
  };

  emitirDocumento = async (tipo: TipoDocumentoOficial): Promise<void> => {
    if (tipo === 'CARTEIRINHA_ESTUDANTIL' || this.emittingDocumentoTipo !== null) return;
    this.emittingDocumentoTipo = tipo;
    this.documentosError = null;
    this.emit();

    const res = await this.core.discente.emitirDocumentoOficial(tipo);
    this.emittingDocumentoTipo = null;
    if (res.ok) {
      this.lastEmittedDocumentoEvent = res.value;
      this.documentosError = null;
    } else {
      this.documentosError = CoreErrorMapper.toUiError(res.error);
    }
    this.emit();
  };

  consumeEmittedDocumento = (): void => {
    this.lastEmittedDocumentoEvent = null;
    this.emit();
  };

  checkForUpdates = async (force: boolean = true): Promise<void> => {
    if (this.stateBox.value.pwaUpdate.checking) return;
    if (force) this.updateError = null;
    const res = await this.core.updates.checkForUpdates();
    if (!res.ok && force) {
      this.updateError = CoreErrorMapper.toUiError(res.error);
      this.emit();
    }
  };

  applyUpdate = async (): Promise<void> => {
    await this.core.updates.applyUpdate();
  };

  dismissProfileError = (): void => {
    this.profileError = null;
    this.emit();
  };

  dismissCarteirinhaError = (): void => {
    this.carteirinhaError = null;
    this.emit();
  };

  dismissBoletimError = (): void => {
    this.boletimError = null;
    this.emit();
  };

  dismissDocumentosError = (): void => {
    this.documentosError = null;
    this.emit();
  };

  dismissUpdateError = (): void => {
    this.updateError = null;
    this.emit();
  };
}

export interface AppStores {
  readonly core: UnbPocketCore;
  readonly authStore: AuthStore;
  readonly ruStore: RuStore;
  readonly turmasStore: TurmasStore;
  readonly discenteStore: DiscenteStore;
  dispose(): void;
}

export function createAppStores(
  core: UnbPocketCore,
  options: { autoBootstrap?: boolean; autoFetchRu?: boolean } = {},
): AppStores {
  const authStore = new AuthStore(core, options.autoBootstrap ?? true);
  const ruStore = new RuStore(core, options.autoFetchRu ?? true);
  const turmasStore = new TurmasStore(core);
  const discenteStore = new DiscenteStore(core);

  return {
    core,
    authStore,
    ruStore,
    turmasStore,
    discenteStore,
    dispose() {
      authStore.dispose();
      ruStore.dispose();
      turmasStore.dispose();
      discenteStore.dispose();
    },
  };
}
