import {
  CoreResults,
  IDLE_PRELOAD_PROGRESS,
  MutableStateValue,
  SigaaNotificacaoHelpers,
  type AuthRepository,
  type CoreError,
  type CoreResult,
  type DiscenteRepository,
  type DiscenteResumo,
  type ObservableValue,
  type PortalHomeSyncSummary,
  type PreloadPhase,
  type PreloadProgress,
  type PwaUpdateRepository,
  type PwaUpdateState,
  type RuRepository,
  type SigaaNotificacaoConfig,
  type TurmasRepository,
  type UnbPocketCore,
} from '../model/models';
import { UnbHttpTransport } from '../network/unbHttpSession';
import { DiscentePortalParser } from '../parser/discentePortalParser';
import { WorkerPdfExtractor, type PdfExtractor } from '../pdf/pdfExtractorClient';
import { BlobFileStore, type FileStore } from './local/blobFileStore';
import {
  RuPreferencesStore,
  SigaaNotificationPreferencesStore,
  SnapshotCacheStore,
} from './local/snapshotCacheStore';
import {
  BrowserNotificationPoster,
  PwaNotificationCoordinator,
  type NotificationDeepLinkEvent,
  type NotificationPosterPort,
} from './notification/pwaNotificationCoordinator';
import { DiscenteRepositoryImpl } from './repository/discenteRepositoryImpl';
import { RuRepositoryImpl } from './repository/ruRepositoryImpl';
import { TurmasRepositoryImpl } from './repository/turmasRepositoryImpl';
import { WebCryptoCredentialVault, type CredentialVault } from './security/webCryptoVault';
import { SigaaSessionCoordinator } from './session/sigaaSessionCoordinator';

export const APP_VERSION_NAME = '1.0.0';

export class PwaUpdateRepositoryImpl implements PwaUpdateRepository {
  private readonly stateBox: MutableStateValue<PwaUpdateState>;
  private readonly clockMillis: () => number;
  private swRegistration: ServiceWorkerRegistration | null = null;
  private applyCallback: (() => Promise<void>) | null = null;

  constructor(
    currentVersionName: string = APP_VERSION_NAME,
    clockMillis: () => number = () => Date.now(),
  ) {
    this.clockMillis = clockMillis;
    this.stateBox = new MutableStateValue<PwaUpdateState>({
      currentVersionName,
      updateReady: false,
      checking: false,
      lastCheckedAtEpochMillis: null,
    });
  }

  get state(): ObservableValue<PwaUpdateState> {
    return this.stateBox;
  }

  bindServiceWorker(params: {
    registration?: ServiceWorkerRegistration | null;
    updateReady?: boolean;
    applyUpdate?: () => Promise<void>;
  }): void {
    if (params.registration !== undefined) {
      this.swRegistration = params.registration;
    }
    if (params.applyUpdate) {
      this.applyCallback = params.applyUpdate;
    }
    if (params.updateReady !== undefined) {
      this.stateBox.update((prev) => ({
        ...prev,
        updateReady: params.updateReady ?? prev.updateReady,
      }));
    }
  }

  async checkForUpdates(): Promise<CoreResult<boolean>> {
    this.stateBox.update((prev) => ({ ...prev, checking: true }));
    try {
      let reg = this.swRegistration;
      if (!reg && typeof navigator !== 'undefined' && 'serviceWorker' in navigator) {
        reg = (await navigator.serviceWorker.getRegistration()) ?? null;
        this.swRegistration = reg;
      }
      if (reg) {
        await reg.update();
      }
      const ready = Boolean(reg?.waiting) || this.stateBox.value.updateReady;
      this.stateBox.update((prev) => ({
        ...prev,
        checking: false,
        updateReady: ready,
        lastCheckedAtEpochMillis: this.clockMillis(),
      }));
      return CoreResults.success(ready);
    } catch (err) {
      this.stateBox.update((prev) => ({
        ...prev,
        checking: false,
        lastCheckedAtEpochMillis: this.clockMillis(),
      }));
      return CoreResults.failure({
        kind: 'Unexpected',
        message:
          err instanceof Error
            ? err.message
            : 'Nao foi possivel verificar atualizacoes do aplicativo web.',
      });
    }
  }

  async applyUpdate(): Promise<void> {
    if (this.applyCallback) {
      await this.applyCallback();
      return;
    }
    if (this.swRegistration?.waiting) {
      this.swRegistration.waiting.postMessage({ type: 'SKIP_WAITING' });
    }
    if (typeof window !== 'undefined') {
      window.location.reload();
    }
  }
}

export interface UnbPocketCoreInstance extends UnbPocketCore {
  readonly notifications: PwaNotificationCoordinator;
  readonly pwaUpdates: PwaUpdateRepositoryImpl;
}

class DefaultUnbPocketCore implements UnbPocketCoreInstance {
  readonly auth: AuthRepository;
  readonly ru: RuRepository;
  readonly turmas: TurmasRepository;
  readonly discente: DiscenteRepository;
  readonly updates: PwaUpdateRepository;
  readonly pwaUpdates: PwaUpdateRepositoryImpl;
  readonly notifications: PwaNotificationCoordinator;

  private readonly coordinator: SigaaSessionCoordinator;
  private readonly ruRepo: RuRepositoryImpl;
  private readonly turmasRepo: TurmasRepositoryImpl;
  private readonly discenteRepo: DiscenteRepositoryImpl;
  private readonly fileStore: FileStore;
  private readonly clockMillis: () => number;

  private readonly preloadState = new MutableStateValue<PreloadProgress>(IDLE_PRELOAD_PROGRESS);
  private preloadGeneration = 0;

  constructor(params: {
    coordinator: SigaaSessionCoordinator;
    ruRepo: RuRepositoryImpl;
    turmasRepo: TurmasRepositoryImpl;
    discenteRepo: DiscenteRepositoryImpl;
    pwaUpdates: PwaUpdateRepositoryImpl;
    notifications: PwaNotificationCoordinator;
    fileStore: FileStore;
    clockMillis: () => number;
  }) {
    this.coordinator = params.coordinator;
    this.ruRepo = params.ruRepo;
    this.turmasRepo = params.turmasRepo;
    this.discenteRepo = params.discenteRepo;
    this.pwaUpdates = params.pwaUpdates;
    this.notifications = params.notifications;
    this.fileStore = params.fileStore;
    this.clockMillis = params.clockMillis;

    this.auth = params.coordinator;
    this.ru = params.ruRepo;
    this.turmas = params.turmasRepo;
    this.discente = params.discenteRepo;
    this.updates = params.pwaUpdates;
  }

  get preloadProgress(): ObservableValue<PreloadProgress> {
    return this.preloadState;
  }

  cancelAndResetPreload(): void {
    this.preloadGeneration += 1;
    this.preloadState.value = IDLE_PRELOAD_PROGRESS;
  }

  async readCachedBlob(localFilePath: string): Promise<Blob | null> {
    return this.fileStore.readBlob(localFilePath);
  }

  async syncPortalHome(includeSaldoRu: boolean = true): Promise<CoreResult<PortalHomeSyncSummary>> {
    this.preloadState.update((prev) => ({
      ...prev,
      phase: 'RUNNING_ESSENTIAL',
      currentTarget: 'Portal Discente',
      lastError: null,
    }));

    const result = await this.coordinator.withAuthenticatedJsf('INTERACTIVE', async (nav) => {
      const cachedLoginHtml = this.coordinator.consumeFreshLoginPortalHtml();
      let homeHtml: string;
      if (cachedLoginHtml !== null) {
        homeHtml = cachedLoginHtml;
      } else {
        const home = await nav.getPortalHomeHtml();
        if (!home.ok) return home;
        homeHtml = home.value;
      }

      let anyChanged = false;
      let resumoRes = await this.discenteRepo.persistResumoFromHomeHtmlWithDiff(nav, homeHtml);
      if (!resumoRes.ok && cachedLoginHtml !== null) {
        const directHome = await nav.getPortalHomeHtml();
        if (!directHome.ok) return directHome;
        homeHtml = directHome.value;
        resumoRes = await this.discenteRepo.persistResumoFromHomeHtmlWithDiff(nav, homeHtml);
      }
      if (!resumoRes.ok) return resumoRes;
      if (resumoRes.value.changed) anyChanged = true;

      let saldoAtualizado = false;
      if (includeSaldoRu) {
        const saldoRes = await this.ruRepo.refreshSaldoExtratoWithDiff(nav, homeHtml);
        if (!saldoRes.ok) {
          if (saldoRes.error.kind === 'SessionExpired') return saldoRes;
        } else {
          if (saldoRes.value.changed) anyChanged = true;
          saldoAtualizado = true;
        }
      }

      const turmasRes = await this.turmasRepo.persistHomeTurmasAndFeedWithDiff(
        nav,
        homeHtml,
        true,
      );
      if (!turmasRes.ok) return turmasRes;
      if (turmasRes.value.changed) anyChanged = true;

      const feedCount = DiscentePortalParser.parseAtualizacoesFeed(homeHtml).length;
      return CoreResults.success({
        syncedAtEpochMillis: this.clockMillis(),
        turmasCount: turmasRes.value.turmas.length,
        atualizacoesCount: feedCount,
        saldoRuAtualizado: saldoAtualizado,
        anyDataChanged: anyChanged,
      });
    });

    if (result.ok) {
      const prev = this.preloadState.value;
      if (prev.phase === 'RUNNING_ESSENTIAL') {
        this.preloadState.value = {
          ...prev,
          phase: 'COMPLETED',
          currentTarget: null,
          updatedAnyData: prev.updatedAnyData || result.value.anyDataChanged,
        };
      }
    } else {
      this.preloadState.update((prev) => ({
        ...prev,
        phase: 'IDLE',
        currentTarget: null,
        lastError: result.error,
      }));
    }

    return result;
  }

  async refreshDiscentePerfilESaldo(
    forceDownloadFoto: boolean = false,
  ): Promise<CoreResult<DiscenteResumo>> {
    return this.coordinator.withAuthenticatedJsf('INTERACTIVE', async (nav) => {
      const home = await nav.getPortalHomeHtml();
      if (!home.ok) return home;

      const resumoRes = await this.discenteRepo.persistResumoFromHomeHtmlWithDiff(
        nav,
        home.value,
        forceDownloadFoto,
      );
      if (!resumoRes.ok) return resumoRes;

      const saldoRes = await this.ruRepo.refreshSaldoExtratoWithDiff(nav, home.value);
      if (!saldoRes.ok && saldoRes.error.kind === 'SessionExpired') {
        return saldoRes;
      }

      return CoreResults.success(resumoRes.value.resumo);
    });
  }

  startBackgroundPreload(includeTurmaDetails: boolean = true): void {
    this.preloadGeneration += 1;
    const gen = this.preloadGeneration;
    void this.runPhase2Preload(gen, includeTurmaDetails);
  }

  async silentRefreshAll(
    includeTurmaDetails: boolean = false,
  ): Promise<CoreResult<PortalHomeSyncSummary>> {
    const session = await this.coordinator.ensureValidSession();
    if (!session.ok) {
      await this.notifications.evaluateUpdatesAndNotify(true);
      await this.notifications.syncLocalSchedules(true);
      return session;
    }

    const summary = await this.syncPortalHome(true);
    if (summary.ok) {
      this.startBackgroundPreload(includeTurmaDetails);
      await this.notifications.evaluateUpdatesAndNotify(true);
      await this.notifications.syncLocalSchedules(true);
    } else {
      await this.notifications.evaluateUpdatesAndNotify(true);
      await this.notifications.syncLocalSchedules(true);
    }
    return summary;
  }

  observeSigaaNotificacoesConfig(): ObservableValue<SigaaNotificacaoConfig> {
    return this.notifications.observeConfig();
  }

  async setSigaaNotificacoesConfig(config: SigaaNotificacaoConfig): Promise<CoreResult<void>> {
    return this.notifications.setConfig(config);
  }

  async autoEnableSigaaNotificacoesIfEligible(): Promise<CoreResult<boolean>> {
    return this.notifications.autoEnableIfEligible();
  }

  private async runPhase2Preload(gen: number, includeTurmaDetails: boolean): Promise<void> {
    const isCancelled = (): boolean => this.preloadGeneration !== gen;

    const turmas = await this.turmasRepo.getCachedTurmasSnapshot();
    if (isCancelled()) return;

    const totalSteps = 2 + (includeTurmaDetails ? turmas.length * 5 : 0);
    let completed = 0;
    let anyUpdated = this.preloadState.value.updatedAnyData;
    let lastError: CoreError | null = null;
    let abortedByAuth = false;

    const publish = (
      target: string | null,
      phase: PreloadPhase = 'RUNNING_BACKGROUND',
    ): void => {
      if (isCancelled()) return;
      this.preloadState.value = {
        phase,
        completedSteps: completed,
        totalSteps,
        currentTarget: target,
        updatedAnyData: anyUpdated,
        lastError,
      };
    };

    const runStep = async (
      label: string,
      block: () => Promise<CoreResult<boolean>>,
    ): Promise<void> => {
      if (abortedByAuth || isCancelled()) return;
      publish(label);
      try {
        const res = await block();
        if (isCancelled()) return;
        if (res.ok) {
          if (res.value) anyUpdated = true;
        } else {
          lastError = res.error;
          if (
            res.error.kind === 'SessionExpired' ||
            res.error.kind === 'NoStoredCredentials' ||
            res.error.kind === 'InvalidCredentials'
          ) {
            abortedByAuth = true;
          }
        }
      } finally {
        completed += 1;
      }
    };

    await runStep('Carteirinha Estudantil', async () => {
      const res = await this.discenteRepo.refreshCarteirinhaWithDiff(false, 'BACKGROUND_PRELOAD');
      return res.ok ? CoreResults.success(res.value.changed) : res;
    });

    await runStep('Boletim Geral', async () => {
      const res = await this.discenteRepo.refreshBoletimGeralWithDiff('BACKGROUND_PRELOAD');
      return res.ok ? CoreResults.success(res.value.changed) : res;
    });

    if (includeTurmaDetails) {
      for (const turma of turmas) {
        if (isCancelled() || abortedByAuth) break;
        const labelBase = turma.codigoDisciplina ?? turma.nomeDisciplina;

        await runStep(`${labelBase} · Tópicos`, async () => {
          const res = await this.turmasRepo.refreshTurmaDetalheWithDiff(
            turma.frontEndIdTurma,
            'BACKGROUND_PRELOAD',
          );
          return res.ok ? CoreResults.success(res.value.changed) : res;
        });

        await runStep(`${labelBase} · Frequência`, async () => {
          const res = await this.turmasRepo.refreshFrequenciaWithDiff(
            turma.frontEndIdTurma,
            'BACKGROUND_PRELOAD',
          );
          return res.ok ? CoreResults.success(res.value.changed) : res;
        });

        await runStep(`${labelBase} · Notas`, async () => {
          const res = await this.turmasRepo.refreshNotasParciaisWithDiff(
            turma.frontEndIdTurma,
            'BACKGROUND_PRELOAD',
          );
          return res.ok ? CoreResults.success(res.value.changed) : res;
        });

        await runStep(`${labelBase} · Avaliações`, async () => {
          const res = await this.turmasRepo.refreshAvaliacoesWithDiff(
            turma.frontEndIdTurma,
            'BACKGROUND_PRELOAD',
          );
          return res.ok ? CoreResults.success(res.value.changed) : res;
        });

        await runStep(`${labelBase} · Tarefas`, async () => {
          const res = await this.turmasRepo.refreshTarefasWithDiff(
            turma.frontEndIdTurma,
            'BACKGROUND_PRELOAD',
          );
          return res.ok ? CoreResults.success(res.value.changed) : res;
        });
      }
    }

    if (isCancelled()) return;
    publish(null, 'COMPLETED');

    const config = this.notifications.getConfig();
    if (SigaaNotificacaoHelpers.hasAnyEnabled(config)) {
      await this.notifications.evaluateUpdatesAndNotify(true);
      if (SigaaNotificacaoHelpers.hasAnyLocalScheduleEnabled(config)) {
        await this.notifications.prepareAndScheduleLocalReminders(true);
      }
    }
  }
}

export interface CreateUnbPocketCoreOptions {
  transport?: UnbHttpTransport;
  cacheStore?: SnapshotCacheStore;
  ruPrefs?: RuPreferencesStore;
  sigaaNotifPrefs?: SigaaNotificationPreferencesStore;
  vault?: CredentialVault;
  fileStore?: FileStore;
  pdfExtractor?: PdfExtractor;
  notificationPoster?: NotificationPosterPort;
  onDeepLink?: (event: NotificationDeepLinkEvent) => void;
  clockMillis?: () => number;
}

let sharedCoreInstance: UnbPocketCoreInstance | null = null;

export const UnbPocketCoreFactory = {
  create(onDeepLink?: (event: NotificationDeepLinkEvent) => void): UnbPocketCoreInstance {
    if (!sharedCoreInstance) {
      sharedCoreInstance = this.createWithPorts({ onDeepLink });
      void sharedCoreInstance.notifications.syncLocalSchedules(true);
    }
    return sharedCoreInstance;
  },

  createWithPorts(options: CreateUnbPocketCoreOptions = {}): UnbPocketCoreInstance {
    const clockMillis = options.clockMillis ?? (() => Date.now());
    const transport = options.transport ?? new UnbHttpTransport();
    const cacheStore = options.cacheStore ?? new SnapshotCacheStore();
    const ruPrefs = options.ruPrefs ?? new RuPreferencesStore();
    const sigaaNotifPrefs = options.sigaaNotifPrefs ?? new SigaaNotificationPreferencesStore();
    const vault = options.vault ?? new WebCryptoCredentialVault();
    const fileStore = options.fileStore ?? new BlobFileStore();
    const pdfExtractor = options.pdfExtractor ?? new WorkerPdfExtractor();
    const poster =
      options.notificationPoster ?? new BrowserNotificationPoster(options.onDeepLink);

    let onLogoutCallback: (clearCachedData: boolean) => Promise<void> = async () => undefined;
    let onRuNotifCallback: () => void = () => undefined;

    const coordinator = new SigaaSessionCoordinator({
      transport,
      vault,
      cacheStore,
      fileStore,
      clockMillis,
      onLogoutHook: async (clearCachedData) => {
        await onLogoutCallback(clearCachedData);
      },
    });

    const ruRepo = new RuRepositoryImpl({
      transport,
      coordinator,
      cacheStore,
      prefsStore: ruPrefs,
      fileStore,
      pdfExtractor,
      clockMillis,
      onRuNotificacoesChanged: () => {
        onRuNotifCallback();
      },
    });

    const turmasRepo = new TurmasRepositoryImpl({
      coordinator,
      cacheStore,
      fileStore,
      clockMillis,
    });

    const discenteRepo = new DiscenteRepositoryImpl({
      coordinator,
      cacheStore,
      fileStore,
      pdfExtractor,
    });

    const pwaUpdates = new PwaUpdateRepositoryImpl(APP_VERSION_NAME, clockMillis);

    const notifications = new PwaNotificationCoordinator({
      cacheStore,
      sigaaPrefs: sigaaNotifPrefs,
      ruPrefs,
      poster,
      prepareRuMealContent: (refeicao, now) => ruRepo.prepareNotificacaoRefeicao(refeicao, now),
      clockMillis,
    });

    const core = new DefaultUnbPocketCore({
      coordinator,
      ruRepo,
      turmasRepo,
      discenteRepo,
      pwaUpdates,
      notifications,
      fileStore,
      clockMillis,
    });

    onLogoutCallback = async (clearCachedData: boolean) => {
      core.cancelAndResetPreload();
      turmasRepo.clearRuntimeState();
      await notifications.onLogout(clearCachedData);
    };

    onRuNotifCallback = () => {
      void notifications.evaluateAndScheduleRuNotifications(
        ruPrefs.preferencias.getSnapshot().notificacoes,
        true,
      );
    };

    return core;
  },
};
