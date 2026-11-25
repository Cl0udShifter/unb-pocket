import {
  CoreResults,
  MutableStateValue,
  TIPO_DOCUMENTO_BY_ID,
  type BoletimSemestre,
  type CarteirinhaEstudantil,
  type CoreResult,
  type DiscenteRepository,
  type DiscenteResumo,
  type DocumentoOficialEmitido,
  type ObservableValue,
  type PerfilDiscente,
  type SyncedData,
  type TipoDocumentoOficial,
} from '../../model/models';
import type { HttpPayload, JsfPriority } from '../../network/unbHttpSession';
import { DiscenteDocumentosParser } from '../../parser/discenteDocumentosParser';
import { DiscentePortalParser } from '../../parser/discentePortalParser';
import type { PdfExtractor } from '../../pdf/pdfExtractorClient';
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

const ACTION_RELATORIO_NOTAS =
  'menu_form_menu_discente_discente_menu:A]#{ relatorioNotasAluno.gerarRelatorio }';
const ACTION_CARTEIRINHA =
  'menu_form_menu_discente_discente_menu:A]#{ portalDiscente.emitiCarteiraEstudantilUnb }';
const ACTION_ATESTADO_MATRICULA =
  'menu_form_menu_discente_discente_menu:A]#{ portalDiscente.atestadoMatriculaUnb }';
const ACTION_DECLARACAO_VINCULO =
  'menu_form_menu_discente_discente_menu:A]#{ declaracaoVinculo.emitirDeclaracao}';
const ACTION_HISTORICO_ESCOLAR =
  'menu_form_menu_discente_discente_menu:A]#{ portalDiscente.historico }';
const ACTION_PASSE_LIVRE =
  'menu_form_menu_discente_discente_menu:A]#{ portalDiscente.passeEstudantilUnb }';

export class DiscenteRepositoryImpl implements DiscenteRepository {
  private readonly coordinator: SigaaSessionCoordinator;
  private readonly cacheStore: SnapshotCacheStore;
  private readonly fileStore: FileStore;
  private readonly pdfExtractor: PdfExtractor;

  private readonly docsListState = new MutableStateValue<readonly DocumentoOficialEmitido[]>([]);

  constructor(params: {
    coordinator: SigaaSessionCoordinator;
    cacheStore: SnapshotCacheStore;
    fileStore: FileStore;
    pdfExtractor: PdfExtractor;
  }) {
    this.coordinator = params.coordinator;
    this.cacheStore = params.cacheStore;
    this.fileStore = params.fileStore;
    this.pdfExtractor = params.pdfExtractor;

    const docsChannel = this.cacheStore.observe<readonly DocumentoOficialEmitido[]>(
      CacheKeys.DISCENTE_DOCUMENTOS,
    );
    this.docsListState.value = docsChannel.getSnapshot()?.value ?? [];
    docsChannel.subscribe((synced) => {
      this.docsListState.value = synced?.value ?? [];
    });
  }

  observeDiscenteResumo(): ObservableValue<SyncedData<DiscenteResumo> | null> {
    return this.cacheStore.observe<DiscenteResumo>(CacheKeys.DISCENTE_RESUMO);
  }

  async refreshDiscenteResumo(): Promise<CoreResult<DiscenteResumo>> {
    return this.coordinator.withAuthenticatedJsf('INTERACTIVE', async (nav) => {
      const home = await nav.getPortalHomeHtml();
      if (!home.ok) return home;
      const res = await this.persistResumoFromHomeHtmlWithDiff(nav, home.value, true);
      if (!res.ok) return res;
      return CoreResults.success(res.value.resumo);
    });
  }

  async persistResumoFromHomeHtmlWithDiff(
    nav: SigaaNavigator,
    homeHtml: string,
    forceDownloadFoto: boolean = false,
  ): Promise<CoreResult<{ resumo: DiscenteResumo; changed: boolean }>> {
    const parsed = DiscentePortalParser.parseDiscenteResumo(homeHtml);
    if (!parsed.ok) return parsed;

    const existingCached =
      (await this.cacheStore.get<DiscenteResumo>(CacheKeys.DISCENTE_RESUMO))?.value ?? null;
    const resolvedFotoPath = await this.resolveProfileFotoLocalPath(
      nav,
      parsed.value.perfil.fotoUrl,
      existingCached?.perfil ?? null,
      forceDownloadFoto,
    );

    const finalResumo: DiscenteResumo = {
      ...parsed.value,
      perfil: {
        ...parsed.value.perfil,
        fotoLocalPath: resolvedFotoPath,
      },
    };

    const changed = await this.cacheStore.put<DiscenteResumo>({
      cacheKey: CacheKeys.DISCENTE_RESUMO,
      domain: CacheDomains.DISCENTE,
      origin: 'REMOTE',
      syncedAtEpochMillis: nav.nowMillis(),
      value: finalResumo,
    });
    return CoreResults.success({ resumo: finalResumo, changed });
  }

  private async resolveProfileFotoLocalPath(
    nav: SigaaNavigator,
    fotoUrl: string | null,
    cachedPerfil: PerfilDiscente | null,
    forceDownload: boolean,
  ): Promise<string | null> {
    if (!fotoUrl || !fotoUrl.trim()) return null;

    let reusablePath: string | null = null;
    if (
      cachedPerfil &&
      cachedPerfil.fotoUrl === fotoUrl &&
      cachedPerfil.fotoLocalPath &&
      (await this.fileStore.exists(cachedPerfil.fotoLocalPath))
    ) {
      reusablePath = cachedPerfil.fotoLocalPath;
    }

    if (!forceDownload && reusablePath) {
      return reusablePath;
    }

    const payloadRes = await nav.getSigaaBinary(fotoUrl);
    if (!payloadRes.ok) return reusablePath;
    if (!isValidImagePayload(payloadRes.value)) return reusablePath;

    const ext = resolveImageExtension(payloadRes.value.contentType, fotoUrl);
    const hash = await shortSha256(payloadRes.value.bytes);
    const fileName = `perfil_foto_${hash}.${ext}`;
    const mime = `image/${ext === 'jpg' ? 'jpeg' : ext}`;

    try {
      const saved = await this.fileStore.saveBytes(
        'discente',
        fileName,
        payloadRes.value.bytes,
        mime,
      );
      if (reusablePath && reusablePath !== saved.path) {
        await this.fileStore.deleteFile(reusablePath).catch(() => undefined);
      }
      return saved.path;
    } catch {
      return reusablePath;
    }
  }

  observeCarteirinha(): ObservableValue<SyncedData<CarteirinhaEstudantil> | null> {
    return this.cacheStore.observe<CarteirinhaEstudantil>(CacheKeys.DISCENTE_CARTEIRINHA);
  }

  async refreshCarteirinha(force: boolean = false): Promise<CoreResult<CarteirinhaEstudantil>> {
    const res = await this.refreshCarteirinhaWithDiff(force, 'INTERACTIVE');
    if (!res.ok) return res;
    return CoreResults.success(res.value.carteirinha);
  }

  async refreshCarteirinhaWithDiff(
    force: boolean,
    priority: JsfPriority,
  ): Promise<CoreResult<{ carteirinha: CarteirinhaEstudantil; changed: boolean }>> {
    if (!force) {
      const cached =
        (await this.cacheStore.get<CarteirinhaEstudantil>(CacheKeys.DISCENTE_CARTEIRINHA))?.value ??
        null;
      if (
        cached &&
        (await this.fileStore.exists(cached.qrImageLocalPath)) &&
        (await this.fileStore.exists(cached.pdfLocalPath))
      ) {
        return CoreResults.success({ carteirinha: cached, changed: false });
      }
    }

    return this.coordinator.withAuthenticatedJsf(priority, (nav) =>
      this.emitAndSaveCarteirinhaLocked(nav),
    );
  }

  observeBoletimGeral(): ObservableValue<SyncedData<readonly BoletimSemestre[]> | null> {
    return this.cacheStore.observe<readonly BoletimSemestre[]>(CacheKeys.DISCENTE_BOLETIM);
  }

  async refreshBoletimGeral(): Promise<CoreResult<readonly BoletimSemestre[]>> {
    const res = await this.refreshBoletimGeralWithDiff('INTERACTIVE');
    if (!res.ok) return res;
    return CoreResults.success(res.value.boletim);
  }

  async refreshBoletimGeralWithDiff(
    priority: JsfPriority,
  ): Promise<CoreResult<{ boletim: readonly BoletimSemestre[]; changed: boolean }>> {
    return this.coordinator.withAuthenticatedJsf(priority, async (nav) => {
      const payloadRes = await nav.postMenuDiscenteAction(ACTION_RELATORIO_NOTAS);
      if (!payloadRes.ok) return payloadRes;

      const parsed = DiscenteDocumentosParser.parseBoletimGeral(
        payloadRes.value.decodeSigaaIso88591(),
      );
      if (!parsed.ok) return parsed;

      const changed = await this.cacheStore.put<readonly BoletimSemestre[]>({
        cacheKey: CacheKeys.DISCENTE_BOLETIM,
        domain: CacheDomains.DISCENTE,
        origin: 'REMOTE',
        syncedAtEpochMillis: nav.nowMillis(),
        value: parsed.value,
      });
      return CoreResults.success({ boletim: parsed.value, changed });
    });
  }

  observeDocumentosEmitidos(): ObservableValue<readonly DocumentoOficialEmitido[]> {
    return this.docsListState;
  }

  async emitirDocumentoOficial(
    tipo: TipoDocumentoOficial,
  ): Promise<CoreResult<DocumentoOficialEmitido>> {
    return this.coordinator.withAuthenticatedJsf('INTERACTIVE', async (nav) => {
      let emittedRes: CoreResult<DocumentoOficialEmitido>;
      switch (tipo) {
        case 'CARTEIRINHA_ESTUDANTIL': {
          const cRes = await this.emitAndSaveCarteirinhaLocked(nav);
          if (!cRes.ok) return cRes;
          const meta = TIPO_DOCUMENTO_BY_ID[tipo];
          emittedRes = CoreResults.success({
            tipo,
            titulo: meta.displayName,
            codigoVerificacao: cRes.value.carteirinha.qrPayload16Digitos,
            mimeType: meta.mimeType,
            localFilePath: cRes.value.carteirinha.pdfLocalPath,
            emitidoEmEpochMillis: nav.nowMillis(),
          });
          break;
        }
        case 'ATESTADO_MATRICULA':
          emittedRes = await this.emitTextOrPdfDocument(
            nav,
            tipo,
            ACTION_ATESTADO_MATRICULA,
            'atestado_matricula.html',
          );
          break;
        case 'DECLARACAO_VINCULO':
          emittedRes = await this.emitTextOrPdfDocument(
            nav,
            tipo,
            ACTION_DECLARACAO_VINCULO,
            'declaracao_vinculo.pdf',
          );
          break;
        case 'HISTORICO_ESCOLAR':
          emittedRes = await this.emitTextOrPdfDocument(
            nav,
            tipo,
            ACTION_HISTORICO_ESCOLAR,
            'historico_escolar.pdf',
          );
          break;
        case 'PASSE_LIVRE_ESTUDANTIL':
          emittedRes = await this.emitTextOrPdfDocument(
            nav,
            tipo,
            ACTION_PASSE_LIVRE,
            'passe_livre_estudantil.html',
          );
          break;
      }

      if (!emittedRes.ok) return emittedRes;
      const doc = emittedRes.value;

      const currentDocs =
        (
          await this.cacheStore.get<readonly DocumentoOficialEmitido[]>(
            CacheKeys.DISCENTE_DOCUMENTOS,
          )
        )?.value ?? [];
      const updatedDocs = [...currentDocs.filter((d) => d.tipo !== tipo), doc];
      await this.cacheStore.put<readonly DocumentoOficialEmitido[]>({
        cacheKey: CacheKeys.DISCENTE_DOCUMENTOS,
        domain: CacheDomains.DISCENTE,
        origin: 'REMOTE',
        syncedAtEpochMillis: nav.nowMillis(),
        value: updatedDocs,
      });

      return CoreResults.success(doc);
    });
  }

  private async emitAndSaveCarteirinhaLocked(
    nav: SigaaNavigator,
  ): Promise<CoreResult<{ carteirinha: CarteirinhaEstudantil; changed: boolean }>> {
    const payloadRes = await nav.postMenuDiscenteAction(ACTION_CARTEIRINHA);
    if (!payloadRes.ok) return payloadRes;

    if (!payloadRes.value.isPdf()) {
      return CoreResults.failure({
        kind: 'PdfExtractionError',
        documentType: 'Carteirinha Estudantil',
        reason: 'SIGAA nao retornou um PDF valido para a carteirinha.',
      });
    }

    const extracted = await this.pdfExtractor.extractCarteirinha(payloadRes.value.bytes);
    if (!extracted.ok) return extracted;

    const parsed = DiscenteDocumentosParser.parseCarteirinha(extracted.value);
    if (!parsed.ok) return parsed;

    try {
      const pdfFile = await this.fileStore.saveBytes(
        'carteirinha',
        'carteirinha.pdf',
        payloadRes.value.bytes,
        'application/pdf',
      );
      const qrFile = await this.fileStore.saveRgbAsPng(
        'carteirinha',
        'qr_img4.png',
        parsed.value.qrImage,
      );
      const fotoFile = parsed.value.foto3x4
        ? await this.fileStore.saveRgbAsPng('carteirinha', 'foto_3x4.png', parsed.value.foto3x4)
        : null;

      const carteirinha: CarteirinhaEstudantil = {
        nomeCompleto: parsed.value.nomeCompleto,
        matricula: parsed.value.matricula,
        cpfTexto: parsed.value.cpfTexto,
        curso: parsed.value.curso,
        nivel: parsed.value.nivel,
        validadeTexto: parsed.value.validadeTexto,
        qrPayload16Digitos: parsed.value.qrPayload16Digitos,
        qrImageLocalPath: qrFile.path,
        foto3x4LocalPath: fotoFile?.path ?? null,
        pdfLocalPath: pdfFile.path,
      };

      const changed = await this.cacheStore.put<CarteirinhaEstudantil>({
        cacheKey: CacheKeys.DISCENTE_CARTEIRINHA,
        domain: CacheDomains.DISCENTE,
        origin: 'REMOTE',
        syncedAtEpochMillis: nav.nowMillis(),
        value: carteirinha,
      });

      return CoreResults.success({ carteirinha, changed });
    } catch (err) {
      return CoreResults.failure({
        kind: 'StorageError',
        operation: 'saveCarteirinha',
        reason: err instanceof Error ? err.message : 'Falha ao gravar arquivos da carteirinha.',
      });
    }
  }

  private async emitTextOrPdfDocument(
    nav: SigaaNavigator,
    tipo: TipoDocumentoOficial,
    jscookAction: string,
    defaultFileName: string,
  ): Promise<CoreResult<DocumentoOficialEmitido>> {
    const meta = TIPO_DOCUMENTO_BY_ID[tipo];
    const payloadRes = await nav.postMenuDiscenteAction(jscookAction);
    if (!payloadRes.ok) return payloadRes;

    const payload = payloadRes.value;
    if (meta.mimeType === 'application/pdf' && !payload.isPdf()) {
      return CoreResults.failure({
        kind: 'PdfExtractionError',
        documentType: meta.displayName,
        reason: 'Resposta do SIGAA nao e um documento PDF valido.',
      });
    }

    let verificationCode: string | null = null;
    let bytesToSave: Uint8Array;
    let finalMimeType = meta.mimeType;

    if (payload.isPdf()) {
      const linesRes = await this.pdfExtractor.extractTextLines(payload.bytes, meta.displayName);
      if (linesRes.ok) {
        verificationCode = DiscenteDocumentosParser.extractVerificationCode(
          linesRes.value.join('\n'),
        );
      }
      bytesToSave = payload.bytes;
      finalMimeType = 'application/pdf';
    } else {
      const decodedHtml = payload.decodeSigaaIso88591();
      verificationCode = DiscenteDocumentosParser.extractVerificationCode(decodedHtml);
      const utf8Html = normalizeSigaaHtmlToUtf8(decodedHtml);
      bytesToSave = new TextEncoder().encode(utf8Html);
      finalMimeType = 'text/html;charset=utf-8';
    }

    const fileName = payload.extractAttachmentFileName(defaultFileName);
    try {
      const saved = await this.fileStore.saveBytes(
        'documentos',
        fileName,
        bytesToSave,
        finalMimeType,
      );
      return CoreResults.success({
        tipo,
        titulo: meta.displayName,
        codigoVerificacao: verificationCode,
        mimeType: meta.mimeType,
        localFilePath: saved.path,
        emitidoEmEpochMillis: nav.nowMillis(),
      });
    } catch (err) {
      return CoreResults.failure({
        kind: 'StorageError',
        operation: 'emitirDocumento',
        reason: err instanceof Error ? err.message : 'Erro ao salvar documento.',
      });
    }
  }
}

function isValidImagePayload(payload: HttpPayload): boolean {
  if (payload.bytes.length === 0 || payload.isPdf()) return false;
  const mime = (payload.contentType?.split(';')[0] ?? '').trim().toLowerCase();
  if (mime.includes('text/html') || mime.includes('application/json')) return false;
  return mime === '' || mime.startsWith('image/') || mime === 'application/octet-stream';
}

function resolveImageExtension(contentType: string | null, fotoUrl: string): string {
  const mime = (contentType?.split(';')[0] ?? '').trim().toLowerCase();
  if (mime === 'image/png') return 'png';
  if (mime === 'image/webp') return 'webp';
  if (mime === 'image/gif') return 'gif';
  if (mime === 'image/jpeg' || mime === 'image/jpg') return 'jpg';
  const urlExt = fotoUrl.split('?')[0].split('.').pop()?.toLowerCase() ?? '';
  return ['png', 'jpg', 'jpeg', 'webp', 'gif'].includes(urlExt) ? urlExt : 'jpg';
}

async function shortSha256(bytes: Uint8Array): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new Uint8Array(bytes));
  const arr = new Uint8Array(digest).slice(0, 6);
  return Array.from(arr)
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

function normalizeSigaaHtmlToUtf8(html: string): string {
  const replaced = html.replace(/charset\s*=\s*iso-8859-1/gi, 'charset=utf-8');
  if (/<meta[^>]+charset/i.test(replaced)) return replaced;
  if (/<head[^>]*>/i.test(replaced)) {
    return replaced.replace(/<head([^>]*)>/i, '<head$1><meta charset="utf-8" />');
  }
  return `<!doctype html><html lang="pt-BR"><head><meta charset="utf-8" /></head><body>${replaced}</body></html>`;
}
