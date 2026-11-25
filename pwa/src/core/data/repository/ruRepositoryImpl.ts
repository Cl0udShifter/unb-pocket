import {
  CAMPUS_RU_BY_ID,
  CAMPUS_RU_LIST,
  CoreResults,
  diaSemanaRuFromIso,
  isRuSlotEnabled,
  type Alergenico,
  type CampusRu,
  type CardapioSemanalRu,
  type CoreError,
  type CoreResult,
  type NoticiaRu,
  type ObservableValue,
  type RuAutoSyncSummary,
  type RuNotificacaoConfig,
  type RuNotificacaoConteudo,
  type RuPreferencias,
  type RuRepository,
  type RuSelecaoInicial,
  type SaldoExtratoRu,
  type SyncedData,
  type TabelaPrecosRu,
  type TipoRefeicao,
} from '../../model/models';
import type { UnbHttpTransport } from '../../network/unbHttpSession';
import { getBrasiliaParts } from '../../parser/brasiliaTime';
import { CasAndJsfParser } from '../../parser/casAndJsfParser';
import { DiscentePortalParser } from '../../parser/discentePortalParser';
import { RuCalculator } from '../../parser/ruCalculator';
import {
  RuMenuFreshness,
  ScheduleSelectionResolver,
} from '../../parser/ruMenuFreshness';
import { RuMenuPdfParser } from '../../parser/ruMenuPdfParser';
import { RuNotificationPlanner } from '../../parser/ruNotificationPlanner';
import {
  RuWpParser,
  type CampusPdfLink,
  type RuWpMenuPage,
} from '../../parser/ruWpParser';
import type { PdfExtractor } from '../../pdf/pdfExtractorClient';
import type { FileStore } from '../local/blobFileStore';
import {
  CacheDomains,
  CacheKeys,
  type RuPreferencesStore,
  type SnapshotCacheStore,
} from '../local/snapshotCacheStore';
import type {
  SigaaNavigator,
  SigaaSessionCoordinator,
} from '../session/sigaaSessionCoordinator';

interface RuAutoSyncMetaDto {
  lastCheckEpochMillis: number;
}

export class RuRepositoryImpl implements RuRepository {
  private readonly transport: UnbHttpTransport;
  private readonly coordinator: SigaaSessionCoordinator;
  private readonly cacheStore: SnapshotCacheStore;
  private readonly prefsStore: RuPreferencesStore;
  private readonly fileStore: FileStore;
  private readonly pdfExtractor: PdfExtractor;
  private readonly clockMillis: () => number;
  private readonly onRuNotificacoesChanged?: (config: RuNotificacaoConfig) => void;

  private syncChain: Promise<unknown> = Promise.resolve();

  constructor(params: {
    transport: UnbHttpTransport;
    coordinator: SigaaSessionCoordinator;
    cacheStore: SnapshotCacheStore;
    prefsStore: RuPreferencesStore;
    fileStore: FileStore;
    pdfExtractor: PdfExtractor;
    clockMillis?: () => number;
    onRuNotificacoesChanged?: (config: RuNotificacaoConfig) => void;
  }) {
    this.transport = params.transport;
    this.coordinator = params.coordinator;
    this.cacheStore = params.cacheStore;
    this.prefsStore = params.prefsStore;
    this.fileStore = params.fileStore;
    this.pdfExtractor = params.pdfExtractor;
    this.clockMillis = params.clockMillis ?? (() => Date.now());
    this.onRuNotificacoesChanged = params.onRuNotificacoesChanged;
  }

  private withSyncLock<T>(fn: () => Promise<T>): Promise<T> {
    const next = this.syncChain.then(fn, fn);
    this.syncChain = next.then(
      () => undefined,
      () => undefined,
    );
    return next;
  }

  observePreferencias(): ObservableValue<RuPreferencias> {
    return this.prefsStore.preferencias;
  }

  async setCampusSelecionado(campus: CampusRu): Promise<CoreResult<void>> {
    try {
      await this.prefsStore.setCampus(campus);
      return CoreResults.success(undefined);
    } catch (err) {
      return CoreResults.failure({
        kind: 'StorageError',
        operation: 'setCampusSelecionado',
        reason: err instanceof Error ? err.message : '',
      });
    }
  }

  async setAlergenicosRestritos(
    alergenicos: readonly Alergenico[],
    ocultarPratos: boolean,
  ): Promise<CoreResult<void>> {
    try {
      await this.prefsStore.setAlergenicos(alergenicos, ocultarPratos);
      return CoreResults.success(undefined);
    } catch (err) {
      return CoreResults.failure({
        kind: 'StorageError',
        operation: 'setAlergenicosRestritos',
        reason: err instanceof Error ? err.message : '',
      });
    }
  }

  async setNotificacoesConfig(config: RuNotificacaoConfig): Promise<CoreResult<void>> {
    try {
      await this.prefsStore.setNotificacoes(config);
      this.onRuNotificacoesChanged?.(config);
      return CoreResults.success(undefined);
    } catch (err) {
      return CoreResults.failure({
        kind: 'StorageError',
        operation: 'setNotificacoesConfig',
        reason: err instanceof Error ? err.message : '',
      });
    }
  }

  async prepareNotificacaoRefeicao(
    refeicao: TipoRefeicao,
    nowEpochMillis: number = this.clockMillis(),
  ): Promise<CoreResult<RuNotificacaoConteudo | null>> {
    try {
      const prefs = this.prefsStore.preferencias.getSnapshot();
      const parts = getBrasiliaParts(nowEpochMillis);
      const diaSemana = diaSemanaRuFromIso(parts.dayOfWeekIso);
      if (!diaSemana || !isRuSlotEnabled(prefs.notificacoes, diaSemana, refeicao)) {
        return CoreResults.success(null);
      }

      const campus = prefs.campusSelecionado;
      let cardapio = (await this.cacheStore.get<CardapioSemanalRu>(CacheKeys.cardapio(campus)))?.value ?? null;
      const needsRemote =
        !cardapio ||
        RuNotificationPlanner.findDayIndexForDate(cardapio, parts.isoDate) === null;
      if (needsRemote) {
        const refreshed = await this.refreshCardapio(campus, false);
        if (refreshed.ok) {
          cardapio = refreshed.value;
        }
      }

      return CoreResults.success(
        RuNotificationPlanner.buildNotificationContentOrFallback(
          cardapio,
          prefs,
          refeicao,
          nowEpochMillis,
        ),
      );
    } catch (err) {
      return CoreResults.failure({
        kind: 'StorageError',
        operation: 'prepareNotificacaoRefeicao',
        reason: err instanceof Error ? err.message : '',
      });
    }
  }

  resolveSelecaoInicial(
    cardapio: CardapioSemanalRu | null,
    nowEpochMillis?: number,
  ): RuSelecaoInicial {
    return ScheduleSelectionResolver.resolveRuInitialSelection(
      cardapio,
      nowEpochMillis ?? this.clockMillis(),
    );
  }

  observeCardapio(campus: CampusRu): ObservableValue<SyncedData<CardapioSemanalRu> | null> {
    return this.cacheStore.observe<CardapioSemanalRu>(CacheKeys.cardapio(campus));
  }

  async refreshCardapio(
    campus: CampusRu,
    force: boolean = false,
  ): Promise<CoreResult<CardapioSemanalRu>> {
    return this.withSyncLock(async () => {
      const now = this.clockMillis();
      const parsedPageRes = await this.fetchMenuPage(now);
      if (!parsedPageRes.ok) return parsedPageRes;

      const parsedPage = parsedPageRes.value;
      const campusLink = parsedPage.campusLinks[campus];
      if (!campusLink) {
        return CoreResults.failure({
          kind: 'ParseError',
          source: 'wp/v2/pages/12853',
          reason: `Cardapio nao encontrado para ${CAMPUS_RU_BY_ID[campus].displayName}.`,
        });
      }

      const cached = (await this.cacheStore.get<CardapioSemanalRu>(CacheKeys.cardapio(campus)))?.value ?? null;
      if (!force && isCacheUpToDate(cached, parsedPage.modifiedGmt, campusLink.pdfUrl)) {
        return CoreResults.success(cached);
      }

      const persisted = await this.fetchAndPersistCampusMenu(
        campus,
        parsedPage.modifiedGmt,
        campusLink,
      );
      if (!persisted.ok) return persisted;
      return CoreResults.success(persisted.value.menu);
    });
  }

  async syncCardapiosIfNeeded(
    forceCheck: boolean = false,
    includeExtras: boolean = false,
  ): Promise<CoreResult<RuAutoSyncSummary>> {
    return this.withSyncLock(async () => {
      const now = this.clockMillis();
      const cachedByCampus: Partial<Record<CampusRu, CardapioSemanalRu | null>> = {};
      for (const c of CAMPUS_RU_LIST) {
        cachedByCampus[c.id] =
          (await this.cacheStore.get<CardapioSemanalRu>(CacheKeys.cardapio(c.id)))?.value ?? null;
      }

      const meta = await this.cacheStore.get<RuAutoSyncMetaDto>(CacheKeys.RU_AUTO_SYNC_META);
      const lastCheck = meta?.value.lastCheckEpochMillis ?? null;

      const shouldCheck = RuMenuFreshness.shouldCheckRemote(
        cachedByCampus,
        lastCheck,
        now,
        forceCheck,
      );
      if (!shouldCheck) {
        const upToDate = CAMPUS_RU_LIST.map((c) => c.id).filter(
          (id) => cachedByCampus[id] !== null && cachedByCampus[id] !== undefined,
        );
        return CoreResults.success({
          checkedAtEpochMillis: lastCheck ?? now,
          skippedDueToCooldown: true,
          updatedCampi: [],
          upToDateCampi: upToDate,
          failedCampi: {},
          tabelaPrecosAtualizada: false,
          noticiasAtualizadas: false,
          anyUpdated: false,
        });
      }

      const pageRes = await this.fetchMenuPage(now);
      if (!pageRes.ok) return pageRes;
      const parsedPage = pageRes.value;

      await this.cacheStore.put<RuAutoSyncMetaDto>({
        cacheKey: CacheKeys.RU_AUTO_SYNC_META,
        domain: CacheDomains.META,
        origin: 'REMOTE',
        syncedAtEpochMillis: now,
        value: { lastCheckEpochMillis: now },
      });

      const updatedCampi: CampusRu[] = [];
      const upToDateCampi: CampusRu[] = [];
      const failedCampi: Partial<Record<CampusRu, CoreError>> = {};

      for (const c of CAMPUS_RU_LIST) {
        const campus = c.id;
        const campusLink = parsedPage.campusLinks[campus];
        if (!campusLink) {
          failedCampi[campus] = {
            kind: 'ParseError',
            source: 'wp/v2/pages/12853',
            reason: `Cardapio nao encontrado para ${c.displayName}.`,
          };
          continue;
        }

        const cached = cachedByCampus[campus] ?? null;
        if (isCacheUpToDate(cached, parsedPage.modifiedGmt, campusLink.pdfUrl)) {
          upToDateCampi.push(campus);
          continue;
        }

        const res = await this.fetchAndPersistCampusMenu(
          campus,
          parsedPage.modifiedGmt,
          campusLink,
        );
        if (!res.ok) {
          failedCampi[campus] = res.error;
        } else if (res.value.changed) {
          updatedCampi.push(campus);
        } else {
          upToDateCampi.push(campus);
        }
      }

      let tabelaPrecosAtualizada = false;
      let noticiasAtualizadas = false;
      if (includeExtras) {
        const pRes = await this.refreshTabelaPrecosWithDiff();
        tabelaPrecosAtualizada = pRes.ok && pRes.value.changed;
        const nRes = await this.refreshNoticiasWithDiff(10);
        noticiasAtualizadas = nRes.ok && nRes.value.changed;
      }

      return CoreResults.success({
        checkedAtEpochMillis: now,
        skippedDueToCooldown: false,
        updatedCampi,
        upToDateCampi,
        failedCampi,
        tabelaPrecosAtualizada,
        noticiasAtualizadas,
        anyUpdated:
          updatedCampi.length > 0 || tabelaPrecosAtualizada || noticiasAtualizadas,
      });
    });
  }

  observeSaldoExtrato(): ObservableValue<SyncedData<SaldoExtratoRu> | null> {
    return this.cacheStore.observe<SaldoExtratoRu>(CacheKeys.RU_SALDO);
  }

  async refreshSaldoExtrato(): Promise<CoreResult<SaldoExtratoRu>> {
    return this.coordinator.withAuthenticatedJsf('INTERACTIVE', async (nav) => {
      const home = await nav.getPortalHomeHtml();
      if (!home.ok) return home;
      const res = await this.refreshSaldoExtratoWithDiff(nav, home.value);
      if (!res.ok) return res;
      return CoreResults.success(res.value.saldo);
    });
  }

  async refreshSaldoExtratoWithDiff(
    nav: SigaaNavigator,
    homeHtml: string,
  ): Promise<CoreResult<{ saldo: SaldoExtratoRu; changed: boolean }>> {
    const extratoParams = CasAndJsfParser.extractExtratoRuFormParams(homeHtml);
    if (!extratoParams) {
      return CoreResults.failure({
        kind: 'UnavailableForProfile',
        feature: 'Saldo RU',
        reason: 'Formulario #formExibirExtrato nao disponivel na home do discente.',
      });
    }

    const payloadRes = await nav.postDiscenteForm(extratoParams);
    if (!payloadRes.ok) return payloadRes;

    const precos =
      (await this.cacheStore.get<TabelaPrecosRu>(CacheKeys.RU_PRECOS))?.value ??
      RuCalculator.defaultPrecosResolucao0037;
    const parsed = DiscentePortalParser.parseExtratoRu(
      payloadRes.value.decodeSigaaIso88591(),
      precos,
    );
    if (!parsed.ok) return parsed;

    const changed = await this.cacheStore.put<SaldoExtratoRu>({
      cacheKey: CacheKeys.RU_SALDO,
      domain: CacheDomains.RU,
      origin: 'REMOTE',
      syncedAtEpochMillis: nav.nowMillis(),
      value: parsed.value,
    });
    return CoreResults.success({ saldo: parsed.value, changed });
  }

  observeTabelaPrecos(): ObservableValue<SyncedData<TabelaPrecosRu> | null> {
    return this.cacheStore.observe<TabelaPrecosRu>(CacheKeys.RU_PRECOS, {
      value: RuCalculator.defaultPrecosResolucao0037,
      origin: 'FALLBACK',
      syncedAtEpochMillis: 0,
    });
  }

  async refreshTabelaPrecos(force: boolean = false): Promise<CoreResult<TabelaPrecosRu>> {
    if (!force) {
      const cached = await this.cacheStore.get<TabelaPrecosRu>(CacheKeys.RU_PRECOS);
      if (cached && cached.origin === 'REMOTE') {
        return CoreResults.success(cached.value);
      }
    }
    const res = await this.refreshTabelaPrecosWithDiff();
    if (!res.ok) return res;
    return CoreResults.success(res.value.tabela);
  }

  private async refreshTabelaPrecosWithDiff(): Promise<
    CoreResult<{ tabela: TabelaPrecosRu; changed: boolean }>
  > {
    const res = await this.transport.get(this.transport.endpoints.ruPrecosPageUrl);
    if (!res.ok) return res;
    const tabela = RuWpParser.parsePricesPage(res.value.decodeUtf8());
    const changed = await this.cacheStore.put<TabelaPrecosRu>({
      cacheKey: CacheKeys.RU_PRECOS,
      domain: CacheDomains.RU,
      origin: 'REMOTE',
      syncedAtEpochMillis: this.clockMillis(),
      value: tabela,
    });
    return CoreResults.success({ tabela, changed });
  }

  observeNoticias(): ObservableValue<SyncedData<readonly NoticiaRu[]> | null> {
    return this.cacheStore.observe<readonly NoticiaRu[]>(CacheKeys.RU_NOTICIAS);
  }

  async refreshNoticias(perPage: number = 10): Promise<CoreResult<readonly NoticiaRu[]>> {
    const res = await this.refreshNoticiasWithDiff(perPage);
    if (!res.ok) return res;
    return CoreResults.success(res.value.noticias);
  }

  private async refreshNoticiasWithDiff(
    perPage: number,
  ): Promise<CoreResult<{ noticias: readonly NoticiaRu[]; changed: boolean }>> {
    const res = await this.transport.get(this.transport.endpoints.ruNoticiasUrl(perPage));
    if (!res.ok) return res;
    const parsed = RuWpParser.parseNoticias(res.value.decodeUtf8());
    if (!parsed.ok) return parsed;
    const changed = await this.cacheStore.put<readonly NoticiaRu[]>({
      cacheKey: CacheKeys.RU_NOTICIAS,
      domain: CacheDomains.RU,
      origin: 'REMOTE',
      syncedAtEpochMillis: this.clockMillis(),
      value: parsed.value,
    });
    return CoreResults.success({ noticias: parsed.value, changed });
  }

  async markNoticiasVistas(ids: readonly number[]): Promise<CoreResult<void>> {
    try {
      await this.prefsStore.markNoticiasVistas(ids);
      return CoreResults.success(undefined);
    } catch (err) {
      return CoreResults.failure({
        kind: 'StorageError',
        operation: 'markNoticiasVistas',
        reason: err instanceof Error ? err.message : '',
      });
    }
  }

  async downloadCardapioPdfLocal(campus: CampusRu): Promise<CoreResult<string>> {
    const cachedPath =
      (await this.cacheStore.get<CardapioSemanalRu>(CacheKeys.cardapio(campus)))?.value
        .localPdfPath ?? null;
    if (cachedPath && (await this.fileStore.exists(cachedPath))) {
      return CoreResults.success(cachedPath);
    }
    const refreshed = await this.refreshCardapio(campus, true);
    if (!refreshed.ok) return refreshed;
    if (refreshed.value.localPdfPath) {
      return CoreResults.success(refreshed.value.localPdfPath);
    }
    return CoreResults.failure({
      kind: 'StorageError',
      operation: 'downloadCardapioPdfLocal',
      reason: 'PDF local indisponivel.',
    });
  }

  private async fetchMenuPage(nowEpochMillis: number): Promise<CoreResult<RuWpMenuPage>> {
    const pagePayload = await this.transport.get(this.transport.endpoints.ruCardapiosPageUrl);
    if (!pagePayload.ok) return pagePayload;
    const referenceIsoDate = RuMenuFreshness.localDateInBrasilia(nowEpochMillis);
    return RuWpParser.parseMenuPage(pagePayload.value.decodeUtf8(), referenceIsoDate);
  }

  private async fetchAndPersistCampusMenu(
    campus: CampusRu,
    modifiedGmt: string,
    campusLink: CampusPdfLink,
  ): Promise<CoreResult<{ menu: CardapioSemanalRu; changed: boolean }>> {
    const pdfPayload = await this.transport.get(campusLink.pdfUrl);
    if (!pdfPayload.ok) return pdfPayload;

    const slug = CAMPUS_RU_BY_ID[campus].slug;
    let localPdfPath: string | null = null;
    try {
      const saved = await this.fileStore.saveBytes(
        'pdfs',
        `cardapio_${slug}.pdf`,
        pdfPayload.value.bytes,
        'application/pdf',
      );
      localPdfPath = saved.path;
    } catch (err) {
      return CoreResults.failure({
        kind: 'StorageError',
        operation: 'saveCardapioPdf',
        reason: err instanceof Error ? err.message : '',
      });
    }

    const pagesRes = await this.pdfExtractor.extractRuMenuPages(pdfPayload.value.bytes);
    const parsedMenuResult = pagesRes.ok
      ? RuMenuPdfParser.parsePages({
          campus,
          semanaLabel: campusLink.semanaLabel,
          pdfUrl: campusLink.pdfUrl,
          fallbackImageUrl: null,
          localPdfPath,
          modifiedGmt,
          pages: pagesRes.value,
        })
      : pagesRes;

    const now = this.clockMillis();
    if (parsedMenuResult.ok) {
      const changed = await this.cacheStore.put<CardapioSemanalRu>({
        cacheKey: CacheKeys.cardapio(campus),
        domain: CacheDomains.RU,
        origin: 'REMOTE',
        syncedAtEpochMillis: now,
        value: parsedMenuResult.value,
      });
      return CoreResults.success({ menu: parsedMenuResult.value, changed });
    }

    const fallbackImageUrl = await this.findFallbackImageUrl(campusLink.pdfUrl, campus);
    const fallback: CardapioSemanalRu = {
      campus,
      semanaLabel: campusLink.semanaLabel,
      pdfUrl: campusLink.pdfUrl,
      fallbackImageUrl,
      localPdfPath,
      modifiedGmt,
      parserStatus: fallbackImageUrl ? 'FALLBACK_IMAGE_ONLY' : 'FALLBACK_PDF_ONLY',
      dias: [],
    };
    const changed = await this.cacheStore.put<CardapioSemanalRu>({
      cacheKey: CacheKeys.cardapio(campus),
      domain: CacheDomains.RU,
      origin: 'FALLBACK',
      syncedAtEpochMillis: now,
      value: fallback,
    });
    return CoreResults.success({ menu: fallback, changed });
  }

  private async findFallbackImageUrl(pdfUrl: string, campus: CampusRu): Promise<string | null> {
    const mediaRes = await this.transport.get(this.transport.endpoints.ruMediaPdfsUrl);
    if (!mediaRes.ok) return null;
    const parsed = RuWpParser.parseMediaList(mediaRes.value.decodeUtf8());
    if (!parsed.ok) return null;
    const keyword = CasAndJsfParser.normalizeLabel(CAMPUS_RU_BY_ID[campus].headingKeyword);
    const match =
      parsed.value.find((it) => it.sourceUrl === pdfUrl) ??
      parsed.value.find((it) => CasAndJsfParser.normalizeLabel(it.title).includes(keyword));
    return match?.fallbackFullImageUrl ?? null;
  }
}

function isCacheUpToDate(
  cached: CardapioSemanalRu | null,
  modifiedGmt: string,
  pdfUrl: string,
): cached is CardapioSemanalRu {
  return (
    cached !== null &&
    cached.modifiedGmt === modifiedGmt &&
    cached.pdfUrl === pdfUrl &&
    cached.parserStatus === 'PARSED_STRUCTURED' &&
    cached.dias.length > 0 &&
    cached.dias.length <= 7 &&
    cached.dias.every((d) => !d.dataTexto.startsWith('COL_'))
  );
}
