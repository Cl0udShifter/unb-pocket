import {
  mergeCookies,
  type StoredCookie,
} from '../../../shared/network/cookieCodec';
import {
  ReadOnlyViolationError,
  SigaaReadOnlyGuard,
} from '../../../shared/network/sigaaReadOnlyGuard';
import { CoreResults, type CoreResult } from '../model/models';

export type JsfPriority = 'INTERACTIVE' | 'BACKGROUND_PRELOAD';

const SESSION_COOKIE_STORAGE_KEY = 'unb_pocket_session_cookies_v1';

export class ClientCookieJar {
  private cookies: StoredCookie[] = [];

  constructor() {
    this.restoreFromSessionStorage();
  }

  getCookies(nowMillis: number = Date.now()): StoredCookie[] {
    this.cookies = this.cookies.filter((c) => c.expiresAt === null || c.expiresAt > nowMillis);
    return [...this.cookies];
  }

  replaceFromProxy(cookies: readonly StoredCookie[], nowMillis: number = Date.now()): void {
    this.cookies = mergeCookies([], cookies, nowMillis);
    this.persistToSessionStorage();
  }

  clear(): void {
    this.cookies = [];
    try {
      if (typeof sessionStorage !== 'undefined') {
        sessionStorage.removeItem(SESSION_COOKIE_STORAGE_KEY);
      }
    } catch {
      // Ignore storage restrictions
    }
  }

  private restoreFromSessionStorage(): void {
    try {
      if (typeof sessionStorage === 'undefined') return;
      const raw = sessionStorage.getItem(SESSION_COOKIE_STORAGE_KEY);
      if (!raw) return;
      const parsed = JSON.parse(raw) as StoredCookie[];
      if (Array.isArray(parsed)) {
        this.cookies = mergeCookies([], parsed, Date.now());
      }
    } catch {
      this.cookies = [];
    }
  }

  private persistToSessionStorage(): void {
    try {
      if (typeof sessionStorage === 'undefined') return;
      sessionStorage.setItem(SESSION_COOKIE_STORAGE_KEY, JSON.stringify(this.cookies));
    } catch {
      // Ignore storage restrictions
    }
  }
}

/**
 * Serializa chamadas JSF que dependem do estado sequencial `javax.faces.ViewState`
 * com preempcao por prioridade para interacoes do usuario sobre pre-carga em segundo plano.
 */
export class UnbHttpSession {
  readonly cookieJar: ClientCookieJar;
  readonly allowedHosts: ReadonlySet<string>;

  private mutexLocked = false;
  private readonly mutexWaiters: Array<() => void> = [];
  private activeInteractiveCount = 0;
  private readonly interactiveZeroWaiters: Array<() => void> = [];

  constructor(
    cookieJar: ClientCookieJar = new ClientCookieJar(),
    allowedHosts: ReadonlySet<string> = SigaaReadOnlyGuard.defaultAllowedHosts,
  ) {
    this.cookieJar = cookieJar;
    this.allowedHosts = allowedHosts;
  }

  async withJsfLock<T>(
    priority: JsfPriority,
    block: () => Promise<T>,
  ): Promise<T> {
    if (priority === 'INTERACTIVE') {
      this.activeInteractiveCount += 1;
      try {
        return await this.runInMutex(block);
      } finally {
        this.activeInteractiveCount = Math.max(0, this.activeInteractiveCount - 1);
        if (this.activeInteractiveCount === 0) {
          const waiters = this.interactiveZeroWaiters.splice(0);
          for (const wake of waiters) wake();
        }
      }
    }

    while (true) {
      await this.waitForZeroInteractive();
      let executed = false;
      let result!: T;

      await this.runInMutex(async () => {
        if (this.activeInteractiveCount > 0) {
          return;
        }
        executed = true;
        result = await block();
      });

      if (executed) {
        return result;
      }
    }
  }

  private async runInMutex<T>(fn: () => Promise<T>): Promise<T> {
    if (this.mutexLocked) {
      await new Promise<void>((resolve) => {
        this.mutexWaiters.push(resolve);
      });
    } else {
      this.mutexLocked = true;
    }

    try {
      return await fn();
    } finally {
      const next = this.mutexWaiters.shift();
      if (next) {
        next();
      } else {
        this.mutexLocked = false;
      }
    }
  }

  private waitForZeroInteractive(): Promise<void> {
    if (this.activeInteractiveCount === 0) {
      return Promise.resolve();
    }
    return new Promise<void>((resolve) => {
      this.interactiveZeroWaiters.push(resolve);
    });
  }

  static decodeSigaaBytes(bytes: Uint8Array): string {
    return new TextDecoder('iso-8859-1').decode(bytes);
  }
}

export class NetworkEndpoints {
  readonly sigaaBaseUrl: string;
  readonly casBaseUrl: string;
  readonly sigBaseUrl: string;
  readonly ruBaseUrl: string;
  readonly proxyEndpoint: string;

  constructor(init?: Partial<{
    sigaaBaseUrl: string;
    casBaseUrl: string;
    sigBaseUrl: string;
    ruBaseUrl: string;
    proxyEndpoint: string;
  }>) {
    this.sigaaBaseUrl = init?.sigaaBaseUrl ?? 'https://sigaa.unb.br';
    this.casBaseUrl = init?.casBaseUrl ?? 'https://autenticacao.unb.br';
    this.sigBaseUrl = init?.sigBaseUrl ?? 'https://sig.unb.br';
    this.ruBaseUrl = init?.ruBaseUrl ?? 'https://ru.unb.br';
    this.proxyEndpoint = init?.proxyEndpoint ?? '/api/proxy';
  }

  get discentePortalUrl(): string {
    return `${this.sigaaBaseUrl}/sigaa/portais/discente/discente.jsf`;
  }

  get verPortalDiscenteUrl(): string {
    return `${this.sigaaBaseUrl}/sigaa/verPortalDiscente.do`;
  }

  get avaIndexUrl(): string {
    return `${this.sigaaBaseUrl}/sigaa/ava/index.jsf`;
  }

  get turmasAnterioresUrl(): string {
    return `${this.sigaaBaseUrl}/sigaa/portais/discente/turmas.jsf`;
  }

  get ruCardapiosPageUrl(): string {
    return `${this.ruBaseUrl}/wp-json/wp/v2/pages/12853?_fields=id,modified_gmt,content`;
  }

  get ruMediaPdfsUrl(): string {
    return `${this.ruBaseUrl}/wp-json/wp/v2/media?per_page=15&mime_type=application/pdf`;
  }

  ruNoticiasUrl(perPage: number): string {
    return `${this.ruBaseUrl}/wp-json/wp/v2/posts?per_page=${perPage}&_embed`;
  }

  get ruPrecosPageUrl(): string {
    return `${this.ruBaseUrl}/wp-json/wp/v2/pages?slug=precos-e-grupos&_fields=id,modified_gmt,content`;
  }
}

export class HttpPayload {
  readonly finalUrl: string;
  readonly statusCode: number;
  readonly contentType: string | null;
  readonly contentDisposition: string | null;
  readonly bytes: Uint8Array;

  constructor(params: {
    finalUrl: string;
    statusCode: number;
    contentType: string | null;
    contentDisposition: string | null;
    bytes: Uint8Array;
  }) {
    this.finalUrl = params.finalUrl;
    this.statusCode = params.statusCode;
    this.contentType = params.contentType;
    this.contentDisposition = params.contentDisposition;
    this.bytes = params.bytes;
  }

  decodeSigaaIso88591(): string {
    return UnbHttpSession.decodeSigaaBytes(this.bytes);
  }

  decodeUtf8(): string {
    return new TextDecoder('utf-8').decode(this.bytes);
  }

  isPdf(): boolean {
    if (this.contentType?.toLowerCase().includes('application/pdf')) {
      return true;
    }
    return (
      this.bytes.length >= 4 &&
      this.bytes[0] === 0x25 && // %
      this.bytes[1] === 0x50 && // P
      this.bytes[2] === 0x44 && // D
      this.bytes[3] === 0x46 // F
    );
  }

  resolveRelativeUrl(relativeOrAbsolute: string): string {
    try {
      return new URL(relativeOrAbsolute, this.finalUrl).toString();
    } catch {
      return relativeOrAbsolute;
    }
  }

  extractAttachmentFileName(fallbackName: string): string {
    const disposition = this.contentDisposition ?? '';
    const utf8Match = /filename\*=UTF-8''([^;\r\n]+)/i.exec(disposition);
    if (utf8Match) {
      const raw = utf8Match[1].replace(/^["\s]+|["\s]+$/g, '');
      try {
        return decodeURIComponent(raw);
      } catch {
        return raw;
      }
    }
    const stdMatch = /filename="?([^";\r\n]+)"?/i.exec(disposition);
    if (stdMatch) {
      return stdMatch[1].replace(/^["\s]+|["\s]+$/g, '');
    }
    return fallbackName;
  }
}

export function encodeFormIso88591(fields: Record<string, string>): string {
  const encodeToken = (str: string): string => {
    let out = '';
    for (let i = 0; i < str.length; i += 1) {
      const code = str.charCodeAt(i);
      if (code === 0x20) {
        out += '+';
      } else if (
        (code >= 0x30 && code <= 0x39) ||
        (code >= 0x41 && code <= 0x5a) ||
        (code >= 0x61 && code <= 0x7a) ||
        code === 0x2d ||
        code === 0x5f ||
        code === 0x2e ||
        code === 0x2a
      ) {
        out += str.charAt(i);
      } else if (code <= 0xff) {
        out += `%${code.toString(16).toUpperCase().padStart(2, '0')}`;
      } else {
        const utf8 = encodeURIComponent(str.charAt(i));
        out += utf8;
      }
    }
    return out;
  };

  return Object.entries(fields)
    .map(([k, v]) => `${encodeToken(k)}=${encodeToken(v)}`)
    .join('&');
}

function decodeCookiesBase64(encoded: string | null): StoredCookie[] | null {
  if (!encoded) return null;
  try {
    const binary = atob(encoded);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i += 1) {
      bytes[i] = binary.charCodeAt(i);
    }
    const json = new TextDecoder('utf-8').decode(bytes);
    const parsed = JSON.parse(json) as StoredCookie[];
    return Array.isArray(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

export class UnbHttpTransport {
  readonly session: UnbHttpSession;
  readonly endpoints: NetworkEndpoints;
  private readonly fetchImpl: typeof fetch;
  private readonly forceProxyAll: boolean;

  constructor(options?: {
    session?: UnbHttpSession;
    endpoints?: NetworkEndpoints;
    fetchImpl?: typeof fetch;
    forceProxyAll?: boolean;
  }) {
    this.session = options?.session ?? new UnbHttpSession();
    this.endpoints = options?.endpoints ?? new NetworkEndpoints();
    this.fetchImpl = options?.fetchImpl ?? ((input, init) => fetch(input, init));
    this.forceProxyAll = options?.forceProxyAll ?? false;
  }

  async get(
    url: string,
    headers: Record<string, string> = {},
  ): Promise<CoreResult<HttpPayload>> {
    return this.executeRequest(url, 'GET', null, headers);
  }

  async postForm(
    url: string,
    fields: Record<string, string>,
  ): Promise<CoreResult<HttpPayload>> {
    const formBody = encodeFormIso88591(fields);
    return this.executeRequest(url, 'POST', formBody, {});
  }

  private canFetchDirectlyInBrowser(parsedUrl: URL, method: 'GET' | 'POST'): boolean {
    if (this.forceProxyAll || method !== 'GET') return false;
    const host = parsedUrl.hostname.toLowerCase();
    // WordPress REST API v2 endpoints on ru.unb.br send Access-Control-Allow-Origin: *
    if (host === 'ru.unb.br' && parsedUrl.pathname.startsWith('/wp-json/')) {
      return true;
    }
    return false;
  }

  private async executeRequest(
    targetUrl: string,
    method: 'GET' | 'POST',
    formBody: string | null,
    headers: Record<string, string>,
  ): Promise<CoreResult<HttpPayload>> {
    let parsedUrl: URL;
    try {
      parsedUrl = new URL(targetUrl);
    } catch (err) {
      return CoreResults.failure({
        kind: 'HttpFailure',
        statusCode: 0,
        endpoint: targetUrl,
        message: err instanceof Error ? err.message : 'URL invalida',
      });
    }

    try {
      SigaaReadOnlyGuard.validateRequestWithHosts(
        parsedUrl.hostname,
        `${parsedUrl.pathname}${parsedUrl.search}`,
        method,
        formBody,
        this.session.allowedHosts,
      );
    } catch (err) {
      if (err instanceof ReadOnlyViolationError) {
        return CoreResults.failure({
          kind: 'ReadOnlyViolation',
          blockedTarget: err.blockedTarget,
          reason: err.reason,
        });
      }
      throw err;
    }

    if (this.canFetchDirectlyInBrowser(parsedUrl, method)) {
      try {
        const directRes = await this.fetchImpl(targetUrl, {
          method: 'GET',
          headers,
        });
        if (directRes.ok) {
          const buf = new Uint8Array(await directRes.arrayBuffer());
          return CoreResults.success(
            new HttpPayload({
              finalUrl: directRes.url || targetUrl,
              statusCode: directRes.status,
              contentType: directRes.headers.get('content-type'),
              contentDisposition: directRes.headers.get('content-disposition'),
              bytes: buf,
            }),
          );
        }
        return CoreResults.failure({
          kind: 'HttpFailure',
          statusCode: directRes.status,
          endpoint: targetUrl,
          message: directRes.statusText || null,
        });
      } catch {
        // Fallback to stateless proxy if browser CORS or network policy blocks direct fetch
      }
    }

    try {
      const proxyRes = await this.fetchImpl(this.endpoints.proxyEndpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          url: targetUrl,
          method,
          formBody,
          headers,
          followRedirects: true,
          cookies: this.session.cookieJar.getCookies(),
        }),
      });

      if (proxyRes.status === 403) {
        const errJson = (await proxyRes.json().catch(() => null)) as {
          error?: string;
          blockedTarget?: string;
          reason?: string;
        } | null;
        if (errJson?.error === 'READ_ONLY_VIOLATION') {
          return CoreResults.failure({
            kind: 'ReadOnlyViolation',
            blockedTarget: errJson.blockedTarget ?? targetUrl,
            reason: errJson.reason ?? 'Bloqueado pelo proxy Read-Only',
          });
        }
      }

      if (proxyRes.status === 502 || proxyRes.status === 504) {
        return CoreResults.failure({ kind: 'NetworkUnavailable' });
      }

      if (!proxyRes.ok) {
        return CoreResults.failure({
          kind: 'HttpFailure',
          statusCode: proxyRes.status,
          endpoint: targetUrl,
          message: proxyRes.statusText || null,
        });
      }

      const updatedCookies = decodeCookiesBase64(proxyRes.headers.get('X-Unb-Cookies'));
      if (updatedCookies) {
        this.session.cookieJar.replaceFromProxy(updatedCookies);
      }

      const upstreamStatus = Number.parseInt(
        proxyRes.headers.get('X-Unb-Status') ?? String(proxyRes.status),
        10,
      );
      const finalUrl = proxyRes.headers.get('X-Unb-Final-Url') ?? targetUrl;
      const bytes = new Uint8Array(await proxyRes.arrayBuffer());

      if (upstreamStatus < 200 || upstreamStatus >= 300) {
        return CoreResults.failure({
          kind: 'HttpFailure',
          statusCode: upstreamStatus,
          endpoint: targetUrl,
          message: null,
        });
      }

      return CoreResults.success(
        new HttpPayload({
          finalUrl,
          statusCode: upstreamStatus,
          contentType: proxyRes.headers.get('Content-Type'),
          contentDisposition: proxyRes.headers.get('X-Unb-Content-Disposition'),
          bytes,
        }),
      );
    } catch {
      return CoreResults.failure({ kind: 'NetworkUnavailable' });
    }
  }
}
