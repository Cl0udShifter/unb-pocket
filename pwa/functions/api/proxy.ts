import {
  buildCookieHeader,
  mergeCookies,
  parseSetCookieHeader,
  type StoredCookie,
} from '../../shared/network/cookieCodec';
import {
  ReadOnlyViolationError,
  SigaaReadOnlyGuard,
} from '../../shared/network/sigaaReadOnlyGuard';

export interface ProxyEnvelopeRequest {
  url: string;
  method?: 'GET' | 'POST' | 'HEAD';
  formBody?: string | null;
  headers?: Record<string, string>;
  followRedirects?: boolean;
  cookies?: StoredCookie[];
}

export interface ProxyHandlerOptions {
  allowedHosts?: ReadonlySet<string>;
  fetchImpl?: typeof fetch;
  allowCrossOriginForTests?: boolean;
}

const MAX_REDIRECTS = 12;
const USER_AGENT = 'UnBPocket-PWA/1.0';

function encodeCookiesBase64(cookies: readonly StoredCookie[]): string {
  const json = JSON.stringify(cookies);
  const bytes = new TextEncoder().encode(json);
  let binary = '';
  for (let i = 0; i < bytes.length; i += 1) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

function extractSetCookies(headers: Headers): string[] {
  const maybeGetSetCookie = (headers as unknown as { getSetCookie?: () => string[] }).getSetCookie;
  if (typeof maybeGetSetCookie === 'function') {
    const list = maybeGetSetCookie.call(headers);
    if (Array.isArray(list) && list.length > 0) {
      return list;
    }
  }
  const single = headers.get('set-cookie');
  if (!single) return [];
  // Split folded Set-Cookie headers safely without breaking Expires=Wed, 21 Oct...
  return single.split(/,(?=\s*[!#$%&'*+\-.^_`|~0-9A-Za-z]+=)/g).map((s) => s.trim());
}

function isSameOriginRequest(request: Request): boolean {
  const secFetchSite = request.headers.get('sec-fetch-site');
  if (secFetchSite && secFetchSite !== 'same-origin' && secFetchSite !== 'none') {
    return false;
  }
  const origin = request.headers.get('origin');
  if (origin) {
    try {
      const reqUrl = new URL(request.url);
      const originUrl = new URL(origin);
      if (originUrl.host !== reqUrl.host) {
        return false;
      }
    } catch {
      return false;
    }
  }
  return true;
}

export async function handleProxyRequest(
  request: Request,
  options: ProxyHandlerOptions = {},
): Promise<Response> {
  if (request.method !== 'POST') {
    return jsonError(405, 'METHOD_NOT_ALLOWED', 'Use POST para /api/proxy.');
  }

  if (!options.allowCrossOriginForTests && !isSameOriginRequest(request)) {
    return jsonError(403, 'FORBIDDEN_ORIGIN', 'Requisicao cross-origin bloqueada.');
  }

  let payload: ProxyEnvelopeRequest;
  try {
    payload = (await request.json()) as ProxyEnvelopeRequest;
  } catch {
    return jsonError(400, 'BAD_JSON', 'Envelope JSON invalido.');
  }

  if (!payload || typeof payload.url !== 'string' || payload.url.trim().length === 0) {
    return jsonError(400, 'MISSING_URL', 'Campo url obrigatorio.');
  }

  const allowedHosts = options.allowedHosts ?? SigaaReadOnlyGuard.defaultAllowedHosts;
  const fetchFn = options.fetchImpl ?? fetch;
  const followRedirects = payload.followRedirects !== false;

  let currentUrl: URL;
  try {
    currentUrl = new URL(payload.url);
  } catch {
    return jsonError(400, 'INVALID_URL', 'URL alvo invalida.');
  }

  let currentMethod: string = (payload.method ?? 'GET').toUpperCase();
  let currentBody: string | null = currentMethod === 'POST' ? (payload.formBody ?? '') : null;
  let workingCookies: StoredCookie[] = Array.isArray(payload.cookies) ? [...payload.cookies] : [];

  try {
    for (let hop = 0; hop <= MAX_REDIRECTS; hop += 1) {
      const pathWithQuery = `${currentUrl.pathname}${currentUrl.search}`;
      SigaaReadOnlyGuard.validateRequestWithHosts(
        currentUrl.hostname,
        pathWithQuery,
        currentMethod,
        currentBody,
        allowedHosts,
      );

      const outboundHeaders = new Headers();
      outboundHeaders.set('User-Agent', USER_AGENT);
      outboundHeaders.set(
        'Accept',
        'text/html,application/xhtml+xml,application/xml;q=0.9,application/pdf,image/*,*/*;q=0.8',
      );
      outboundHeaders.set('Accept-Language', 'pt-BR,pt;q=0.9,en-US;q=0.7');

      if (payload.headers) {
        for (const [k, v] of Object.entries(payload.headers)) {
          const lower = k.toLowerCase();
          if (lower !== 'host' && lower !== 'cookie' && lower !== 'content-length') {
            outboundHeaders.set(k, v);
          }
        }
      }

      const cookieHeader = buildCookieHeader(workingCookies, currentUrl);
      if (cookieHeader.length > 0) {
        outboundHeaders.set('Cookie', cookieHeader);
      }

      let bodyPayload: string | undefined;
      if (currentMethod === 'POST' && currentBody !== null) {
        outboundHeaders.set('Content-Type', 'application/x-www-form-urlencoded; charset=ISO-8859-1');
        bodyPayload = currentBody;
      }

      const upstream = await fetchFn(currentUrl.toString(), {
        method: currentMethod,
        headers: outboundHeaders,
        body: bodyPayload,
        redirect: 'manual',
      });

      const rawSetCookies = extractSetCookies(upstream.headers);
      if (rawSetCookies.length > 0) {
        const parsed = rawSetCookies
          .map((raw) => parseSetCookieHeader(raw, currentUrl))
          .filter((c): c is StoredCookie => c !== null);
        workingCookies = mergeCookies(workingCookies, parsed);
      }

      const isRedirect =
        upstream.status === 301 ||
        upstream.status === 302 ||
        upstream.status === 303 ||
        upstream.status === 307 ||
        upstream.status === 308;

      const location = upstream.headers.get('location');
      if (followRedirects && isRedirect && location) {
        if (hop === MAX_REDIRECTS) {
          return jsonError(508, 'TOO_MANY_REDIRECTS', 'Limite de redirecionamentos excedido.');
        }
        const nextUrl = new URL(location, currentUrl);
        if (
          upstream.status === 301 ||
          upstream.status === 302 ||
          upstream.status === 303
        ) {
          currentMethod = 'GET';
          currentBody = null;
        }
        currentUrl = nextUrl;
        continue;
      }

      const responseHeaders = new Headers();
      const upstreamContentType = upstream.headers.get('content-type');
      if (upstreamContentType) {
        responseHeaders.set('Content-Type', upstreamContentType);
      } else {
        responseHeaders.set('Content-Type', 'application/octet-stream');
      }

      const upstreamDisposition = upstream.headers.get('content-disposition');
      if (upstreamDisposition) {
        responseHeaders.set('X-Unb-Content-Disposition', upstreamDisposition);
      }

      responseHeaders.set('X-Unb-Final-Url', currentUrl.toString());
      responseHeaders.set('X-Unb-Status', String(upstream.status));
      responseHeaders.set('X-Unb-Cookies', encodeCookiesBase64(workingCookies));
      responseHeaders.set('Cache-Control', 'no-store, no-cache, must-revalidate');

      return new Response(upstream.body, {
        status: 200,
        headers: responseHeaders,
      });
    }
  } catch (err) {
    if (err instanceof ReadOnlyViolationError) {
      return Response.json(
        {
          error: 'READ_ONLY_VIOLATION',
          blockedTarget: err.blockedTarget,
          reason: err.reason,
        },
        {
          status: 403,
          headers: { 'Cache-Control': 'no-store' },
        },
      );
    }
    return Response.json(
      {
        error: 'NETWORK_UNAVAILABLE',
        message: err instanceof Error ? err.message : 'Falha de conexao com o portal da UnB.',
      },
      {
        status: 502,
        headers: { 'Cache-Control': 'no-store' },
      },
    );
  }

  return jsonError(500, 'UNEXPECTED', 'Fluxo do proxy encerrado inesperadamente.');
}

function jsonError(status: number, code: string, message: string): Response {
  return Response.json(
    { error: code, message },
    {
      status,
      headers: { 'Cache-Control': 'no-store' },
    },
  );
}

export const onRequestPost = async (context: { request: Request }): Promise<Response> =>
  handleProxyRequest(context.request);
