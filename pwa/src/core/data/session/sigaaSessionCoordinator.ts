import {
  CoreResults,
  MutableStateValue,
  type AuthRepository,
  type AuthState,
  type CoreResult,
  type CredentialsInput,
  type ObservableValue,
} from '../../model/models';
import {
  HttpPayload,
  type JsfPriority,
  type NetworkEndpoints,
  type UnbHttpTransport,
} from '../../network/unbHttpSession';
import { CasAndJsfParser } from '../../parser/casAndJsfParser';
import type { FileStore } from '../local/blobFileStore';
import type { SnapshotCacheStore } from '../local/snapshotCacheStore';
import type { CredentialVault } from '../security/webCryptoVault';

const SESSION_TIMEOUT_MILLIS = 40 * 60 * 1000;
const SAFETY_MARGIN_MILLIS = 60 * 1000;
const FRESH_HTML_TTL_MILLIS = 30 * 1000;

export class SigaaNavigator {
  private readonly transport: UnbHttpTransport;
  private readonly clockMillis: () => number;
  private readonly onTouch: (idDiscente: string | null) => void;

  constructor(
    transport: UnbHttpTransport,
    clockMillis: () => number,
    onTouch: (idDiscente: string | null) => void,
  ) {
    this.transport = transport;
    this.clockMillis = clockMillis;
    this.onTouch = onTouch;
  }

  get endpoints(): NetworkEndpoints {
    return this.transport.endpoints;
  }

  nowMillis(): number {
    return this.clockMillis();
  }

  async getPortalHomeHtml(): Promise<CoreResult<string>> {
    const res = await this.transport.get(this.endpoints.verPortalDiscenteUrl);
    if (!res.ok) return res;
    return this.checkHtmlNotExpired(res.value);
  }

  async postDiscenteForm(params: Record<string, string>): Promise<CoreResult<HttpPayload>> {
    const res = await this.transport.postForm(this.endpoints.discentePortalUrl, params);
    if (!res.ok) return res;
    return this.checkPayloadNotExpired(res.value);
  }

  async postMenuDiscenteAction(
    jscookAction: string,
    existingHomeHtml: string | null = null,
  ): Promise<CoreResult<HttpPayload>> {
    let homeHtml = existingHomeHtml;
    if (homeHtml === null) {
      const home = await this.getPortalHomeHtml();
      if (!home.ok) return home;
      homeHtml = home.value;
    }

    const params = CasAndJsfParser.buildMenuDiscenteParams(homeHtml, jscookAction);
    if (!params) {
      return CoreResults.failure({
        kind: 'ParseError',
        source: 'discente.jsf',
        reason: 'Formulario #menu:form_menu_discente nao encontrado.',
      });
    }
    return this.postDiscenteForm(params);
  }

  async postAvaForm(params: Record<string, string>): Promise<CoreResult<HttpPayload>> {
    const res = await this.transport.postForm(this.endpoints.avaIndexUrl, params);
    if (!res.ok) return res;
    return this.checkPayloadNotExpired(res.value);
  }

  async getSigaaBinary(relativeOrAbsoluteUrl: string): Promise<CoreResult<HttpPayload>> {
    let targetUrl: string;
    if (/^https?:\/\//i.test(relativeOrAbsoluteUrl)) {
      targetUrl = relativeOrAbsoluteUrl;
    } else if (relativeOrAbsoluteUrl.startsWith('//')) {
      targetUrl = `https:${relativeOrAbsoluteUrl}`;
    } else {
      const base = this.endpoints.sigaaBaseUrl.replace(/\/+$/, '');
      const rel = relativeOrAbsoluteUrl.replace(/^\/+/, '');
      targetUrl = `${base}/${rel}`;
    }
    const res = await this.transport.get(targetUrl);
    if (!res.ok) return res;
    return this.checkPayloadNotExpired(res.value);
  }

  private checkHtmlNotExpired(payload: HttpPayload): CoreResult<string> {
    const html = payload.decodeSigaaIso88591();
    if (payload.finalUrl.includes('/sso-server/login') || CasAndJsfParser.isCasLoginPage(html)) {
      return CoreResults.failure({ kind: 'SessionExpired' });
    }
    const idDiscente = CasAndJsfParser.extractDiscenteId(html);
    this.onTouch(idDiscente);
    return CoreResults.success(html);
  }

  private checkPayloadNotExpired(payload: HttpPayload): CoreResult<HttpPayload> {
    if (payload.finalUrl.includes('/sso-server/login')) {
      return CoreResults.failure({ kind: 'SessionExpired' });
    }
    const isBinary =
      payload.isPdf() ||
      (payload.contentType?.toLowerCase().startsWith('image/') ?? false);
    if (!isBinary) {
      const html = payload.decodeSigaaIso88591();
      if (CasAndJsfParser.isCasLoginPage(html)) {
        return CoreResults.failure({ kind: 'SessionExpired' });
      }
      const idDiscente = CasAndJsfParser.extractDiscenteId(html);
      this.onTouch(idDiscente);
    } else {
      this.onTouch(null);
    }
    return CoreResults.success(payload);
  }
}

export class SigaaSessionCoordinator implements AuthRepository {
  private readonly transport: UnbHttpTransport;
  private readonly vault: CredentialVault;
  private readonly cacheStore: SnapshotCacheStore;
  private readonly fileStore: FileStore;
  private readonly clockMillis: () => number;
  private readonly onLogoutHook: (clearCachedData: boolean) => Promise<void>;

  private readonly state = new MutableStateValue<AuthState>({ kind: 'Unauthenticated' });
  private freshLoginPortalHtml: string | null = null;
  private freshLoginCapturedAtMillis = 0;

  constructor(params: {
    transport: UnbHttpTransport;
    vault: CredentialVault;
    cacheStore: SnapshotCacheStore;
    fileStore: FileStore;
    clockMillis?: () => number;
    onLogoutHook?: (clearCachedData: boolean) => Promise<void>;
  }) {
    this.transport = params.transport;
    this.vault = params.vault;
    this.cacheStore = params.cacheStore;
    this.fileStore = params.fileStore;
    this.clockMillis = params.clockMillis ?? (() => Date.now());
    this.onLogoutHook = params.onLogoutHook ?? (async () => undefined);
  }

  get authState(): ObservableValue<AuthState> {
    return this.state;
  }

  consumeFreshLoginPortalHtml(): string | null {
    const html = this.freshLoginPortalHtml;
    if (!html) return null;
    const capturedAt = this.freshLoginCapturedAtMillis;
    this.freshLoginPortalHtml = null;
    this.freshLoginCapturedAtMillis = 0;
    return this.clockMillis() - capturedAt <= FRESH_HTML_TTL_MILLIS ? html : null;
  }

  async login(
    credentials: CredentialsInput,
  ): Promise<CoreResult<Extract<AuthState, { kind: 'Authenticated' }>>> {
    return this.transport.session.withJsfLock('INTERACTIVE', () =>
      this.loginLocked(credentials),
    );
  }

  async ensureValidSession(): Promise<
    CoreResult<Extract<AuthState, { kind: 'Authenticated' }>>
  > {
    const current = this.state.value;
    const now = this.clockMillis();
    if (
      current.kind === 'Authenticated' &&
      current.sessionExpiresAtEpochMillis !== null &&
      now < current.sessionExpiresAtEpochMillis - SAFETY_MARGIN_MILLIS
    ) {
      return CoreResults.success(current);
    }

    return this.transport.session.withJsfLock('INTERACTIVE', () =>
      this.ensureValidSessionLocked(),
    );
  }

  async logout(clearCachedData: boolean = true): Promise<CoreResult<void>> {
    try {
      await this.onLogoutHook(clearCachedData);
      this.freshLoginPortalHtml = null;
      this.freshLoginCapturedAtMillis = 0;
      this.transport.session.cookieJar.clear();
      await this.vault.clear();
      if (clearCachedData) {
        await this.cacheStore.clearSigaaUserData();
        await this.fileStore.clearSigaaUserFiles();
      }
      this.state.value = { kind: 'Unauthenticated' };
      return CoreResults.success(undefined);
    } catch (err) {
      return CoreResults.failure({
        kind: 'StorageError',
        operation: 'logout',
        reason: err instanceof Error ? err.message : 'Falha ao limpar sessao',
      });
    }
  }

  async hasSavedCredentials(): Promise<boolean> {
    return this.vault.exists();
  }

  async withAuthenticatedJsf<T>(
    priority: JsfPriority,
    block: (nav: SigaaNavigator) => Promise<CoreResult<T>>,
  ): Promise<CoreResult<T>> {
    return this.transport.session.withJsfLock(priority, async () => {
      const sessionCheck = await this.ensureValidSessionLocked();
      if (!sessionCheck.ok) return sessionCheck;

      const navigator = new SigaaNavigator(
        this.transport,
        this.clockMillis,
        (id) => this.updateSessionTouch(id),
      );
      const firstAttempt = await block(navigator);
      if (!firstAttempt.ok && firstAttempt.error.kind === 'SessionExpired') {
        const saved = await this.vault.load();
        if (!saved) {
          this.state.value = { kind: 'Unauthenticated' };
          return firstAttempt;
        }
        const relogin = await this.loginLocked({
          matricula: saved.matricula,
          password: saved.password,
          rememberCredentials: true,
        });
        if (!relogin.ok) return relogin;
        return block(navigator);
      }
      return firstAttempt;
    });
  }

  private async ensureValidSessionLocked(): Promise<
    CoreResult<Extract<AuthState, { kind: 'Authenticated' }>>
  > {
    const current = this.state.value;
    const now = this.clockMillis();
    if (
      current.kind === 'Authenticated' &&
      current.sessionExpiresAtEpochMillis !== null &&
      now < current.sessionExpiresAtEpochMillis - SAFETY_MARGIN_MILLIS
    ) {
      return CoreResults.success(current);
    }

    const saved = await this.vault.load();
    if (!saved) {
      return CoreResults.failure(
        current.kind === 'Authenticated'
          ? { kind: 'SessionExpired' }
          : { kind: 'NoStoredCredentials' },
      );
    }

    return this.loginLocked({
      matricula: saved.matricula,
      password: saved.password,
      rememberCredentials: true,
    });
  }

  private async loginLocked(
    credentials: CredentialsInput,
  ): Promise<CoreResult<Extract<AuthState, { kind: 'Authenticated' }>>> {
    const cleanMatricula = credentials.matricula.trim();
    const remember = credentials.rememberCredentials !== false;
    if (!cleanMatricula || !credentials.password) {
      return CoreResults.failure({ kind: 'InvalidCredentials' });
    }

    this.state.value = { kind: 'Reauthenticating', matricula: cleanMatricula };
    this.transport.session.cookieJar.clear();

    const entryRes = await this.transport.get(this.transport.endpoints.discentePortalUrl);
    if (!entryRes.ok) {
      this.state.value = { kind: 'Unauthenticated' };
      return entryRes;
    }

    const entryPayload = entryRes.value;
    const entryHtml = entryPayload.decodeSigaaIso88591();
    let portalHtml: string;

    if (CasAndJsfParser.isCasLoginPage(entryHtml)) {
      const casForm = CasAndJsfParser.parseCasLoginForm(entryHtml);
      if (!casForm) {
        this.state.value = { kind: 'Unauthenticated' };
        return CoreResults.failure({
          kind: 'ParseError',
          source: 'cas/login',
          reason: 'Formulario de login CAS (#login-form) invalido.',
        });
      }

      const postUrl = entryPayload.resolveRelativeUrl(casForm.action);
      const loginRes = await this.transport.postForm(postUrl, {
        username: cleanMatricula,
        password: credentials.password,
        lt: casForm.lt,
        execution: casForm.execution,
        _eventId: casForm.eventId,
        submit: 'Submit',
      });

      if (!loginRes.ok) {
        this.state.value = { kind: 'Unauthenticated' };
        return loginRes;
      }

      const responseHtml = loginRes.value.decodeSigaaIso88591();
      if (
        CasAndJsfParser.isCasLoginPage(responseHtml) ||
        loginRes.value.finalUrl.includes('/sso-server/login')
      ) {
        this.state.value = { kind: 'Unauthenticated' };
        return CoreResults.failure({ kind: 'InvalidCredentials' });
      }
      portalHtml = responseHtml;
    } else {
      portalHtml = entryHtml;
    }

    const idDiscente = CasAndJsfParser.extractDiscenteId(portalHtml);
    if (remember) {
      await this.vault.save(cleanMatricula, credentials.password);
      // Request persistent storage on iOS/modern browsers after saving credentials
      if (typeof navigator !== 'undefined' && navigator.storage?.persist) {
        void navigator.storage.persist().catch(() => false);
      }
    } else {
      await this.vault.clear();
    }

    const now = this.clockMillis();
    const authenticated: Extract<AuthState, { kind: 'Authenticated' }> = {
      kind: 'Authenticated',
      matricula: cleanMatricula,
      idDiscente,
      sessionExpiresAtEpochMillis: now + SESSION_TIMEOUT_MILLIS,
      hasStoredCredentials: remember,
    };

    if (idDiscente) {
      this.freshLoginPortalHtml = portalHtml;
      this.freshLoginCapturedAtMillis = now;
    } else {
      this.freshLoginPortalHtml = null;
      this.freshLoginCapturedAtMillis = 0;
    }

    this.state.value = authenticated;
    return CoreResults.success(authenticated);
  }

  private updateSessionTouch(idDiscente: string | null): void {
    const current = this.state.value;
    if (current.kind === 'Authenticated') {
      this.state.value = {
        ...current,
        idDiscente: idDiscente ?? current.idDiscente,
        sessionExpiresAtEpochMillis: this.clockMillis() + SESSION_TIMEOUT_MILLIS,
      };
    }
  }
}
