import { describe, expect, it } from 'vitest';
import {
  ReadOnlyViolationError,
  SigaaReadOnlyGuard,
} from '../../shared/network/sigaaReadOnlyGuard';
import { handleProxyRequest } from '../../functions/api/proxy';

describe('SigaaReadOnlyGuard', () => {
  it('allows read-only GETs and whitelisted POSTs including CAS password containing Confirmar', () => {
    expect(() =>
      SigaaReadOnlyGuard.validateRequest('ru.unb.br', '/wp-json/wp/v2/pages/12853', 'GET', null),
    ).not.toThrow();

    expect(() =>
      SigaaReadOnlyGuard.validateRequest(
        'api.github.com',
        '/repos/Cl0udShifter/unb-pocket/releases/latest',
        'GET',
        null,
      ),
    ).not.toThrow();

    expect(() =>
      SigaaReadOnlyGuard.validateRequest(
        'sigaa.unb.br',
        '/sigaa/portais/discente/discente.jsf',
        'POST',
        'formExibirExtrato=formExibirExtrato&javax.faces.ViewState=j_id1',
      ),
    ).not.toThrow();

    expect(() =>
      SigaaReadOnlyGuard.validateRequest(
        'autenticacao.unb.br',
        '/sso-server/login;jsessionid=ABC?service=https%3A%2F%2Fsig.unb.br',
        'POST',
        'username=190026308&password=Confirmar123&lt=LT-1&execution=e1s1&_eventId=submit&submit=Submit',
      ),
    ).not.toThrow();
  });

  it('blocks mutating payloads, forbidden tokens, non-whitelisted paths, and unauthorized hosts', () => {
    for (const token of SigaaReadOnlyGuard.forbiddenSigaaTokens) {
      expect(() =>
        SigaaReadOnlyGuard.validateRequest(
          'sigaa.unb.br',
          '/sigaa/portais/discente/discente.jsf',
          'POST',
          `jscook_action=${token}&javax.faces.ViewState=j_id1`,
        ),
      ).toThrow(ReadOnlyViolationError);
    }

    expect(() =>
      SigaaReadOnlyGuard.validateRequest(
        'sigaa.unb.br',
        '/sigaa/graduacao/matricula/painel.jsf',
        'POST',
        'confirmar=1',
      ),
    ).toThrow(ReadOnlyViolationError);

    expect(() =>
      SigaaReadOnlyGuard.validateRequest('example.com', '/api', 'GET', null),
    ).toThrow(ReadOnlyViolationError);

    expect(() =>
      SigaaReadOnlyGuard.validateRequest(
        'sigaa.unb.br',
        '/sigaa/portais/discente/discente.jsf',
        'DELETE',
        null,
      ),
    ).toThrow(ReadOnlyViolationError);

    expect(() =>
      SigaaReadOnlyGuard.validateRequest(
        'ru.unb.br',
        '/wp-json/wp/v2/posts',
        'POST',
        'title=test',
      ),
    ).toThrow(ReadOnlyViolationError);
  });
});

describe('Stateless Proxy Handler', () => {
  it('blocks cross-origin abuse and mutating SIGAA payloads at the edge', async () => {
    const crossOriginReq = new Request('https://unbpocket.pages.dev/api/proxy', {
      method: 'POST',
      headers: {
        Origin: 'https://evil.example.com',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ url: 'https://sigaa.unb.br/sigaa/verPortalDiscente.do' }),
    });
    const crossRes = await handleProxyRequest(crossOriginReq);
    expect(crossRes.status).toBe(403);

    const mutatingReq = new Request('https://unbpocket.pages.dev/api/proxy', {
      method: 'POST',
      headers: {
        Origin: 'https://unbpocket.pages.dev',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        url: 'https://sigaa.unb.br/sigaa/portais/discente/discente.jsf',
        method: 'POST',
        formBody: 'jscook_action=trancamentoMatricula',
      }),
    });
    const mutatingRes = await handleProxyRequest(mutatingReq);
    expect(mutatingRes.status).toBe(403);
    const body = (await mutatingRes.json()) as { error: string };
    expect(body.error).toBe('READ_ONLY_VIOLATION');
  });

  it('follows CAS -> SIG -> SIGAA redirect hops, forwards cookies in memory, and preserves ISO-8859-1 bytes', async () => {
    const seenRequests: Array<{ url: string; method: string; cookie: string | null }> = [];

    const mockFetch: typeof fetch = async (input, init) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url;
      const headers = new Headers(init?.headers);
      seenRequests.push({
        url,
        method: init?.method ?? 'GET',
        cookie: headers.get('Cookie'),
      });

      if (url.startsWith('https://autenticacao.unb.br/sso-server/login')) {
        return new Response(null, {
          status: 302,
          headers: {
            Location: 'https://sig.unb.br/sigaa/login/cas?ticket=ST-123',
            'Set-Cookie': 'CASTGC=TGT-1; Path=/sso-server; Secure; HttpOnly',
          },
        });
      }
      if (url.startsWith('https://sig.unb.br/sigaa/login/cas')) {
        return new Response(null, {
          status: 301,
          headers: {
            Location: 'https://sigaa.unb.br/sigaa/login/cas?ticket=ST-123',
          },
        });
      }
      if (url.startsWith('https://sigaa.unb.br/sigaa/login/cas')) {
        return new Response(null, {
          status: 302,
          headers: {
            Location: 'https://sigaa.unb.br/sigaa/portais/discente/discente.jsf',
            'Set-Cookie': 'JSESSIONID=SIGAA-SESS-99; Path=/sigaa; HttpOnly',
          },
        });
      }

      // Return ISO-8859-1 bytes for "Ação e Média"
      const latin1 = new Uint8Array([0x41, 0xe7, 0xe3, 0x6f, 0x20, 0x65, 0x20, 0x4d, 0xe9, 0x64, 0x69, 0x61]);
      return new Response(latin1, {
        status: 200,
        headers: {
          'Content-Type': 'text/html;charset=ISO-8859-1',
        },
      });
    };

    const req = new Request('https://unbpocket.pages.dev/api/proxy', {
      method: 'POST',
      headers: {
        Origin: 'https://unbpocket.pages.dev',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        url: 'https://autenticacao.unb.br/sso-server/login?service=https%3A%2F%2Fsig.unb.br%2Fsigaa%2Flogin%2Fcas',
        method: 'POST',
        formBody: 'username=190026308&password=sec&lt=LT-1&execution=e1s1&_eventId=submit&submit=Submit',
      }),
    });

    const res = await handleProxyRequest(req, { fetchImpl: mockFetch });
    expect(res.status).toBe(200);
    expect(res.headers.get('X-Unb-Status')).toBe('200');
    expect(res.headers.get('X-Unb-Final-Url')).toBe('https://sigaa.unb.br/sigaa/portais/discente/discente.jsf');

    expect(seenRequests).toHaveLength(4);
    expect(seenRequests[0].method).toBe('POST');
    expect(seenRequests[1].method).toBe('GET');
    expect(seenRequests[3].cookie).toContain('JSESSIONID=SIGAA-SESS-99');

    const rawBytes = new Uint8Array(await res.arrayBuffer());
    const decoded = new TextDecoder('iso-8859-1').decode(rawBytes);
    expect(decoded).toBe('Ação e Média');
  });
});
