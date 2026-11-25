import {
  CoreResults,
  DEFAULT_SIGAA_NOTIFICACAO_CONFIG,
  SigaaNotificacaoHelpers,
  type AtualizacaoTurmaFeed,
  type AvaliacaoTurma,
  type BoletimSemestre,
  type CoreResult,
  type DiscenteResumo,
  type MapaFrequenciaTurma,
  type NotasParciaisTurma,
  type ObservableValue,
  type RuDeepLinkTarget,
  type RuNotificacaoConfig,
  type RuNotificacaoConteudo,
  type SaldoExtratoRu,
  type SigaaDeepLinkTarget,
  type SigaaNotificacaoConfig,
  type SigaaNotificacaoConteudo,
  type TarefaTurma,
  type TipoRefeicao,
  type TurmaDetalheAva,
  type TurmaResumo,
} from '../../model/models';
import { getBrasiliaParts } from '../../parser/brasiliaTime';
import { RuNotificationPlanner } from '../../parser/ruNotificationPlanner';
import {
  SigaaNotificationPlanner,
  type SigaaNotificationBaseline,
  type SigaaSnapshotInput,
} from '../../parser/sigaaNotificationPlanner';
import {
  CacheDomains,
  CacheKeys,
  type RuPreferencesStore,
  type SigaaNotificationPreferencesStore,
  type SnapshotCacheStore,
} from '../local/snapshotCacheStore';

const SENT_KEY_RETENTION_MILLIS = 14 * 24 * 60 * 60 * 1000;
const RU_SENT_KEY_CACHE_KEY = 'ru_notif_sent_keys';
const MAX_SAFE_TIMEOUT_MILLIS = 24 * 60 * 60 * 1000;

export type NotificationDeepLinkEvent =
  | { readonly kind: 'sigaa'; readonly payload: SigaaDeepLinkTarget }
  | { readonly kind: 'ru'; readonly payload: RuDeepLinkTarget };

export interface NotificationPosterPort {
  postSigaa(items: readonly SigaaNotificacaoConteudo[]): Promise<void>;
  postRu(item: RuNotificacaoConteudo): Promise<void>;
}

export class BrowserNotificationPoster implements NotificationPosterPort {
  private readonly onDeepLink?: (event: NotificationDeepLinkEvent) => void;

  constructor(onDeepLink?: (event: NotificationDeepLinkEvent) => void) {
    this.onDeepLink = onDeepLink;
  }

  async postSigaa(items: readonly SigaaNotificacaoConteudo[]): Promise<void> {
    if (!isNotificationPermitted()) return;
    for (const item of items) {
      const body = item.detalhesExpandido
        ? `${item.resumo}\n${item.detalhesExpandido}`
        : item.resumo;
      await this.dispatchSystemNotification({
        title: item.titulo,
        body,
        tag: item.dedupKey,
        deepLink: { kind: 'sigaa', payload: { target: item.target } },
      });
    }
  }

  async postRu(item: RuNotificacaoConteudo): Promise<void> {
    if (!isNotificationPermitted()) return;
    await this.dispatchSystemNotification({
      title: item.titulo,
      body: item.mensagem,
      tag: `ru_meal:${item.campus}:${item.dataTexto}:${item.refeicao}`,
      deepLink: {
        kind: 'ru',
        payload: {
          campus: item.campus,
          dataTexto: item.dataTexto,
          dayIndex: item.dayIndex,
          refeicao: item.refeicao,
        },
      },
    });
  }

  private async dispatchSystemNotification(params: {
    title: string;
    body: string;
    tag: string;
    deepLink: NotificationDeepLinkEvent;
  }): Promise<void> {
    try {
      if (typeof navigator !== 'undefined' && 'serviceWorker' in navigator) {
        const reg = await navigator.serviceWorker.getRegistration();
        if (reg && typeof reg.showNotification === 'function') {
          await reg.showNotification(params.title, {
            body: params.body,
            tag: params.tag,
            icon: '/icons/icon-192.png',
            badge: '/icons/icon-192.png',
            data: params.deepLink,
          });
          return;
        }
      }
      if (typeof Notification !== 'undefined') {
        const n = new Notification(params.title, {
          body: params.body,
          tag: params.tag,
          icon: '/icons/icon-192.png',
        });
        n.onclick = () => {
          if (typeof window !== 'undefined') window.focus();
          this.onDeepLink?.(params.deepLink);
          n.close();
        };
      }
    } catch {
      // Best-effort browser notification dispatch
    }
  }
}

export function isNotificationPermitted(): boolean {
  return typeof Notification !== 'undefined' && Notification.permission === 'granted';
}

export async function requestBrowserNotificationPermission(): Promise<NotificationPermission | 'unsupported'> {
  if (typeof Notification === 'undefined') return 'unsupported';
  if (Notification.permission === 'granted') return 'granted';
  try {
    return await Notification.requestPermission();
  } catch {
    return Notification.permission;
  }
}

export class PwaNotificationCoordinator {
  private readonly cacheStore: SnapshotCacheStore;
  private readonly sigaaPrefs: SigaaNotificationPreferencesStore;
  private readonly ruPrefs: RuPreferencesStore;
  private readonly poster: NotificationPosterPort;
  private readonly prepareRuMealContent: (
    refeicao: TipoRefeicao,
    nowEpochMillis: number,
  ) => Promise<CoreResult<RuNotificacaoConteudo | null>>;
  private readonly clockMillis: () => number;

  private lockChain: Promise<unknown> = Promise.resolve();
  private sigaaReminderTimer: ReturnType<typeof setTimeout> | null = null;
  private ruReminderTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(params: {
    cacheStore: SnapshotCacheStore;
    sigaaPrefs: SigaaNotificationPreferencesStore;
    ruPrefs: RuPreferencesStore;
    poster: NotificationPosterPort;
    prepareRuMealContent: (
      refeicao: TipoRefeicao,
      nowEpochMillis: number,
    ) => Promise<CoreResult<RuNotificacaoConteudo | null>>;
    clockMillis?: () => number;
  }) {
    this.cacheStore = params.cacheStore;
    this.sigaaPrefs = params.sigaaPrefs;
    this.ruPrefs = params.ruPrefs;
    this.poster = params.poster;
    this.prepareRuMealContent = params.prepareRuMealContent;
    this.clockMillis = params.clockMillis ?? (() => Date.now());
  }

  private withStateLock<T>(fn: () => Promise<T>): Promise<T> {
    const next = this.lockChain.then(fn, fn);
    this.lockChain = next.then(
      () => undefined,
      () => undefined,
    );
    return next;
  }

  observeConfig(): ObservableValue<SigaaNotificacaoConfig> {
    return this.sigaaPrefs.config;
  }

  getConfig(): SigaaNotificacaoConfig {
    return this.sigaaPrefs.config.getSnapshot();
  }

  async setConfig(config: SigaaNotificacaoConfig): Promise<CoreResult<void>> {
    try {
      const previous = this.getConfig();
      await this.sigaaPrefs.setConfig(config);
      if (SigaaNotificacaoHelpers.hasAnyEnabled(config)) {
        if (!SigaaNotificacaoHelpers.hasAnyEnabled(previous)) {
          await this.seedMissingBaselineFromCache();
        }
        await this.evaluateUpdatesAndNotify(true);
      }
      await this.syncLocalSchedules(true);
      return CoreResults.success(undefined);
    } catch (err) {
      return CoreResults.failure({
        kind: 'StorageError',
        operation: 'setSigaaNotificacoesConfig',
        reason: err instanceof Error ? err.message : '',
      });
    }
  }

  async autoEnableIfEligible(): Promise<CoreResult<boolean>> {
    try {
      const current = this.getConfig();
      if (current.userConfigured || current.habilitado) {
        return CoreResults.success(false);
      }
      const enabled: SigaaNotificacaoConfig = { ...current, habilitado: true };
      await this.sigaaPrefs.setConfig(enabled);
      await this.seedMissingBaselineFromCache();
      await this.syncLocalSchedules(false);
      return CoreResults.success(true);
    } catch (err) {
      return CoreResults.failure({
        kind: 'StorageError',
        operation: 'autoEnableSigaaNotificacoesIfEligible',
        reason: err instanceof Error ? err.message : '',
      });
    }
  }

  async seedMissingBaselineFromCache(): Promise<void> {
    const config = this.getConfig();
    if (!SigaaNotificacaoHelpers.hasAnyEnabled(config)) return;

    await this.withStateLock(async () => {
      const now = this.clockMillis();
      const previous = await this.loadBaseline();
      const snapshot = await this.loadSnapshotInput();
      const result = SigaaNotificationPlanner.detectSnapshotChanges(
        previous,
        snapshot,
        config,
        now,
      );
      if (
        previous === null ||
        JSON.stringify(result.updatedBaseline) !== JSON.stringify(previous)
      ) {
        await this.saveBaseline(result.updatedBaseline, now);
      }
    });
  }

  async evaluateUpdatesAndNotify(
    postNotifications: boolean = true,
  ): Promise<readonly SigaaNotificacaoConteudo[]> {
    const config = this.getConfig();
    if (!SigaaNotificacaoHelpers.hasAnyEnabled(config)) return [];

    const posted = await this.withStateLock(async () => {
      const now = this.clockMillis();
      const previous = await this.loadBaseline();
      const snapshot = await this.loadSnapshotInput();
      const diff = SigaaNotificationPlanner.detectSnapshotChanges(
        previous,
        snapshot,
        config,
        now,
      );
      if (
        previous === null ||
        JSON.stringify(diff.updatedBaseline) !== JSON.stringify(previous)
      ) {
        await this.saveBaseline(diff.updatedBaseline, now);
      }

      if (!postNotifications || diff.notifications.length === 0) {
        return [] as SigaaNotificacaoConteudo[];
      }

      const sentMap = { ...(await this.loadPrunedSentKeys(CacheKeys.SIGAA_NOTIF_SENT_KEYS, now)) };
      const fresh = diff.notifications.filter((it) => !(it.dedupKey in sentMap));
      if (fresh.length > 0) {
        for (const item of fresh) {
          sentMap[item.dedupKey] = now;
        }
        await this.saveSentKeys(CacheKeys.SIGAA_NOTIF_SENT_KEYS, sentMap, now);
      }
      return fresh;
    });

    if (posted.length > 0) {
      await this.poster.postSigaa(posted);
    }
    return posted;
  }

  async prepareAndScheduleLocalReminders(postDueNow: boolean = false): Promise<number | null> {
    this.clearSigaaTimer();
    const config = this.getConfig();
    if (!SigaaNotificacaoHelpers.hasAnyLocalScheduleEnabled(config)) return null;

    const { dueItems, nextTrigger } = await this.withStateLock(async () => {
      const now = this.clockMillis();
      const turmas =
        (await this.cacheStore.get<readonly TurmaResumo[]>(CacheKeys.TURMAS_LIST))?.value ?? [];

      const avaliacoesByTurma: Record<string, readonly AvaliacaoTurma[]> = {};
      const tarefasByTurma: Record<string, readonly TarefaTurma[]> = {};

      for (const turma of turmas) {
        const avals =
          (
            await this.cacheStore.get<readonly AvaliacaoTurma[]>(
              CacheKeys.turmaAvaliacoes(turma.frontEndIdTurma),
            )
          )?.value ?? [];
        if (avals.length > 0) {
          avaliacoesByTurma[turma.frontEndIdTurma] = avals;
        }

        const tars =
          (
            await this.cacheStore.get<readonly TarefaTurma[]>(
              CacheKeys.turmaTarefas(turma.frontEndIdTurma),
            )
          )?.value ?? [];
        if (tars.length > 0) {
          tarefasByTurma[turma.frontEndIdTurma] = tars;
        }
      }

      const reminders = SigaaNotificationPlanner.buildUpcomingReminders({
        turmas,
        avaliacoesByTurma,
        tarefasByTurma,
        config,
        nowEpochMillis: now,
      });

      const sentMap = { ...(await this.loadPrunedSentKeys(CacheKeys.SIGAA_NOTIF_SENT_KEYS, now)) };
      const sentSet = new Set(Object.keys(sentMap));
      const due = postDueNow
        ? SigaaNotificationPlanner.extractDueReminders(reminders, sentSet, now)
        : [];

      if (due.length > 0) {
        for (const r of due) {
          sentMap[r.notificacao.dedupKey] = now;
          sentSet.add(r.notificacao.dedupKey);
        }
        await this.saveSentKeys(CacheKeys.SIGAA_NOTIF_SENT_KEYS, sentMap, now);
      }

      const upcoming = reminders.find(
        (r) => r.triggerAtEpochMillis > now && !sentSet.has(r.notificacao.dedupKey),
      );
      return {
        dueItems: due.map((r) => r.notificacao),
        nextTrigger: upcoming?.triggerAtEpochMillis ?? null,
      };
    });

    if (dueItems.length > 0) {
      await this.poster.postSigaa(dueItems);
    }

    if (nextTrigger !== null) {
      const delay = Math.min(Math.max(1000, nextTrigger - this.clockMillis()), MAX_SAFE_TIMEOUT_MILLIS);
      this.sigaaReminderTimer = setTimeout(() => {
        void this.prepareAndScheduleLocalReminders(true);
      }, delay);
    }

    return nextTrigger;
  }

  async evaluateAndScheduleRuNotifications(
    config: RuNotificacaoConfig = this.ruPrefs.preferencias.getSnapshot().notificacoes,
    postCatchUpNow: boolean = true,
  ): Promise<number | null> {
    this.clearRuTimer();
    const now = this.clockMillis();
    const sentMap = { ...(await this.loadPrunedSentKeys(RU_SENT_KEY_CACHE_KEY, now)) };
    const sentSet = new Set(Object.keys(sentMap));

    if (postCatchUpNow) {
      const catchUp = RuNotificationPlanner.findCatchUpMeal(config, sentSet, now);
      if (catchUp) {
        const contentRes = await this.prepareRuMealContent(catchUp.refeicao, now);
        if (contentRes.ok && contentRes.value) {
          const todayIso = getBrasiliaParts(now).isoDate;
          const slotKey = RuNotificationPlanner.sentSlotKey(todayIso, catchUp.refeicao);
          sentMap[slotKey] = now;
          await this.saveSentKeys(RU_SENT_KEY_CACHE_KEY, sentMap, now);
          await this.poster.postRu(contentRes.value);
        }
      }
    }

    const next = RuNotificationPlanner.findNextTrigger(config, this.clockMillis());
    if (next) {
      const delay = Math.min(
        Math.max(1000, next.triggerEpochMillis - this.clockMillis()),
        MAX_SAFE_TIMEOUT_MILLIS,
      );
      this.ruReminderTimer = setTimeout(() => {
        void this.evaluateAndScheduleRuNotifications(
          this.ruPrefs.preferencias.getSnapshot().notificacoes,
          true,
        );
      }, delay);
      return next.triggerEpochMillis;
    }
    return null;
  }

  async syncLocalSchedules(postDueNow: boolean = true): Promise<void> {
    await this.prepareAndScheduleLocalReminders(postDueNow);
    await this.evaluateAndScheduleRuNotifications(
      this.ruPrefs.preferencias.getSnapshot().notificacoes,
      postDueNow,
    );
  }

  async onLogout(clearCachedData: boolean): Promise<void> {
    this.clearSigaaTimer();
    if (clearCachedData) {
      await this.sigaaPrefs.resetForLogout();
      await this.cacheStore.clearKey(CacheKeys.SIGAA_NOTIF_BASELINE);
      await this.cacheStore.clearKey(CacheKeys.SIGAA_NOTIF_SENT_KEYS);
    } else {
      await this.prepareAndScheduleLocalReminders(false);
    }
  }

  dispose(): void {
    this.clearSigaaTimer();
    this.clearRuTimer();
  }

  private clearSigaaTimer(): void {
    if (this.sigaaReminderTimer !== null) {
      clearTimeout(this.sigaaReminderTimer);
      this.sigaaReminderTimer = null;
    }
  }

  private clearRuTimer(): void {
    if (this.ruReminderTimer !== null) {
      clearTimeout(this.ruReminderTimer);
      this.ruReminderTimer = null;
    }
  }

  private async loadSnapshotInput(): Promise<SigaaSnapshotInput> {
    const turmas =
      (await this.cacheStore.get<readonly TurmaResumo[]>(CacheKeys.TURMAS_LIST))?.value ?? [];
    const feedSynced = await this.cacheStore.get<readonly AtualizacaoTurmaFeed[]>(
      CacheKeys.TURMAS_FEED,
    );

    const detalhesPorTurma: Record<string, TurmaDetalheAva> = {};
    const notasPorTurma: Record<string, NotasParciaisTurma> = {};
    const frequenciaPorTurma: Record<string, MapaFrequenciaTurma> = {};

    for (const turma of turmas) {
      const det = await this.cacheStore.get<TurmaDetalheAva>(
        CacheKeys.turmaDetalhe(turma.frontEndIdTurma),
      );
      if (det) detalhesPorTurma[turma.frontEndIdTurma] = det.value;

      const notas = await this.cacheStore.get<NotasParciaisTurma>(
        CacheKeys.turmaNotas(turma.frontEndIdTurma),
      );
      if (notas) notasPorTurma[turma.frontEndIdTurma] = notas.value;

      const freq = await this.cacheStore.get<MapaFrequenciaTurma>(
        CacheKeys.turmaFrequencia(turma.frontEndIdTurma),
      );
      if (freq) frequenciaPorTurma[turma.frontEndIdTurma] = freq.value;
    }

    const saldoRu =
      (await this.cacheStore.get<SaldoExtratoRu>(CacheKeys.RU_SALDO))?.value ?? null;
    const boletim =
      (await this.cacheStore.get<readonly BoletimSemestre[]>(CacheKeys.DISCENTE_BOLETIM))
        ?.value ?? null;
    const indices =
      (await this.cacheStore.get<DiscenteResumo>(CacheKeys.DISCENTE_RESUMO))?.value.indices ??
      null;

    return {
      turmas,
      feed: feedSynced?.value ?? [],
      feedAvailable: feedSynced !== null,
      detalhesPorTurma,
      notasPorTurma,
      frequenciaPorTurma,
      saldoExtratoRu: saldoRu,
      boletim,
      indices,
    };
  }

  private async loadBaseline(): Promise<SigaaNotificationBaseline | null> {
    return (
      (
        await this.cacheStore.get<SigaaNotificationBaseline>(
          CacheKeys.SIGAA_NOTIF_BASELINE,
        )
      )?.value ?? null
    );
  }

  private async saveBaseline(
    baseline: SigaaNotificationBaseline,
    nowEpochMillis: number,
  ): Promise<void> {
    await this.cacheStore.put<SigaaNotificationBaseline>({
      cacheKey: CacheKeys.SIGAA_NOTIF_BASELINE,
      domain: CacheDomains.TURMAS,
      origin: 'CACHE',
      syncedAtEpochMillis: nowEpochMillis,
      value: baseline,
    });
  }

  private async loadPrunedSentKeys(
    cacheKey: string,
    nowEpochMillis: number,
  ): Promise<Record<string, number>> {
    const raw =
      (await this.cacheStore.get<Record<string, number>>(cacheKey))?.value ?? {};
    const cutoff = nowEpochMillis - SENT_KEY_RETENTION_MILLIS;
    const pruned: Record<string, number> = {};
    for (const [k, ts] of Object.entries(raw)) {
      if (ts >= cutoff) {
        pruned[k] = ts;
      }
    }
    return pruned;
  }

  private async saveSentKeys(
    cacheKey: string,
    sentKeys: Record<string, number>,
    nowEpochMillis: number,
  ): Promise<void> {
    await this.cacheStore.put<Record<string, number>>({
      cacheKey,
      domain: cacheKey === RU_SENT_KEY_CACHE_KEY ? CacheDomains.META : CacheDomains.TURMAS,
      origin: 'CACHE',
      syncedAtEpochMillis: nowEpochMillis,
      value: sentKeys,
    });
  }
}

export { DEFAULT_SIGAA_NOTIFICACAO_CONFIG };
