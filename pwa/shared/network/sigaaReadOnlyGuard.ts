/**
 * Excecao lancada antes da transmissao HTTP quando uma requisicao viola
 * o contrato estrito de somente leitura ou tenta acessar hosts externos.
 */
export class ReadOnlyViolationError extends Error {
  readonly blockedTarget: string;
  readonly reason: string;

  constructor(blockedTarget: string, reason: string) {
    super(`Bloqueio Read-Only (${blockedTarget}): ${reason}`);
    this.name = 'ReadOnlyViolationError';
    this.blockedTarget = blockedTarget;
    this.reason = reason;
  }
}

function safeDecodeURIComponent(raw: string): string {
  const normalized = raw.replace(/\+/g, ' ');
  try {
    return decodeURIComponent(normalized);
  } catch {
    // Fallback Latin-1 percent decoding for SIGAA ISO-8859-1 form payloads
    return normalized.replace(/%([0-9A-Fa-f]{2})/g, (_, hex: string) =>
      String.fromCharCode(parseInt(hex, 16)),
    );
  }
}

/**
 * Guard-rail programatico compartilhado entre cliente PWA e proxy stateless
 * para garantir que o UnB Pocket execute apenas requisicoes de leitura e
 * navegacao/emissao mapeadas no contrato oficial.
 */
export const SigaaReadOnlyGuard = {
  defaultAllowedHosts: new Set<string>([
    'sigaa.unb.br',
    'autenticacao.unb.br',
    'sig.unb.br',
    'arquivos.unb.br',
    'ru.unb.br',
    'api.github.com',
    'github.com',
    'objects.githubusercontent.com',
    'release-assets.githubusercontent.com',
  ]),

  getOnlyHosts: new Set<string>([
    'ru.unb.br',
    'api.github.com',
    'github.com',
    'objects.githubusercontent.com',
    'release-assets.githubusercontent.com',
  ]),

  allowedPostPaths: new Set<string>([
    '/sso-server/login',
    '/sigaa/portais/discente/discente.jsf',
    '/sigaa/ava/index.jsf',
    '/cxpostal/caixa_postal.jsf',
  ]),

  allowedCasLoginKeys: new Set<string>([
    'username',
    'password',
    'lt',
    'execution',
    '_eventId',
    'submit',
  ]),

  forbiddenSigaaTokens: [
    'retiradaMatricula',
    'trancamentoMatricula',
    'trancamentoPrograma',
    'novoTopicoForum',
    'btEnviarEmailNee',
    'dados_discente',
    'alterarSenha',
    'excluirMensagem',
    'enviarMensagem',
    'Confirmar',
  ] as const,

  validateRequest(
    host: string,
    path: string,
    method: string,
    formEncodedBody?: string | null,
  ): void {
    this.validateRequestWithHosts(
      host,
      path,
      method,
      formEncodedBody,
      this.defaultAllowedHosts,
    );
  },

  validateRequestWithHosts(
    host: string,
    path: string,
    method: string,
    formEncodedBody: string | null | undefined,
    allowedHosts: ReadonlySet<string>,
  ): void {
    const normalizedHost = host.toLowerCase();
    if (!allowedHosts.has(normalizedHost)) {
      throw new ReadOnlyViolationError(
        `${normalizedHost}${path}`,
        'Host nao autorizado pelo contrato do UnB Pocket.',
      );
    }

    const upperMethod = method.toUpperCase();
    if (upperMethod === 'GET' || upperMethod === 'HEAD') {
      return;
    }
    if (upperMethod === 'POST') {
      this.validatePost(normalizedHost, path, formEncodedBody);
      return;
    }

    throw new ReadOnlyViolationError(
      `${upperMethod} ${normalizedHost}${path}`,
      `Metodo HTTP ${upperMethod} nao permitido no modo somente leitura.`,
    );
  },

  validatePost(
    host: string,
    path: string,
    formEncodedBody?: string | null,
  ): void {
    const cleanPath = path.split(';')[0].split('?')[0];
    if (this.getOnlyHosts.has(host) || !this.allowedPostPaths.has(cleanPath)) {
      throw new ReadOnlyViolationError(
        `POST ${host}${cleanPath}`,
        'Endpoint POST fora da whitelist de navegacao/emissao read-only.',
      );
    }

    const rawBody = formEncodedBody ?? '';
    if (cleanPath === '/sso-server/login') {
      const keys = rawBody
        .split('&')
        .filter((part) => part.trim().length > 0)
        .map((param) => {
          const eqIdx = param.indexOf('=');
          const rawKey = eqIdx >= 0 ? param.slice(0, eqIdx) : param;
          return safeDecodeURIComponent(rawKey);
        });
      const unexpectedKeys = keys.filter((k) => !this.allowedCasLoginKeys.has(k));
      if (unexpectedKeys.length > 0) {
        throw new ReadOnlyViolationError(
          `POST ${host}${cleanPath}`,
          `Campos nao permitidos no login CAS: ${unexpectedKeys.join(', ')}`,
        );
      }
      return;
    }

    const decodedBody = safeDecodeURIComponent(rawBody);
    for (const token of this.forbiddenSigaaTokens) {
      if (decodedBody.includes(token)) {
        throw new ReadOnlyViolationError(
          `POST ${host}${cleanPath}`,
          `Payload contem acao mutavel proibida: ${token}`,
        );
      }
    }
  },
};
