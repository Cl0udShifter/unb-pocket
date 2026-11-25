import {
  CoreResults,
  type ArquivoBaixado,
  type AtualizacaoTurmaFeed,
  type AvaliacaoTurma,
  type CoreResult,
  type DiaSemanaSigaa,
  type MapaFrequenciaTurma,
  type MaterialAulaAva,
  type NotasParciaisTurma,
  type NoticiaTurmaAva,
  type ObservableValue,
  type SyncedData,
  type TarefaTurma,
  type TurmaDetalheAva,
  type TurmaResumo,
  type TurmasRepository,
} from '../../model/models';
import type { HttpPayload, JsfPriority } from '../../network/unbHttpSession';
import { CasAndJsfParser } from '../../parser/casAndJsfParser';
import { DiscentePortalParser } from '../../parser/discentePortalParser';
import { ScheduleSelectionResolver } from '../../parser/ruMenuFreshness';
import {
  TurmaVirtualParser,
  type AtestadoTurmaItem,
  type ParsedAvaFormTurmaItem,
  type ParsedAvaHome,
  type ParsedAvaNoticiaRef,
} from '../../parser/turmaVirtualParser';
import type { FileStore } from '../local/blobFileStore';
import {
  CacheDomains,
  CacheKeys,
  type SnapshotCacheStore,
} from '../local/snapshotCacheStore';
import type {
  SigaaNavigator,
  SigaaSessionCoordinator,
} from '../session/sigaaSessionCoordinator';

const ACTION_ATESTADO_MATRICULA =
  'menu_form_menu_discente_discente_menu:A]#{ portalDiscente.atestadoMatriculaUnb }';

export class TurmasRepositoryImpl implements TurmasRepository {
  private readonly coordinator: SigaaSessionCoordinator;
  private readonly cacheStore: SnapshotCacheStore;
  private readonly fileStore: FileStore;
  private readonly clockMillis: () => number;

  private readonly runtimeAvaCache = new Map<string, ParsedAvaHome>();
  private currentAvaHomeTurmaId: string | null = null;
  private currentAvaMenuTurmaId: string | null = null;
  private currentAvaMenuActions: Record<string, Record<string, string>> = {};

  constructor(params: {
    coordinator: SigaaSessionCoordinator;
    cacheStore: SnapshotCacheStore;
    fileStore: FileStore;
    clockMillis?: () => number;
  }) {
    this.coordinator = params.coordinator;
    this.cacheStore = params.cacheStore;
    this.fileStore = params.fileStore;
    this.clockMillis = params.clockMillis ?? (() => Date.now());
  }

  clearRuntimeState(): void {
    this.runtimeAvaCache.clear();
    this.currentAvaHomeTurmaId = null;
    this.currentAvaMenuTurmaId = null;
    this.currentAvaMenuActions = {};
  }

  resolveDiaAtualSigaa(nowEpochMillis?: number): DiaSemanaSigaa {
    return ScheduleSelectionResolver.resolveCurrentSigaaDay(
      nowEpochMillis ?? this.clockMillis(),
    );
  }

  observeTurmasSemestre(): ObservableValue<SyncedData<readonly TurmaResumo[]> | null> {
    return this.cacheStore.observe<readonly TurmaResumo[]>(CacheKeys.TURMAS_LIST);
  }

  observeAtualizacoesFeed(): ObservableValue<SyncedData<readonly AtualizacaoTurmaFeed[]> | null> {
    return this.cacheStore.observe<readonly AtualizacaoTurmaFeed[]>(CacheKeys.TURMAS_FEED);
  }

  async getCachedTurmasSnapshot(): Promise<readonly TurmaResumo[]> {
    return (
      (await this.cacheStore.get<readonly TurmaResumo[]>(CacheKeys.TURMAS_LIST))?.value ?? []
    );
  }

  async refreshTurmasSemestre(
    enriquecerViaAtestado: boolean = true,
  ): Promise<CoreResult<readonly TurmaResumo[]>> {
    return this.coordinator.withAuthenticatedJsf('INTERACTIVE', async (nav) => {
      const home = await nav.getPortalHomeHtml();
      if (!home.ok) return home;
      const res = await this.persistHomeTurmasAndFeedWithDiff(
        nav,
        home.value,
        enriquecerViaAtestado,
        true,
      );
      if (!res.ok) return res;
      return CoreResults.success(res.value.turmas);
    });
  }

  async persistHomeTurmasAndFeedWithDiff(
    nav: SigaaNavigator,
    homeHtml: string,
    enriquecerViaAtestado: boolean,
    reuseHomeHtmlForAtestado: boolean = false,
  ): Promise<CoreResult<{ turmas: readonly TurmaResumo[]; changed: boolean }>> {
    const now = nav.nowMillis();
    const feed = DiscentePortalParser.parseAtualizacoesFeed(homeHtml);
    const feedChanged = await this.cacheStore.put<readonly AtualizacaoTurmaFeed[]>({
      cacheKey: CacheKeys.TURMAS_FEED,
      domain: CacheDomains.TURMAS,
      origin: 'REMOTE',
      syncedAtEpochMillis: now,
      value: feed,
    });

    const homeTurmas = DiscentePortalParser.parseTurmasDaHome(homeHtml).map((it) => it.resumo);
    const existingList = await this.getCachedTurmasSnapshot();
    const existingByFrontEndId = new Map(existingList.map((t) => [t.frontEndIdTurma, t]));

    const needsAtestado =
      enriquecerViaAtestado &&
      homeTurmas.some((turma) => {
        const prev = existingByFrontEndId.get(turma.frontEndIdTurma);
        return !prev || !prev.codigoDisciplina?.trim() || !prev.docenteNome?.trim();
      });

    let atestadoItems: readonly AtestadoTurmaItem[] = [];
    if (needsAtestado) {
      const atestadoRes = await nav.postMenuDiscenteAction(
        ACTION_ATESTADO_MATRICULA,
        reuseHomeHtmlForAtestado ? homeHtml : null,
      );
      if (!atestadoRes.ok) {
        if (atestadoRes.error.kind === 'SessionExpired') return atestadoRes;
      } else {
        atestadoItems = TurmaVirtualParser.parseAtestadoMatricula(
          atestadoRes.value.decodeSigaaIso88591(),
        ).turmas;
      }
    }

    const merged: TurmaResumo[] = homeTurmas.map((turma) => {
      const prev = existingByFrontEndId.get(turma.frontEndIdTurma);
      const match = findAtestadoMatch(turma, atestadoItems);
      return {
        ...turma,
        codigoDisciplina: match?.codigoDisciplina ?? prev?.codigoDisciplina ?? null,
        numeroTurma: match?.numeroTurma ?? prev?.numeroTurma ?? null,
        cargaHorariaHoras: prev?.cargaHorariaHoras ?? null,
        docenteNome: match?.docenteNome ?? prev?.docenteNome ?? null,
      };
    });

    const turmasChanged = await this.cacheStore.put<readonly TurmaResumo[]>({
      cacheKey: CacheKeys.TURMAS_LIST,
      domain: CacheDomains.TURMAS,
      origin: 'REMOTE',
      syncedAtEpochMillis: now,
      value: merged,
    });

    return CoreResults.success({
      turmas: merged,
      changed: feedChanged || turmasChanged,
    });
  }

  observeTurmaDetalhe(frontEndIdTurma: string): ObservableValue<SyncedData<TurmaDetalheAva> | null> {
    return this.cacheStore.observe<TurmaDetalheAva>(CacheKeys.turmaDetalhe(frontEndIdTurma));
  }

  async refreshTurmaDetalhe(frontEndIdTurma: string): Promise<CoreResult<TurmaDetalheAva>> {
    const res = await this.refreshTurmaDetalheWithDiff(frontEndIdTurma, 'INTERACTIVE');
    if (!res.ok) return res;
    return CoreResults.success(res.value.detalhe);
  }

  async refreshTurmaDetalheWithDiff(
    frontEndIdTurma: string,
    priority: JsfPriority,
  ): Promise<CoreResult<{ detalhe: TurmaDetalheAva; changed: boolean }>> {
    return this.coordinator.withAuthenticatedJsf(priority, async (nav) => {
      const entered = await this.enterAvaHomeLocked(nav, frontEndIdTurma);
      if (!entered.ok) return entered;
      return CoreResults.success({
        detalhe: entered.value.avaHome.detalhe,
        changed: entered.value.changed,
      });
    });
  }

  async getNoticiaCompleta(
    frontEndIdTurma: string,
    idNoticia: string,
  ): Promise<CoreResult<NoticiaTurmaAva>> {
    const cached =
      (
        await this.cacheStore.get<NoticiaTurmaAva>(
          CacheKeys.turmaNoticia(frontEndIdTurma, idNoticia),
        )
      )?.value ?? null;

    if (cached) {
      const runtimeRefs = this.runtimeAvaCache.get(frontEndIdTurma)?.anexoRefs;
      const hasAllAnexoRefs =
        cached.anexos.length === 0 ||
        (runtimeRefs !== undefined && cached.anexos.every((a) => runtimeRefs[a.id] !== undefined));
      if (hasAllAnexoRefs) {
        return CoreResults.success(cached);
      }
    }

    const fetched = await this.coordinator.withAuthenticatedJsf('INTERACTIVE', async (nav) => {
      const directRef =
        this.currentAvaHomeTurmaId === frontEndIdTurma
          ? this.runtimeAvaCache.get(frontEndIdTurma)?.noticiaRefs[idNoticia]
          : undefined;

      if (directRef) {
        this.currentAvaHomeTurmaId = null;
        const directRes = await this.fetchAndCacheNoticia(
          nav,
          frontEndIdTurma,
          idNoticia,
          directRef,
        );
        if (directRes.ok) return directRes;
        if (
          directRes.error.kind === 'SessionExpired' ||
          directRes.error.kind === 'NetworkUnavailable'
        ) {
          return directRes;
        }
      }

      const entered = await this.enterAvaHomeLocked(nav, frontEndIdTurma);
      if (!entered.ok) return entered;

      const ref = entered.value.avaHome.noticiaRefs[idNoticia];
      if (!ref) {
        return CoreResults.failure({
          kind: 'UnavailableForProfile',
          feature: 'Noticia AVA',
          reason: `Noticia ${idNoticia} nao encontrada na turma.`,
        });
      }

      this.currentAvaHomeTurmaId = null;
      return this.fetchAndCacheNoticia(nav, frontEndIdTurma, idNoticia, ref);
    });

    if (fetched.ok) return fetched;
    if (cached && fetched.error.kind === 'NetworkUnavailable') {
      return CoreResults.success(cached);
    }
    return fetched;
  }

  private async fetchAndCacheNoticia(
    nav: SigaaNavigator,
    frontEndIdTurma: string,
    idNoticia: string,
    ref: ParsedAvaNoticiaRef,
  ): Promise<CoreResult<NoticiaTurmaAva>> {
    const payloadRes = await nav.postAvaForm(ref.formParams);
    if (!payloadRes.ok) return payloadRes;

    const parsed = TurmaVirtualParser.parseAvaNoticiaCompleta(
      payloadRes.value.decodeSigaaIso88591(),
      idNoticia,
    );
    if (!parsed.ok) return parsed;

    if (Object.keys(parsed.value.anexoRefs).length > 0) {
      const prev = this.runtimeAvaCache.get(frontEndIdTurma);
      if (prev) {
        this.runtimeAvaCache.set(frontEndIdTurma, {
          ...prev,
          anexoRefs: { ...prev.anexoRefs, ...parsed.value.anexoRefs },
        });
      }
    }

    await this.cacheStore.put<NoticiaTurmaAva>({
      cacheKey: CacheKeys.turmaNoticia(frontEndIdTurma, idNoticia),
      domain: CacheDomains.TURMAS,
      origin: 'REMOTE',
      syncedAtEpochMillis: nav.nowMillis(),
      value: parsed.value.noticia,
    });
    return CoreResults.success(parsed.value.noticia);
  }

  observeFrequencia(
    frontEndIdTurma: string,
  ): ObservableValue<SyncedData<MapaFrequenciaTurma> | null> {
    return this.cacheStore.observe<MapaFrequenciaTurma>(CacheKeys.turmaFrequencia(frontEndIdTurma));
  }

  async refreshFrequencia(frontEndIdTurma: string): Promise<CoreResult<MapaFrequenciaTurma>> {
    const res = await this.refreshFrequenciaWithDiff(frontEndIdTurma, 'INTERACTIVE');
    if (!res.ok) return res;
    return CoreResults.success(res.value.data);
  }

  async refreshFrequenciaWithDiff(
    frontEndIdTurma: string,
    priority: JsfPriority,
  ): Promise<CoreResult<{ data: MapaFrequenciaTurma; changed: boolean }>> {
    return this.fetchAndCacheAvaMenuWithDiff(
      frontEndIdTurma,
      'frequencia',
      CacheKeys.turmaFrequencia(frontEndIdTurma),
      priority,
      (html) => TurmaVirtualParser.parseAvaFrequencia(html),
    );
  }

  observeNotasParciais(
    frontEndIdTurma: string,
  ): ObservableValue<SyncedData<NotasParciaisTurma> | null> {
    return this.cacheStore.observe<NotasParciaisTurma>(CacheKeys.turmaNotas(frontEndIdTurma));
  }

  async refreshNotasParciais(frontEndIdTurma: string): Promise<CoreResult<NotasParciaisTurma>> {
    const res = await this.refreshNotasParciaisWithDiff(frontEndIdTurma, 'INTERACTIVE');
    if (!res.ok) return res;
    return CoreResults.success(res.value.data);
  }

  async refreshNotasParciaisWithDiff(
    frontEndIdTurma: string,
    priority: JsfPriority,
  ): Promise<CoreResult<{ data: NotasParciaisTurma; changed: boolean }>> {
    return this.fetchAndCacheAvaMenuWithDiff(
      frontEndIdTurma,
      'ver notas',
      CacheKeys.turmaNotas(frontEndIdTurma),
      priority,
      (html) => TurmaVirtualParser.parseAvaNotasParciais(html),
    );
  }

  observeAvaliacoes(
    frontEndIdTurma: string,
  ): ObservableValue<SyncedData<readonly AvaliacaoTurma[]> | null> {
    return this.cacheStore.observe<readonly AvaliacaoTurma[]>(
      CacheKeys.turmaAvaliacoes(frontEndIdTurma),
    );
  }

  async refreshAvaliacoes(
    frontEndIdTurma: string,
  ): Promise<CoreResult<readonly AvaliacaoTurma[]>> {
    const res = await this.refreshAvaliacoesWithDiff(frontEndIdTurma, 'INTERACTIVE');
    if (!res.ok) return res;
    return CoreResults.success(res.value.data);
  }

  async refreshAvaliacoesWithDiff(
    frontEndIdTurma: string,
    priority: JsfPriority,
  ): Promise<CoreResult<{ data: readonly AvaliacaoTurma[]; changed: boolean }>> {
    return this.fetchAndCacheAvaMenuWithDiff(
      frontEndIdTurma,
      'avaliacoes',
      CacheKeys.turmaAvaliacoes(frontEndIdTurma),
      priority,
      (html) => TurmaVirtualParser.parseAvaAvaliacoes(html),
    );
  }

  observeTarefas(
    frontEndIdTurma: string,
  ): ObservableValue<SyncedData<readonly TarefaTurma[]> | null> {
    return this.cacheStore.observe<readonly TarefaTurma[]>(
      CacheKeys.turmaTarefas(frontEndIdTurma),
    );
  }

  async refreshTarefas(frontEndIdTurma: string): Promise<CoreResult<readonly TarefaTurma[]>> {
    const res = await this.refreshTarefasWithDiff(frontEndIdTurma, 'INTERACTIVE');
    if (!res.ok) return res;
    return CoreResults.success(res.value.data);
  }

  async refreshTarefasWithDiff(
    frontEndIdTurma: string,
    priority: JsfPriority,
  ): Promise<CoreResult<{ data: readonly TarefaTurma[]; changed: boolean }>> {
    return this.fetchAndCacheAvaMenuWithDiff(
      frontEndIdTurma,
      'tarefas',
      CacheKeys.turmaTarefas(frontEndIdTurma),
      priority,
      (html) => TurmaVirtualParser.parseAvaTarefas(html),
    );
  }

  async downloadMaterial(
    frontEndIdTurma: string,
    materialId: string,
  ): Promise<CoreResult<ArquivoBaixado>> {
    return this.coordinator.withAuthenticatedJsf('INTERACTIVE', async (nav) => {
      const directRef =
        this.currentAvaHomeTurmaId === frontEndIdTurma
          ? this.runtimeAvaCache.get(frontEndIdTurma)?.materialRefs[materialId]
          : undefined;

      if (directRef) {
        const directRes = await nav.postAvaForm(directRef.formParams);
        if (directRes.ok) {
          if (!isUnexpectedHtmlAttachment(directRes.value, directRef.material.tipoExtensao)) {
            return this.saveMaterialPayload(
              frontEndIdTurma,
              directRef.material,
              directRes.value,
            );
          }
        } else if (
          directRes.error.kind === 'SessionExpired' ||
          directRes.error.kind === 'NetworkUnavailable'
        ) {
          this.currentAvaHomeTurmaId = null;
          return directRes;
        }
        this.currentAvaHomeTurmaId = null;
      }

      const entered = await this.enterAvaHomeLocked(nav, frontEndIdTurma);
      if (!entered.ok) return entered;

      const ref = entered.value.avaHome.materialRefs[materialId];
      if (!ref) {
        return CoreResults.failure({
          kind: 'UnavailableForProfile',
          feature: 'Material AVA',
          reason: `Material ${materialId} nao encontrado na turma.`,
        });
      }

      const payloadRes = await nav.postAvaForm(ref.formParams);
      if (!payloadRes.ok) return payloadRes;

      if (isUnexpectedHtmlAttachment(payloadRes.value, ref.material.tipoExtensao)) {
        this.currentAvaHomeTurmaId = null;
        return CoreResults.failure({
          kind: 'ParseError',
          source: 'ava/material',
          reason: `O SIGAA nao retornou o arquivo solicitado para ${ref.material.nome}.`,
        });
      }

      return this.saveMaterialPayload(frontEndIdTurma, ref.material, payloadRes.value);
    });
  }

  async downloadAnexoNoticia(
    frontEndIdTurma: string,
    anexoId: string,
  ): Promise<CoreResult<ArquivoBaixado>> {
    return this.coordinator.withAuthenticatedJsf('INTERACTIVE', async (nav) => {
      let ref = this.runtimeAvaCache.get(frontEndIdTurma)?.anexoRefs[anexoId];
      if (!ref) {
        const entered = await this.enterAvaHomeLocked(nav, frontEndIdTurma);
        if (!entered.ok) return entered;
        ref = entered.value.avaHome.anexoRefs[anexoId];
      }

      if (!ref) {
        return CoreResults.failure({
          kind: 'UnavailableForProfile',
          feature: 'Anexo AVA',
          reason: `Anexo ${anexoId} nao encontrado.`,
        });
      }

      let payloadRes: CoreResult<HttpPayload>;
      if (ref.downloadUrl) {
        payloadRes = await nav.getSigaaBinary(ref.downloadUrl);
      } else if (ref.formParams) {
        payloadRes = await nav.postAvaForm(ref.formParams);
      } else {
        return CoreResults.failure({
          kind: 'ParseError',
          source: 'ava/anexo',
          reason: 'Anexo sem URL ou formulario.',
        });
      }

      if (!payloadRes.ok) return payloadRes;
      return this.saveDownloadedPayload(
        frontEndIdTurma,
        ref.anexo.nomeArquivo,
        payloadRes.value,
      );
    });
  }

  private async fetchAndCacheAvaMenuWithDiff<T>(
    frontEndIdTurma: string,
    menuLabel: string,
    cacheKey: string,
    priority: JsfPriority,
    parser: (html: string) => CoreResult<T>,
  ): Promise<CoreResult<{ data: T; changed: boolean }>> {
    return this.coordinator.withAuthenticatedJsf(priority, async (nav) => {
      const directMenuParams =
        this.currentAvaMenuTurmaId === frontEndIdTurma
          ? this.currentAvaMenuActions[menuLabel]
          : undefined;

      if (directMenuParams) {
        this.currentAvaHomeTurmaId = null;
        const directRes = await nav.postAvaForm(directMenuParams);
        if (!directRes.ok) {
          this.currentAvaMenuTurmaId = null;
          this.currentAvaMenuActions = {};
          if (
            directRes.error.kind === 'SessionExpired' ||
            directRes.error.kind === 'NetworkUnavailable'
          ) {
            return directRes;
          }
        } else {
          const directHtml = directRes.value.decodeSigaaIso88591();
          const directParsed = parser(directHtml);
          if (directParsed.ok) {
            this.updateActiveAvaMenuState(frontEndIdTurma, directHtml);
            const menuChanged = await this.cacheStore.put<T>({
              cacheKey,
              domain: CacheDomains.TURMAS,
              origin: 'REMOTE',
              syncedAtEpochMillis: nav.nowMillis(),
              value: directParsed.value,
            });
            return CoreResults.success({ data: directParsed.value, changed: menuChanged });
          }
          this.currentAvaMenuTurmaId = null;
          this.currentAvaMenuActions = {};
        }
      }

      const entered = await this.enterAvaHomeLocked(nav, frontEndIdTurma);
      if (!entered.ok) return entered;

      const menuParams = entered.value.avaHome.menuActions[menuLabel];
      if (!menuParams) {
        return CoreResults.failure({
          kind: 'UnavailableForProfile',
          feature: 'Menu AVA',
          reason: `Opcao '${menuLabel}' indisponivel nesta turma.`,
        });
      }

      this.currentAvaHomeTurmaId = null;
      const menuPayload = await nav.postAvaForm(menuParams);
      if (!menuPayload.ok) {
        this.currentAvaMenuTurmaId = null;
        this.currentAvaMenuActions = {};
        return menuPayload;
      }

      const menuHtml = menuPayload.value.decodeSigaaIso88591();
      this.updateActiveAvaMenuState(frontEndIdTurma, menuHtml);

      const parsed = parser(menuHtml);
      if (!parsed.ok) return parsed;

      const menuChanged = await this.cacheStore.put<T>({
        cacheKey,
        domain: CacheDomains.TURMAS,
        origin: 'REMOTE',
        syncedAtEpochMillis: nav.nowMillis(),
        value: parsed.value,
      });

      return CoreResults.success({
        data: parsed.value,
        changed: entered.value.changed || menuChanged,
      });
    });
  }

  private updateActiveAvaMenuState(frontEndIdTurma: string, avaHtml: string): void {
    const nextActions = TurmaVirtualParser.parseAvaMenuActions(avaHtml);
    if (Object.keys(nextActions).length > 0) {
      this.currentAvaMenuTurmaId = frontEndIdTurma;
      this.currentAvaMenuActions = nextActions;
    } else {
      this.currentAvaMenuTurmaId = null;
      this.currentAvaMenuActions = {};
    }
  }

  private async enterAvaHomeLocked(
    nav: SigaaNavigator,
    frontEndIdTurma: string,
  ): Promise<CoreResult<{ avaHome: ParsedAvaHome; changed: boolean }>> {
    const home = await nav.getPortalHomeHtml();
    if (!home.ok) return home;

    const targetTurma = DiscentePortalParser.parseTurmasDaHome(home.value).find(
      (it) => it.resumo.frontEndIdTurma === frontEndIdTurma,
    );
    if (!targetTurma) {
      return CoreResults.failure({
        kind: 'UnavailableForProfile',
        feature: 'Turma Virtual',
        reason: `Turma ${frontEndIdTurma} nao encontrada no portal atual.`,
      });
    }

    const avaPayload = await nav.postDiscenteForm(targetTurma.formParams);
    if (!avaPayload.ok) return avaPayload;

    const parsedAva = TurmaVirtualParser.parseAvaHome(
      avaPayload.value.decodeSigaaIso88591(),
      frontEndIdTurma,
    );
    if (!parsedAva.ok) return parsedAva;

    this.runtimeAvaCache.set(frontEndIdTurma, parsedAva.value);
    this.currentAvaHomeTurmaId = frontEndIdTurma;
    this.currentAvaMenuTurmaId = frontEndIdTurma;
    this.currentAvaMenuActions = parsedAva.value.menuActions;

    const now = nav.nowMillis();
    const detailChanged = await this.cacheStore.put<TurmaDetalheAva>({
      cacheKey: CacheKeys.turmaDetalhe(frontEndIdTurma),
      domain: CacheDomains.TURMAS,
      origin: 'REMOTE',
      syncedAtEpochMillis: now,
      value: parsedAva.value.detalhe,
    });

    const listEnriched = await this.enrichTurmasListFromFormTurma(
      parsedAva.value.formTurmaItems,
      now,
    );

    return CoreResults.success({
      avaHome: parsedAva.value,
      changed: detailChanged || listEnriched,
    });
  }

  private async enrichTurmasListFromFormTurma(
    formItems: readonly ParsedAvaFormTurmaItem[],
    now: number,
  ): Promise<boolean> {
    if (formItems.length === 0) return false;
    const existing = await this.cacheStore.get<readonly TurmaResumo[]>(CacheKeys.TURMAS_LIST);
    if (!existing) return false;

    const byFrontEndId = new Map(formItems.map((it) => [it.frontEndIdTurma, it]));
    const updated = existing.value.map((turma) => {
      const item = byFrontEndId.get(turma.frontEndIdTurma);
      if (!item) return turma;
      return {
        ...turma,
        codigoDisciplina: turma.codigoDisciplina ?? item.codigoDisciplina,
        cargaHorariaHoras: item.cargaHorariaHoras ?? turma.cargaHorariaHoras,
      };
    });

    return this.cacheStore.put<readonly TurmaResumo[]>({
      cacheKey: CacheKeys.TURMAS_LIST,
      domain: CacheDomains.TURMAS,
      origin: 'REMOTE',
      syncedAtEpochMillis: now,
      value: updated,
    });
  }

  private async saveMaterialPayload(
    frontEndIdTurma: string,
    material: MaterialAulaAva,
    payload: HttpPayload,
  ): Promise<CoreResult<ArquivoBaixado>> {
    const rawExt = material.tipoExtensao.trim().toLowerCase();
    const validExt =
      rawExt &&
      !['indicator', 'file', 'spacer', 'loading', 'spinner', 'ajax', 'blank'].includes(rawExt)
        ? rawExt
        : payload.isPdf()
          ? 'pdf'
          : null;
    const fallbackName =
      validExt && !material.nome.toLowerCase().endsWith(`.${validExt}`)
        ? `${material.nome}.${validExt}`
        : material.nome;
    return this.saveDownloadedPayload(frontEndIdTurma, fallbackName, payload);
  }

  private async saveDownloadedPayload(
    frontEndIdTurma: string,
    fallbackName: string,
    payload: HttpPayload,
  ): Promise<CoreResult<ArquivoBaixado>> {
    const rawName = payload.extractAttachmentFileName(fallbackName);
    const fileName = !rawName.includes('.') && payload.isPdf() ? `${rawName}.pdf` : rawName;
    const mime = resolveMimeType(payload.contentType, fileName, payload.isPdf());

    try {
      const saved = await this.fileStore.saveBytes(
        `materiais/${frontEndIdTurma}`,
        fileName,
        payload.bytes,
        mime,
      );
      return CoreResults.success({
        nomeArquivo: saved.name,
        mimeType: mime,
        localFilePath: saved.path,
        tamanhoBytes: saved.size,
      });
    } catch (err) {
      return CoreResults.failure({
        kind: 'StorageError',
        operation: 'downloadArquivo',
        reason: err instanceof Error ? err.message : 'Erro ao salvar arquivo.',
      });
    }
  }
}

function isUnexpectedHtmlAttachment(payload: HttpPayload, expectedExt: string): boolean {
  const lowerExt = expectedExt.toLowerCase();
  if (lowerExt === 'html' || lowerExt === 'htm') return false;
  const isHtml = payload.contentType?.toLowerCase().includes('text/html') ?? false;
  if (!isHtml) return false;
  const disposition = (payload.contentDisposition ?? '').toLowerCase();
  return !disposition.includes('attachment') && !disposition.includes('filename');
}

function resolveMimeType(
  headerContentType: string | null,
  fileName: string,
  isPdf: boolean,
): string {
  const rawHeader = (headerContentType?.split(';')[0] ?? '').trim();
  if (
    rawHeader &&
    rawHeader.toLowerCase() !== 'application/octet-stream' &&
    rawHeader.toLowerCase() !== 'application/force-download'
  ) {
    return rawHeader;
  }
  if (isPdf) return 'application/pdf';
  const ext = fileName.includes('.') ? fileName.split('.').pop()?.toLowerCase() ?? '' : '';
  switch (ext) {
    case 'pdf':
      return 'application/pdf';
    case 'zip':
      return 'application/zip';
    case 'rar':
      return 'application/vnd.rar';
    case '7z':
      return 'application/x-7z-compressed';
    case 'doc':
      return 'application/msword';
    case 'docx':
      return 'application/vnd.openxmlformats-officedocument.wordprocessingml.document';
    case 'xls':
      return 'application/vnd.ms-excel';
    case 'xlsx':
      return 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
    case 'ppt':
      return 'application/vnd.ms-powerpoint';
    case 'pptx':
      return 'application/vnd.openxmlformats-officedocument.presentationml.presentation';
    case 'txt':
      return 'text/plain';
    case 'csv':
      return 'text/csv';
    case 'png':
      return 'image/png';
    case 'jpg':
    case 'jpeg':
      return 'image/jpeg';
    case 'mp4':
      return 'video/mp4';
    default:
      return rawHeader || 'application/octet-stream';
  }
}

function findAtestadoMatch(
  turma: TurmaResumo,
  atestadoItems: readonly AtestadoTurmaItem[],
): AtestadoTurmaItem | undefined {
  const normName = CasAndJsfParser.normalizeLabel(turma.nomeDisciplina);
  const rawCode = turma.codigoHorarioRaw.split('(')[0].trim().toLowerCase();
  return (
    atestadoItems.find(
      (item) =>
        CasAndJsfParser.normalizeLabel(item.nomeDisciplina) === normName &&
        (!rawCode || item.horarioRaw.toLowerCase().includes(rawCode)),
    ) ??
    atestadoItems.find((item) => CasAndJsfParser.normalizeLabel(item.nomeDisciplina) === normName)
  );
}
