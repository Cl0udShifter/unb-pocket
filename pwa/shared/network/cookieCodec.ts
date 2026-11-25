export interface StoredCookie {
  name: string;
  value: string;
  domain: string;
  hostOnly: boolean;
  path: string;
  expiresAt: number | null;
  secure: boolean;
}

export function parseSetCookieHeader(
  rawHeader: string,
  requestUrl: URL,
  nowMillis: number = Date.now(),
): StoredCookie | null {
  const parts = rawHeader
    .split(';')
    .map((p) => p.trim())
    .filter((p) => p.length > 0);
  if (parts.length === 0) return null;

  const firstEq = parts[0].indexOf('=');
  if (firstEq <= 0) return null;

  const name = parts[0].slice(0, firstEq).trim();
  const value = parts[0].slice(firstEq + 1).trim();
  if (!name) return null;

  let domain = requestUrl.hostname.toLowerCase();
  let hostOnly = true;
  let path = defaultCookiePath(requestUrl.pathname);
  let expiresAt: number | null = null;
  let maxAgeSeen = false;
  let secure = false;

  for (let i = 1; i < parts.length; i += 1) {
    const attr = parts[i];
    const eq = attr.indexOf('=');
    const attrName = (eq >= 0 ? attr.slice(0, eq) : attr).trim().toLowerCase();
    const attrVal = eq >= 0 ? attr.slice(eq + 1).trim() : '';

    if (attrName === 'domain' && attrVal) {
      const cleaned = attrVal.replace(/^\.+/, '').toLowerCase();
      if (domainMatches(requestUrl.hostname.toLowerCase(), cleaned)) {
        domain = cleaned;
        hostOnly = false;
      }
    } else if (attrName === 'path' && attrVal.startsWith('/')) {
      path = attrVal;
    } else if (attrName === 'max-age' && attrVal) {
      const sec = Number.parseInt(attrVal, 10);
      if (!Number.isNaN(sec)) {
        maxAgeSeen = true;
        expiresAt = sec <= 0 ? 0 : nowMillis + sec * 1000;
      }
    } else if (attrName === 'expires' && attrVal && !maxAgeSeen) {
      const parsed = Date.parse(attrVal);
      if (!Number.isNaN(parsed)) {
        expiresAt = parsed;
      }
    } else if (attrName === 'secure') {
      secure = true;
    }
  }

  return {
    name,
    value,
    domain,
    hostOnly,
    path,
    expiresAt,
    secure,
  };
}

export function cookieMatchesUrl(
  cookie: StoredCookie,
  targetUrl: URL,
  nowMillis: number = Date.now(),
): boolean {
  if (cookie.expiresAt !== null && cookie.expiresAt <= nowMillis) {
    return false;
  }
  if (cookie.secure && targetUrl.protocol !== 'https:' && targetUrl.hostname !== 'localhost' && targetUrl.hostname !== '127.0.0.1') {
    return false;
  }
  const host = targetUrl.hostname.toLowerCase();
  if (cookie.hostOnly) {
    if (host !== cookie.domain) return false;
  } else if (!domainMatches(host, cookie.domain)) {
    return false;
  }

  return pathMatches(targetUrl.pathname || '/', cookie.path);
}

export function mergeCookies(
  existing: readonly StoredCookie[],
  incoming: readonly StoredCookie[],
  nowMillis: number = Date.now(),
): StoredCookie[] {
  const map = new Map<string, StoredCookie>();
  const keyOf = (c: StoredCookie) => `${c.domain}|${c.hostOnly ? '1' : '0'}|${c.path}|${c.name}`;

  for (const c of existing) {
    if (c.expiresAt === null || c.expiresAt > nowMillis) {
      map.set(keyOf(c), c);
    }
  }
  for (const c of incoming) {
    const key = keyOf(c);
    if (c.expiresAt !== null && c.expiresAt <= nowMillis) {
      map.delete(key);
    } else {
      map.set(key, c);
    }
  }
  return Array.from(map.values());
}

export function buildCookieHeader(
  cookies: readonly StoredCookie[],
  targetUrl: URL,
  nowMillis: number = Date.now(),
): string {
  return cookies
    .filter((c) => cookieMatchesUrl(c, targetUrl, nowMillis))
    .sort((a, b) => b.path.length - a.path.length)
    .map((c) => `${c.name}=${c.value}`)
    .join('; ');
}

function defaultCookiePath(pathname: string): string {
  if (!pathname || !pathname.startsWith('/')) return '/';
  const lastSlash = pathname.lastIndexOf('/');
  if (lastSlash <= 0) return '/';
  return pathname.slice(0, lastSlash);
}

function domainMatches(host: string, cookieDomain: string): boolean {
  if (host === cookieDomain) return true;
  return host.endsWith(`.${cookieDomain}`);
}

function pathMatches(requestPath: string, cookiePath: string): boolean {
  if (requestPath === cookiePath) return true;
  if (requestPath.startsWith(cookiePath)) {
    if (cookiePath.endsWith('/')) return true;
    if (requestPath.charAt(cookiePath.length) === '/') return true;
  }
  return false;
}
